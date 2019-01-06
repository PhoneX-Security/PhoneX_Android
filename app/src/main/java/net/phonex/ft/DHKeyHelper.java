package net.phonex.ft;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import net.phonex.camera.util.Exif;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipUriInfos;
import net.phonex.db.entity.DHOffline;
import net.phonex.db.entity.FileStorage;
import net.phonex.db.entity.Thumbnail;
import net.phonex.ft.misc.Canceller;
import net.phonex.ft.misc.CountingInputStream;
import net.phonex.ft.misc.DHKeyHolder;
import net.phonex.ft.misc.LimitedInputStream;
import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.ft.misc.PhotoResizeOptions;
import net.phonex.ft.misc.TransmitProgress;
import net.phonex.ft.misc.TransmitProgressI;
import net.phonex.ft.misc.UnknownArchiveStructureException;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ft.transfer.UploadFileParams;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pref.PreferencesManager;
import net.phonex.pub.proto.FileTransfer.DhkeySig;
import net.phonex.pub.proto.FileTransfer.GetDHKeyResponseBodySCip;
import net.phonex.pub.proto.FileTransfer.HybridEncryption;
import net.phonex.pub.proto.FileTransfer.MetaFile;
import net.phonex.pub.proto.FileTransfer.MetaFileDetail;
import net.phonex.pub.proto.FileTransfer.UploadFileEncryptionInfo;
import net.phonex.pub.proto.FileTransfer.UploadFileKey;
import net.phonex.pub.proto.FileTransfer.UploadFileToMac;
import net.phonex.pub.proto.FileTransfer.UploadFileXb;
import net.phonex.service.messaging.MessageManager;
import net.phonex.soap.SSLSOAP;
import net.phonex.soap.ServiceConstants;
import net.phonex.soap.entities.FtDHKey;
import net.phonex.ui.sendFile.FileUtils;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.AESCipher;
import net.phonex.util.crypto.CryptoHelper;
import net.phonex.util.crypto.CryptoHelper.CipherException;
import net.phonex.util.crypto.CryptoHelper.MACVerificationException;
import net.phonex.util.crypto.CryptoHelper.SignatureException;
import net.phonex.util.crypto.MessageDigest;
import net.phonex.util.crypto.MessageDigestInputStream;
import net.phonex.util.system.FilenameUtils;

import org.spongycastle.asn1.ASN1Encodable;
import org.spongycastle.asn1.ASN1Integer;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.ASN1SequenceParser;
import org.spongycastle.asn1.ASN1StreamParser;
import org.spongycastle.openssl.PEMReader;
import org.spongycastle.util.Arrays;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

/**
 * Class for GetDHKey protocol.
 * Implements basic cryptographic operations related to the protocol.
 * 
 * GetDHKey protocol:
 * \begin{tabular}{l l l}
 * 		$A \rightarrow B$: 	& $A$ & getKeyRequest \\
 * 		$A \leftarrow B$: 	& $RSA^e(A_{pub}, K_1), IV_1, AES^e_{K_1, IV_1}(dh\_group\_id, g^x, nonce_1, sig_1)$ & getKeyResponse\\
 * 		$A \rightarrow B$: 	& $hash(nonce_1)$ & getPart2Request\\
 * 		$A \leftarrow B$: 	& $nonce_2, RSA^e(A_{pub}, K_2), IV_2, AES^e_{K_2, IV_2}(sig_2)$ & getPart2Response\\
 * \end{tabular}
 * Where:
 * 	 Apub   ... public RSA key of the remote party
 * 	 RSA    ... asymmetric encryption (RSA/ECB/OAEPWithSHA1AndMGF1Padding)
 *   hash   ... sha256
 *   sig1   ... sig(hash(version || B || hash(B_{crt}) || A || hash(A_{crt}) || dh\_group\_id || g^x || nonce_1 ))
 *   sig2   ... sig(hash(version || B || hash(B_{crt}) || A || hash(A_{crt}) || dh\_group\_id || g^x || nonce_1 || nonce_2 ))
 * 
 * FileTransfer protocol:
 * 	\begin{tabular}{r l l l}
 * 		$B:$ & $salt_B$ 	& = \text{generate random salt} & \\
 * 		$B:$ & $nonce_B$ 	& = \text{generate random nonce} & \\
 * 		$B:$ & $y$ 			& = \text{generate random DH key} & \\
 * 		$B:$ & $c$ 			& = $g^{xy} \; \text{mod} \; p$ & \\
 * 		$B:$ & $salt_1$ 	& = $hash(salt_B \oplus nonce_1)$  & \\
 * 		$B:$ & $c_i$ 		& = $PBKDF2(BASE64(c) \; || \; "\text{pass-}c_i", hash(hash^i(salt_1) || nonce_2), 1024, 256)$ & \\
 * 		$B:$ & $M_B$ 		& = $MAC_{c_1}(version || B || hash(B_{crt}) || A || hash(A_{crt}) || dh\_group\_id || g^x || g^y || g^{xy} || nonce_1 || nonce_2 || nonce_B)$ & \\
 * 		$B:$ & $X_B$ 		& = $B, nonce_B, sig(M_B)$ & \\
 * 		$B:$ & $F_m$ 		& = $iv_1, \{file\_meta\}_{c_{5}}, MAC_{c_6}(iv_1, \{file\_meta\}_{c_{5}} || nonce_2)$ & \\
 * 		$B:$ & $F_p$ 		& = $iv_2, \{file\_pack\}_{c_{7}}, MAC_{c_8}(iv_2, \{file\_pack\}_{c_{7}} || nonce_2)$ & \\
 * 		$A \rightarrow B$: & \multicolumn{2}{l}{ $version, nonce_2, (salt_B, g^y, \{X_B\}_{c_2}, MAC_{c_3}\left(\{X_B\}_{c_2}\right)), F_m, F_p$} \; REST uploadFile &
 * \end{tabular}
 * 
 * @author ph4r05
 *
 */
public class DHKeyHelper {	
	// provider name
    private static final String BC = org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    private static final String TAG = "DHKeyGenerator";
    
    /**
     * Nonce size in bytes. 18B = 24 characters in Base64 encoding. 
     * More than 2**128 than UUID has.
     */
    public static final int NONCE_SIZE = 18;
    
    /**
     * GetKey & FileTransfer protocol version.
     */
    public static final int PROTOCOL_VERSION = 1;
    
    /**
     * Number of C keys derived from DH agreement.
     */
    public static final int CI_KEYS_COUNT=8;
    
    /**
     * Number of days to key expiration on server side, after DH was created.
     */
    public static final int EXPIRATION_SERVER_DAYS = 30;
    
    /**
     * Number of days to key expiration in database, after DH was created.
     */
    public static final int EXPIRATION_DATABASE_DAYS = 60;
    
    /**
     * Number of bits for Ci keys.
     */
    public static final int CI_KEYLEN = 256;
    
    /**
     * Number of PBKDF2 iterations for generating Ci keys.
     */
    public static final int CI_KEY_ITERATIONS = 1024;
    
    public static final int CI_MAC_MB = 1;
    public static final int CI_ENC_XB = 2;
    public static final int CI_MAC_XB = 3;
    public static final int CI_ENC_META = 4;
    public static final int CI_MAC_META = 5;
    public static final int CI_ENC_ARCH = 6;
    public static final int CI_MAC_ARCH = 7;
    
    /**
     * Maximal length of a filename in FileTransferProtocol.
     */
    public static final int MAX_FILENAME_LEN = 64;
    public static final String FILENAME_REGEX = "[^a-zA-Z0-9_\\-]";
    public static final String FILE_HASH_ALG = "SHA-256";
    public static final int THUMBNAIL_LONG_EDGE = 480; // pixels at long edge.
    
    public static final int META_IDX = 0;
    public static final int ARCH_IDX = 1;
    public static final int HOLDER_NUM_ELEMS = 1;

    /**
     * REST POST parameters names for file upload request.
     */
    public static final String FT_UPLOAD_PARAMS[] = new String[]{
    	"version", "nonce2", "user", "dhpub", "hashmeta", "hashpack", "metafile", "packfile"
    };
    
    /**
     * URI to the REST server for file upload.
     */
    public static final String REST_UPLOAD_URI = "/rest/rest/upload";
    public static final String REST_DOWNLOAD_URI = "/rest/rest/download";
    
    public static final int FT_UPLOAD_VERSION = 0;
    public static final int FT_UPLOAD_NONCE2 = 1;
    public static final int FT_UPLOAD_USER = 2;
    public static final int FT_UPLOAD_DHPUB = 3;
    public static final int FT_UPLOAD_HASHMETA = 4;
    public static final int FT_UPLOAD_HASHPACK = 5;
    public static final int FT_UPLOAD_METAFILE = 6;
    public static final int FT_UPLOAD_PACKFILE = 7;
    
    /**
     * HTTP multipart boundary allowed characters (by me, not by standard, I allow less).
     */
    public static final char[] MULTIPART_CHARS = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    
	// SpongyCastle static initialization
    static { 
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(),1);
    }
    
    private Context ctxt;
    private SecureRandom rand;
    private PrivateKey privKey;
    private X509Certificate myCert;
	private X509Certificate sipCert;
	private String userSip;
	private String mySip;
    
	private TransmitProgress txprogress;
	private Canceller canceller;
	private boolean debug=false;
	
	/**
	 * Connection timeout in milliseconds.
	 * 0 for indefinite waiting.
	 */
	private int connectionTimeoutMilli = 0;
	
	/**
	 * Timeout for a reading operation.
	 * 0 for indefinite waiting.
	 */
	private int readTimeoutMilli = 0;

	/**
	 * Throws an exception if a given byte array is null or contains zeros.
	 * @param input
	 */
	public void throwIfNull(byte[] input) {
		if (input==null || input.length==0) {
			throw new IllegalStateException("Given input field cannot be nor null neither empty");
		}
		
		boolean onlyZeros=true;
		for(byte b : input){
			if (b!=(byte)0) {
				onlyZeros=false;
				break;
			}
		}
		
		if (onlyZeros){
			throw new IllegalStateException("Given input field contains " + input.length + " zero bytes.");
		}
	}
	
    /**
     * Empty constructor.
     */
    public DHKeyHelper() {
		
	}
    
    /**
     * Constructor with provided context. 
     * Context is needed for operation.
     * 
     * @param ctxt
     */
    public DHKeyHelper(Context ctxt) {
		this.ctxt = ctxt;
	}
    
	/**
	 * Constructor with provided context and secure random.
	 * 
	 * @param ctxt
	 * @param rand
	 */
	public DHKeyHelper(Context ctxt, SecureRandom rand) {
		super();
		this.ctxt = ctxt;
		this.rand = rand;
	}

    public static void updateProgress(TransmitProgress progress, Double partial, double total){
		if (progress==null) return;
		progress.updateTxProgress(partial, total);
	}
	
	/**
	 * Wrapper for generating DHkeys for the user. 
	 * Prepares data structure.  Main entry point.
	 *
	 * @throws Exception 
	 */
    public DHKeyHolder generateDHKey() throws Exception{
    	FtDHKey key = new FtDHKey();
    	
    	// 1.0 Load certificates for corresponding users
    	final String myCertHash  = MessageDigest.getCertificateDigest(myCert);
    	final String sipCertHash = MessageDigest.getCertificateDigest(sipCert);
    	
    	// 1.1 Generate DH keys with generate().
    	DHOffline data = generateDBDHKey(userSip, sipCertHash);
    	
    	// 1.2 Hash nonce1, server does not have it in plain
    	final String nonce1Hash = Base64.encodeBytes(MessageDigest.hashSha256(data.getNonce1()));
    	
    	key.setAuxVersion(Integer.valueOf(4));
    	key.setVersion(Integer.valueOf(2));
    	key.setProtocolVersion(Integer.valueOf(3));
    	key.setUser(userSip);
    	key.setNonce1(nonce1Hash);
    	key.setNonce2(data.getNonce2());
    	key.setCreatorCertInfo(myCertHash);
    	key.setUserCertInfo(sipCertHash);
    	
    	// 2. Create signatures.
    	byte[] dhPubKey = Base64.decode(data.getPublicKey());
    	throwIfNull(dhPubKey);
    	
    	DhkeySig.Builder bsig = DhkeySig.newBuilder();
    	bsig.setVersion(PROTOCOL_VERSION);
    	bsig.setB(mySip);	// B is the key creator
    	bsig.setBCertHash(myCertHash);
    	bsig.setA(userSip);
    	bsig.setACertHash(sipCertHash);
    	bsig.setDhGroupId(data.getGroupNumber());
    	bsig.setGx(ByteString.copyFrom(dhPubKey));
    	bsig.setNonce1(data.getNonce1());
    	
    	// 2.1 sig1
    	final byte[] sig1 = DHKeySignatureGenerator.generateDhKeySignature(bsig.build(), privKey, rand);
    	throwIfNull(sig1);
    	
    	// 2.2 sig2
    	bsig.setNonce2(data.getNonce2());
    	final byte[] sig2 = DHKeySignatureGenerator.generateDhKeySignature(bsig.build(), privKey, rand);
    	throwIfNull(sig2);
    	
    	// 3. Encrypt by hybrid encryption.
    	// 3.1. 1st ciphertext
    	GetDHKeyResponseBodySCip.Builder scip = GetDHKeyResponseBodySCip.newBuilder();
    	scip.setDhGroupId(data.getGroupNumber());
    	scip.setGx(ByteString.copyFrom(dhPubKey));
    	scip.setNonce1(data.getNonce1());
    	scip.setSig1(ByteString.copyFrom(sig1));
    	
    	GetDHKeyResponseBodySCip scip1 = scip.build();
    	HybridEncryption cip1 = HybridCipher.encrypt(ProtoBuffHelper.writeTo(scip1), sipCert.getPublicKey(), rand);
    	
    	// 3.2. 2nd ciphertext
    	HybridEncryption cip2 = HybridCipher.encrypt(sig2, sipCert.getPublicKey(), rand);
    	
    	// 4. SOAP entity
    	key.setAEncBlock(ProtoBuffHelper.writeTo(cip1)); //
    	// Using hybrid encryption format (everything in AEncBlock), not needed in this version.
    	key.setSEncBlock(new byte[] {});		
    	// Has no use in this version, signature is encrypted in AEncBlock.
    	key.setSig1(new byte[] {});				
    	// Signature 2, for 4th message of the protocol. Using hybrid encryption.
    	key.setSig2(ProtoBuffHelper.writeTo(cip2));
    	
    	// Set expiration date for server side
    	Calendar cal = Calendar.getInstance();
    	cal.add(Calendar.DATE, EXPIRATION_SERVER_DAYS);
    	key.setExpires(cal);
    	
    	DHKeyHolder holder = new DHKeyHolder();
    	holder.dbKey = data;
    	holder.serverKey = key;
    	
    	return holder;
    }
    
    /**
     * Process response of the GetDHKey protocol, 2nd message.
     * Requires byte array corresponding to hybrid encryption output.
     * 
     * @param hybridEncryption
     * @return
     * @throws InvalidProtocolBufferException 
     * @throws CipherException 
     */
    public GetDHKeyResponseBodySCip getDhKeyResponse(byte[] hybridEncryption) throws InvalidProtocolBufferException, CipherException{
    	// 1.0 Read byte array and obtain hybrid encryption.
    	HybridEncryption.Builder bhib = HybridEncryption.newBuilder();
    	bhib.mergeFrom(hybridEncryption);
    	
    	// 2.0 Process by hybrid cipher
    	byte[] plaintext = HybridCipher.decrypt(bhib.build(), privKey, rand);
    	
    	// 3.0 reconstruct message from decrypted ciphertext.
    	GetDHKeyResponseBodySCip.Builder bresp = GetDHKeyResponseBodySCip.newBuilder();
    	bresp.mergeFrom(plaintext);
    	return bresp.build();
    }
    
    /**
     * Process getPart2Response message, decrypts signature2.
     * 
     * @param hybridEncryption
     * @return
     * @throws InvalidProtocolBufferException
     * @throws CipherException
     */
    public byte[] getDhPart2Response(byte[] hybridEncryption) throws InvalidProtocolBufferException, CipherException{
    	// 1.0 Read byte array and obtain hybrid encryption.
    	HybridEncryption.Builder bhib = HybridEncryption.newBuilder();
    	bhib.mergeFrom(hybridEncryption);
    	
    	// 2.0 Process by hybrid cipher
    	return HybridCipher.decrypt(bhib.build(), privKey, rand);
    }
    
    /**
     * Verifies signature from the GetDHKey protocol.
     * If nonce2 is null, it verifies sig1 otherwise sig2.
     * 
     * Uses mySip and userSip (same for certificates) in the order
     * assuming mySip is ID of the requesting side, userSip is the ID 
     * of the user that generated this signature.
     * 
     * @param resp
     * @return
     * @throws SignatureException 
     * @throws IOException 
     * @throws NoSuchAlgorithmException 
     * @throws CertificateEncodingException 
     */
    public boolean verifySig1(GetDHKeyResponseBodySCip resp, String nonce2, byte[] signature) throws SignatureException, CertificateEncodingException, NoSuchAlgorithmException, IOException{
    	// 1.0 Load certificates for corresponding users
    	final String myCertHash  = MessageDigest.getCertificateDigest(myCert);
    	final String sipCertHash = MessageDigest.getCertificateDigest(sipCert);
    	
    	// 2. Create signatures.
    	DhkeySig.Builder bsig = DhkeySig.newBuilder();
    	bsig.setVersion(PROTOCOL_VERSION);
    	bsig.setA(mySip);
    	bsig.setACertHash(myCertHash);
    	bsig.setB(userSip);	// B is the signature creator
    	bsig.setBCertHash(sipCertHash);
    	bsig.setDhGroupId(resp.getDhGroupId());
    	bsig.setGx(resp.getGx());
    	bsig.setNonce1(resp.getNonce1());
    	if (nonce2!=null){
    		bsig.setNonce2(nonce2);
    	}
    	
    	return DHKeySignatureGenerator.verifyDhKeySIgnature(bsig.build(), signature, this.sipCert.getPublicKey());
    }
    
    /**
     * Generates nonce for the protocol with specific size.
     * 
     * @return
     * @author ph4r05
     */
    public byte[] generateNonce(){
    	byte[] toReturn = new byte[NONCE_SIZE];
    	rand.nextBytes(toReturn);
    	return toReturn;
    }
    
    /**
     * Generates DH key pair from the given group.
     * @param groupId
     * @return
     * @throws Exception 
     */
    public KeyPair generateKeyPair(int groupId) throws Exception{
    	DHParameterSpec dhParameterSpec = loadDHParameterSpec(groupId);
		if (dhParameterSpec==null){
			throw new Exception("cannot load dhParameters");
		}		

		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH", BC);
		keyGen.initialize(dhParameterSpec, rand);
		
		// Generate DH key			
		return keyGen.generateKeyPair();	
    }
    
    /**
     * Creates DiffieHellman shared key.
     * Sorry for small "D", Diffie should be with big D, but naming convention...
     * 
     * @param pair
     * @param gx
     * @throws NoSuchProviderException 
     * @throws NoSuchAlgorithmException 
     * @throws InvalidKeyException 
     */
    public byte[] diffieHelman(KeyPair pair, PublicKey gx) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException{
		KeyAgreement aKeyAgree = KeyAgreement.getInstance("DH", BC);
		aKeyAgree.init(pair.getPrivate());   
		aKeyAgree.doPhase(gx, true);
		return aKeyAgree.generateSecret();
    }
    
    /**
	 * Generate and store new DH key pair for particular sip user in contact list.
	 * 
	 * @param sip
	 * @return true on success, otherwise false
	 * @throws Exception 
	 * @author miroc
	 */
	public DHOffline generateDBDHKey(String sip, String sipCertHash) throws Exception{
		// we randomly pick up a prime group (from pregenerated set) from which DH parameters are derived
		int groupId = rand.nextInt(256) + 1;		
		
		// Generate DH key			
		KeyPair aPair = generateKeyPair(groupId);	
		
		// Generate nonces
		byte[] nonce1 = generateNonce();
		byte[] nonce2 = generateNonce();
			
		// Store to DB
		DHOffline data = new DHOffline();
		data.setPrivateKey(Base64.encodeBytes(aPair.getPrivate().getEncoded()));
		data.setPublicKey(Base64.encodeBytes(aPair.getPublic().getEncoded()));
		data.setGroupNumber(groupId);
		data.setSip(sip);
		data.setDateCreated(new Date());
		data.setNonce1(Base64.encodeBytes(nonce1));
		data.setNonce2(Base64.encodeBytes(nonce2));
		data.setaCertHash(sipCertHash);
		
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, EXPIRATION_DATABASE_DAYS);
		data.setDateExpire(cal.getTime());
			
		ContentResolver cr = ctxt.getContentResolver();
		cr.insert(DHOffline.DH_OFFLINE_URI, data.getDbContentValues());
		
		Log.vf(TAG, "Stored DH object to database; detail=[%s]", data.toString());	
		return data;
	}
    
	/**
	 * Loads specific DHkey from the database.
	 * Sip can be null, in that case only nonce2 is used for search.
	 * 
	 * @param nonce2
	 * @param sip
	 * @return
	 */
	public DHOffline loadDHKey(String nonce2, String sip){
		try {
			// Search criteria = nonce2 (and optionally SIP).
    		String selection = DHOffline.FIELD_NONCE2 + "=?";
    		String[] selectionArgs = null;
    		if (sip!=null){
    			selection += " AND " + DHOffline.FIELD_SIP + "=?";
    			selectionArgs = new String[] {nonce2, sip};
    		} else {
    			selectionArgs = new String[] { nonce2 };
    		}
    		
    		Cursor c = ctxt.getContentResolver().query(
					DHOffline.DH_OFFLINE_URI,
					DHOffline.FULL_PROJECTION,
					selection, selectionArgs, null);
    		
    		if (c!=null && c.getCount() > 0 && c.moveToFirst()){
    			DHOffline dh = new DHOffline(c);
                MiscUtils.closeSilently(c);
				
				return dh;
    			
    		} else if (c!=null){
                MiscUtils.closeSilently(c);
    		}
    		
    		return null;
         } catch(Exception e){
         	Log.ef(TAG, e, "Exception during loading DHKey nonce2: %s", nonce2);
         	return null;
         }
	}
	
	/**
	 * Removes all DH keys for particular user. 
	 * 
	 * @param sip
	 * @return
	 */
	public int removeDHKeysForUser(String sip){
		try {
    		String selection = DHOffline.FIELD_SIP + "=?";
    		String[] selectionArgs = new String[] {sip};
    		
    		int d = ctxt.getContentResolver().delete(
					DHOffline.DH_OFFLINE_URI,
					selection,
					selectionArgs);
    		
    		return d;
         } catch(Exception e){
         	Log.ef(TAG, e, "Exception during removing DHKeys for user: %s", sip);
         	return 0;
         }
	}
	
	/**
	 * Removes a DHKey with given nonce2
	 * 
	 * @param nonce2
	 * @return
	 */
	public boolean removeDHKey(String nonce2){
		return DHOffline.removeDHKey(ctxt.getContentResolver(), nonce2);
	}
	
	/**
	 * Removes a DHKey with given nonce2s
	 * 
	 * @param nonces
	 * @return
	 */
	public int removeDHKeys(List<String> nonces){
		return DHOffline.removeDHKeys(ctxt.getContentResolver(), nonces);
	}
	
	/**
	 * Removes DH keys that are either a) older than given date 
	 * OR b) does not have given certificate hash OR both OR just
	 * equals the sip.
	 * 
	 * Returns number of removed entries.
	 * 
	 * @param sip
	 * @param olderThan
	 * @param certHash
	 * @return
	 */
	public int removeDHKeys(String sip, Date olderThan, String certHash){
        return DHOffline.removeDHKeys(ctxt.getContentResolver(), sip, olderThan, certHash, null);
	}
	
	/**
	 * Removes DH keys that are either a) older than given date 
	 * OR b) does not have given certificate hash OR both OR just
	 * equals the sip.
	 * 
	 * Returns number of removed entries.
	 * 
	 * @param sip
	 * @param olderThan
	 * @param certHash
	 * @param expirationLimit
	 * @return
	 */
	public int removeDHKeys(String sip, Date olderThan, String certHash, Date expirationLimit){
		return DHOffline.removeDHKeys(ctxt.getContentResolver(), sip, olderThan, certHash, expirationLimit);
	}
	
	/**
	 * Returns list of a nonce2s for ready DH keys. If
	 * sip is not null, for a given user, otherwise for
	 * everybody.
	 *  
	 * @param sip OPTIONAL
	 * @return
	 */
	public List<String> getReadyDHKeysNonce2(String sip){
		List<String> nonceList = new LinkedList<String>();
		
		try {
			// Search criteria = nonce2 (and optionally SIP).
    		String selection = null;
    		String[] selectionArgs = null;
    		if (sip!=null){
    			selection = DHOffline.FIELD_SIP + "=?";
    			selectionArgs = new String[] {sip};
    		}
    		
    		Cursor c = ctxt.getContentResolver().query(
    				DHOffline.DH_OFFLINE_URI, 
    				DHOffline.LIGHT_PROJECTION, 
    				selection, selectionArgs, null);
    		
    		if (c!=null){
    			while(c.moveToNext()){
    				DHOffline dh = new DHOffline(c);
    				nonceList.add(dh.getNonce2());
    			}
				
				try {
    				c.close();
    			} catch(Exception e) { }
				
				return nonceList;
    		}
    		
    		return null;
         } catch(Exception e){
         	Log.ef(TAG, e, "Exception during loading DHKey nonce2sip: %s", sip);
         	return nonceList;
         }
	}
	
	/**
	 * Converts database DHKey entry to DH Key pair.
	 * 
	 * @param data
	 * @return
	 * @throws IOException
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 */
	public KeyPair getKeyPair(DHOffline data) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException{
		byte[] privKeyBytes = Base64.decode(data.getPrivateKey());
		byte[] pubKeyBytes  = Base64.decode(data.getPublicKey());
		
		return new KeyPair(getPubKeyFromByte(pubKeyBytes), getPrivKeyFromByte(privKeyBytes));
	}
	
	/**
	 * Reconstructs DH PublicKey from byte representation.
	 * 
	 * @param pk
	 * @return
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 */
	public PublicKey getPubKeyFromByte(byte[] pk) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException{
		return KeyFactory.getInstance("DH", BC).generatePublic(new X509EncodedKeySpec(pk)); 
	}
	
	/**
	 * Reconstructs DH PrivKey from byte representation.
	 * 
	 * @param pk
	 * @return
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 */
	public PrivateKey getPrivKeyFromByte(byte[] pk) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException{
		return KeyFactory.getInstance("DH", BC).generatePrivate(new PKCS8EncodedKeySpec(pk));
	}
	
    /**
	 * Load DH PARAMETER from file assets/dh_groups/dhparam_4096_1_0<groupNumber>.pem
	 * 
	 * @param groupNumber should be between 001-256
	 * @return
	 * @author miroc
	 */
	public DHParameterSpec loadDHParameterSpec(int groupNumber){
		try {			
			InputStream is = null;
			is = ctxt.getAssets().open("dh_groups/dhparam_4096_1_0" + String.format("%03d", groupNumber) + ".pem");
	        
	        PEMReader pr = new PEMReader(new InputStreamReader(is));
			ASN1StreamParser asn1StreamParser = new ASN1StreamParser(pr.readPemObject().getContent());

			Object obj = asn1StreamParser.readObject();			
			DHParameterSpec spec = null;
			
			if (obj instanceof ASN1SequenceParser){
				// DH_PARAMETERS contains SEQUENCE of two INTEGERS (p and g, optionally also l)
                ASN1Sequence asn1Sequence = ASN1Sequence.getInstance(obj);
                ASN1Encodable pAsn= asn1Sequence.getObjectAt(0); // prime
                ASN1Encodable gAsn= asn1Sequence.getObjectAt(1); // generator
                if (pAsn==null || gAsn ==null){
                	Log.e(TAG, "cannot load DH parameters from PEM file, ASN1 sequence doesn't contain proper data.");                	
                } else {
                	BigInteger p = ASN1Integer.getInstance(pAsn.toASN1Primitive()).getValue();
                    BigInteger g = ASN1Integer.getInstance(gAsn.toASN1Primitive()).getValue();
                    spec = new DHParameterSpec(p, g);
                }
            }
			pr.close();
	        is.close();			
	        return spec;
		} catch (Exception e) {
			Log.e(TAG, "Problem while working with dhparam assets file", e);
			return null;
		}		
	}
	
	/**
	 * Initializes file transfer protocol holder.
	 * Generates all necessary components for the FileTransfer protocol.
	 * Used if you are in the role of a sender.
	 * 
	 * Procedure assumes client has executed GetDHKey protocol and has:
	 * a) DHKey from message 2.
	 * b) nonce2 obtained from message 4.
	 * 
	 * Function generates DH keys needed for file transfer. Function ends up by generating
	 * X_B, Enc(X_B), MAC(Enc(X_B)).
	 * 
	 * Function requires: rand, userSip, remoteSip, certificates, private key (for producing signature).
	 * 
	 * @param nonce2 - byte representation of nonce2.
	 * @return
	 * @throws Exception 
	 */
	public FTHolder initFTHolder(GetDHKeyResponseBodySCip body, byte[] nonce2) throws Exception{
		// Following the protocol description
		FTHolder holder = new FTHolder();
		holder.nonce2 = nonce2;
		
		// 1.1 saltb
		holder.saltb = generateNonce();
		
		// 1.2 nonceb
		holder.nonceb = generateNonce();
		
		// 1.3 generate random key pair
		holder.kp = generateKeyPair(body.getDhGroupId());
		
		// 1.4 compute DH shared key
		PublicKey dhRemotePublic = getPubKeyFromByte(body.getGx().toByteArray());
		holder.c = diffieHelman(holder.kp, dhRemotePublic);
		
		// 1.5 compute salt1 = hash(saltB XOR nonce1)
		holder.salt1 = computeSalt1(holder.saltb, Base64.decode(body.getNonce1()));
		
		// Check if some of given values are not null - would signalize serious bug.
		throwIfNull(holder.nonce2);
		throwIfNull(holder.saltb);
		throwIfNull(holder.nonceb);
		throwIfNull(holder.c);
		throwIfNull(holder.salt1);
		
		// 1.6 ci
		computeCi(holder);
		
		// 1.7 MB = MAC_{c_1}(version || B || hash(B_{crt}) || A || hash(A_{crt}) || dh\_group\_id || g^x || g^y || g^{xy} || nonce_1 || nonce_2 || nonce_B)
    	final String myCertHash  = MessageDigest.getCertificateDigest(myCert);
    	final String sipCertHash = MessageDigest.getCertificateDigest(sipCert);
    	
		UploadFileToMac.Builder mb = UploadFileToMac.newBuilder();
		mb.setVersion(PROTOCOL_VERSION);
		mb.setB(this.userSip);
		mb.setBCertHash(sipCertHash);
		mb.setA(this.mySip);
		mb.setACertHash(myCertHash);
		mb.setDhGroupId(body.getDhGroupId());
		mb.setGx(body.getGx());
		mb.setGy(ByteString.copyFrom(holder.kp.getPublic().getEncoded()));
		mb.setGxy(ByteString.copyFrom(holder.c));
		mb.setNonce1(body.getNonce1());
		mb.setNonce2(Base64.encodeBytes(nonce2));
		mb.setNonceb(ByteString.copyFrom(holder.nonceb));
		
		// 1.7.2 generate MAC over UploadFileToMac.
		holder.MB = CryptoHelper.hmac(ProtoBuffHelper.writeTo(mb.build()), holder.ci[CI_MAC_MB]);
		
		// 1.8 XB = B, nonceb, sig(Mb)
		// 1.8.1 generate signature on Mb
		byte[] sig = CryptoHelper.sign(holder.MB, privKey, rand);
		UploadFileXb.Builder bXB = UploadFileXb.newBuilder();
		bXB.setB(userSip);
		bXB.setNonceb(ByteString.copyFrom(holder.nonceb));
		bXB.setSig(ByteString.copyFrom(sig));
		holder.XB = bXB.build(); 
		
		// 2.0 Encrypt XB
		holder.encXB = AESCipher.encrypt(ProtoBuffHelper.writeTo(holder.XB), holder.ci[CI_ENC_XB], rand);
		
		// 3.0 MAX encXB
		holder.macEncXB = CryptoHelper.hmac(holder.encXB, holder.ci[CI_MAC_XB]);
		
		return holder;
	}

	/**
	 * Derives sub-keys from DH master key with PBKDF2 and fills in to the FTHolder.
	 *
	 * $c_i$ & = $PBKDF2(c \; || \; "\text{pass-}c_i", hash(hash^i(salt_1) || nonce_2), 1024, 256)$
	 *
	 * @param holder
	 * @return
	 */
	public void computeCi(FTHolder holder) throws GeneralSecurityException, OperationCancelledException, UnsupportedEncodingException {
		if (holder.ci == null) {
			holder.ci = new byte[CI_KEYS_COUNT][];
		}

		for(int i=1; i<CI_KEYS_COUNT; i++){
			holder.ci[i] = computeCi(holder.c, i, holder.salt1, holder.nonce2);
			throwIfNull(holder.ci[i]);

			checkIfCancelled();
		}
	}

	/**
	 * Derives sub-keys from DH master key with PBKDF2
	 * 
	 * $c_i$ & = $PBKDF2(c \; || \; "\text{pass-}c_i", hash(hash^i(salt_1) || nonce_2), 1024, 256)$
	 * 
	 * @param c
	 * @param salt1
	 * @param nonce2
	 * @return
	 * @throws NoSuchAlgorithmException 
	 * @throws UnsupportedEncodingException 
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchProviderException 
	 */
	public byte[] computeCi(byte[] c, int i, byte[] salt1, byte[] nonce2) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeySpecException, NoSuchProviderException{
		// Prepare salt value
		java.security.MessageDigest hasher = java.security.MessageDigest.getInstance("SHA-256");
		// 1.0 hash^i(salt_1)
		byte[] salt1Hashed = MessageDigest.iterativeHash(salt1, i, hasher);
		// 1.1 hash(hash^i(salt_1) || nonce2)
		byte[] toHash = CryptoHelper.concat(salt1Hashed, nonce2);
		byte[] pbkdfsalt = MessageDigest.hashSha256(toHash);
		final String cString = Base64.encodeBytes(c);
		
		return CryptoHelper.pbkdf2(cString + "||pass-c" + i, pbkdfsalt, CI_KEY_ITERATIONS, CI_KEYLEN);
	}
	
	/**
	 * Computes salt1=SHA256(saltb XOR nonce1)
	 * 
	 * @return
	 * @throws NoSuchAlgorithmException 
	 * @throws UnsupportedEncodingException 
	 */
	public byte[] computeSalt1(byte[] saltb, byte[] nonce1) throws UnsupportedEncodingException, NoSuchAlgorithmException{
		int minSize = Math.min(nonce1.length, saltb.length);
		int maxSize = Math.max(nonce1.length, saltb.length);
		byte[] toSalt1 = new byte[maxSize];
		
		System.arraycopy(saltb, 0, toSalt1, 0, saltb.length);
		for(int i=0; i<minSize; i++){
			toSalt1[i] ^= nonce1[i];
		}
		
		return MessageDigest.hashSha256(toSalt1);
	}
	
	/**
	 * Reconstructs ukey from bytes.
	 * Used in FileTransfer protocol to contain DH public key.
	 * 
	 * @param ukeyBytes
	 * @return
	 * @throws InvalidProtocolBufferException 
	 */
	public UploadFileKey reconstructUkey(byte[] ukeyBytes) throws InvalidProtocolBufferException{
		return UploadFileKey.parseFrom(ukeyBytes);
	}
	
	/**
	 * Process file transfer message from the sender.
	 * Reconstructs shared secret to the FTHolder.
	 * 
	 * Used if you are in the role of a receiver.
	 * 
	 * Is assumed user has loaded DHOffline record corresponding to this 
	 * user and nonce2.
	 * 
	 * Function requires: rand, userSip, remoteSip, certificates.
	 * 
	 * If this function ends without exception thrown it means that:
	 *   a) Encryption keys are recovered.
	 *	 b) MAC on the ciphertext XB is verified (ciphertext was not tampered, no chosen ciphertext attack).
	 *	 c) Signature on the MAC is verified, thus the key exchange was not tampered (sides identity,
	 *		  public key), is related to this session (nonce2) and is fresh (nonce1, nonce2, nonceb)
	 *        and the identity of the remote party is verified.
	 * 
	 * @param data			DHOffline database record corresponding to nonce2.
	 * @param ukey			uKey read from the REST response.
	 * 
	 * @throws IOException 
	 * @throws NoSuchAlgorithmException 
	 * @throws UnsupportedEncodingException 
	 * @throws NoSuchProviderException 
	 * @throws InvalidKeySpecException 
	 * @throws InvalidKeyException 
	 * @throws MACVerificationException 
	 * @throws InvalidParameterSpecException 
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * @throws NoSuchPaddingException 
	 * @throws InvalidAlgorithmParameterException 
	 * @throws CertificateEncodingException 
	 * @throws java.security.SignatureException 
	 */
	public FTHolder processFileTransfer(DHOffline data, byte[] ukey) throws UnsupportedEncodingException, GeneralSecurityException, IOException, MACVerificationException, SignatureException{
		UploadFileKey u = reconstructUkey(ukey);
		return processFileTransfer(data, u.getSaltb().toByteArray(), u.getGy().toByteArray(), u.getSCiphertext().toByteArray(), u.getMac().toByteArray());
	}
	
	/**
	 * Process file transfer message from the sender.
	 * Reconstructs shared secret to the FTHolder.
	 * 
	 * Used if you are in the role of a receiver.
	 * 
	 * Is assumed user has loaded DHOffline record corresponding to this 
	 * user and nonce2.
	 * 
	 * Function requires: rand, userSip, remoteSip, certificates.
	 * 
	 * If this function ends without exception thrown it means that:
	 *   a) Encryption keys are recovered.
	 *	 b) MAC on the ciphertext XB is verified (ciphertext was not tampered, no chosen ciphertext attack).
	 *	 c) Signature on the MAC is verified, thus the key exchange was not tampered (sides identity,
	 *		  public key), is related to this session (nonce2) and is fresh (nonce1, nonce2, nonceb)
	 *        and the identity of the remote party is verified.
	 * 
	 * @param data			DHOffline database record corresponding to nonce2.
	 * @param saltb			SaltB from the FTUploadMessage.
	 * @param gy			DH Pub key of a remote party (sender) from the FTUploadMessage.
	 * @param encXB			Encrypted XB from the FTUploadMessage.
	 * @param macEncXB		MAC on encrypted XB from the FTUploadMessage.
	 * @throws IOException 
	 * @throws NoSuchAlgorithmException 
	 * @throws UnsupportedEncodingException 
	 * @throws NoSuchProviderException 
	 * @throws InvalidKeySpecException 
	 * @throws InvalidKeyException 
	 * @throws MACVerificationException 
	 * @throws InvalidParameterSpecException 
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * @throws NoSuchPaddingException 
	 * @throws InvalidAlgorithmParameterException 
	 * @throws CertificateEncodingException 
	 * @throws java.security.SignatureException 
	 */
	public FTHolder processFileTransfer(DHOffline data, byte[] saltb, byte[] gy, byte[] encXB, byte[] macEncXB)
			throws UnsupportedEncodingException, GeneralSecurityException, IOException,
			MACVerificationException, SignatureException, OperationCancelledException {
		
		FTHolder holder = new FTHolder();
		
		// 1.0 recover salt1 = hash(saltB XOR nonce1)
		holder.initFileStruct();
		holder.salt1 = computeSalt1(saltb, Base64.decode(data.getNonce1()));
		holder.saltb = saltb;
		holder.nonce1 = data.getNonce1();
		holder.nonce2 = Base64.decode(data.getNonce2());
		throwIfNull(holder.salt1);
		throwIfNull(holder.saltb);
		
		// Is operation cancelled?
		checkIfCancelled();
		
		// 1.1 compute ci
		holder.kp = getKeyPair(data);
		holder.c  = diffieHelman(holder.kp, getPubKeyFromByte(gy));
		holder.ci = new byte[CI_KEYS_COUNT][];
		throwIfNull(holder.c);
		
		// Is operation cancelled?
		checkIfCancelled();
		computeCi(holder);

		// 1.2 compute MAC on ciphertext and verify it
		holder.macEncXB = CryptoHelper.hmac(encXB, holder.ci[CI_MAC_XB]);
		if (Arrays.areEqual(holder.macEncXB, macEncXB)==false){
			throw new MACVerificationException("MAC does not match");
		}
		
		// Is operation cancelled?
		checkIfCancelled();
		
		// 1.3 decrypt XB
		holder.encXB = encXB;
		throwIfNull(holder.encXB);
		byte[] XBbyte = AESCipher.decrypt(encXB, holder.ci[CI_ENC_XB]);
		
		UploadFileXb.Builder bXB = UploadFileXb.newBuilder();
		bXB.mergeFrom(XBbyte);
		holder.XB = bXB.build();
		
		// Is operation cancelled?
		checkIfCancelled();
		
		// 1.4 produce MAC MB = MAC_{c_1}(version || B || hash(B_{crt}) || A || hash(A_{crt}) || dh\_group\_id || g^x || g^y || g^{xy} || nonce_1 || nonce_2 || nonce_B)
		holder.nonceb = holder.XB.getNonceb().toByteArray();
    	final String myCertHash  = MessageDigest.getCertificateDigest(myCert);
    	final String sipCertHash = MessageDigest.getCertificateDigest(sipCert);
    	
		// Is operation cancelled?
    	checkIfCancelled();
    	
		UploadFileToMac.Builder mb = UploadFileToMac.newBuilder();
		mb.setVersion(PROTOCOL_VERSION);
		mb.setB(this.mySip);
		mb.setBCertHash(myCertHash);
		mb.setA(this.userSip);
		mb.setACertHash(sipCertHash);
		mb.setDhGroupId(data.getGroupNumber());
		mb.setGx(ByteString.copyFrom(Base64.decode(data.getPublicKey()))); // PubKey of the creator.
		mb.setGy(ByteString.copyFrom(gy));								   // PubKey of the sender.
		mb.setGxy(ByteString.copyFrom(holder.c));
		mb.setNonce1(data.getNonce1());
		mb.setNonce2(data.getNonce2());
		mb.setNonceb(ByteString.copyFrom(holder.nonceb));		// Obtained by decryption of XB.
		
		// 1.4.1 generate MAC over UploadFileToMac.
		holder.MB = CryptoHelper.hmac(ProtoBuffHelper.writeTo(mb.build()), holder.ci[CI_MAC_MB]);
		
		// Is operation cancelled?
		checkIfCancelled();
		
		// 1.5 verify signature
		try {
			boolean sigok = CryptoHelper.verify(holder.MB, holder.XB.getSig().toByteArray(), sipCert.getPublicKey());
			if (!sigok) throw new CryptoHelper.SignatureException("Signature is invalid.");
		} catch (Exception e){
			throw new CryptoHelper.SignatureException("Exception during signature verification.", e);
		}
		
		// Done		
		return holder;
	}
	
	/**
	 * Generates HMAC on the file according to the protocol.
	 * Produces HMAC_{key}(iv || nonce2 || file)
	 * 
	 * @param is
	 * @param nonce2
	 * @return
	 * @throws NoSuchProviderException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	public byte[] generateFTFileMac(InputStream is, long offset, byte[] key, byte[] iv, byte[] nonce2, Canceller canceller, TransmitProgress pr) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, IllegalStateException, IOException{
		Mac mac = Mac.getInstance(CryptoHelper.HMAC, BC);
   	 	SecretKeySpec secret_key = new SecretKeySpec(key, CryptoHelper.HMAC);
   	 	mac.init(secret_key);
   	 
   	 	// MAC iv, nonce2, file
   	 	mac.update(iv, 0, iv.length);
   	 	final byte[] nonce2Base64 = Base64.encodeBytesToBytes(nonce2);
   	 	mac.update(nonce2Base64, 0, nonce2Base64.length);

		// Offsetting support.
		CryptoHelper.dropFirst(is, (int)offset, canceller);

   	 	int numBytes;
        long totalBytes = 0;
   	 	final byte[] bytes = new byte[8192];
   	 	while ((numBytes = is.read(bytes)) != -1) {
   	 		mac.update(bytes, 0, numBytes);

            // Cancellation.
            if (canceller != null && canceller.isCancelled()){
                throw new OperationCancelledException("MAC computation cancelled");
            }

            // Progress monitoring.
            if (pr != null){
                totalBytes += numBytes;
                pr.updateTxProgress(totalBytes);
            }
		}
   	 	
   	 	return mac.doFinal();
	}

    /**
     * Generates HMAC on the file according to the protocol.
     * Produces HMAC_{key}(iv || nonce2 || file)
     *
     * @param key
     * @param iv
     * @param nonce2
     * @return
     * @throws NoSuchProviderException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws IOException
     * @throws IllegalStateException
     */
    public Mac getFTMacObject(byte[] key, byte[] iv, byte[] nonce2) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, IllegalStateException, IOException {
        Mac mac = Mac.getInstance(CryptoHelper.HMAC, BC);
        SecretKeySpec secret_key = new SecretKeySpec(key, CryptoHelper.HMAC);
        mac.init(secret_key);

        // MAC iv, nonce2, file
        mac.update(iv, 0, iv.length);
        final byte[] nonce2Base64 = Base64.encodeBytesToBytes(nonce2);
        mac.update(nonce2Base64, 0, nonce2Base64.length);
        return mac;
    }
	
	/**
	 * Generates HMAC on the file according to the protocol.
	 * Produces HMAC_{key}(iv || nonce2 || file)
	 * 
	 * @param file
	 * @param key
	 * @param iv
	 * @param nonce2
	 * @param canceller
	 * @param pr
	 * @return
	 * @throws NoSuchProviderException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	public byte[] generateFTFileMac(File file, long offset, byte[] key, byte[] iv, byte[] nonce2, Canceller canceller, TransmitProgress pr) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, IllegalStateException, IOException{
		InputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file));
			return generateFTFileMac(is, offset, key, iv, nonce2, canceller, pr);
		} finally {
            MiscUtils.closeSilently(is);
		}
	}
	
	/**
	 * Computes hash on the file according to the protocol.
	 * Produces hash(iv || mac(iv, nonce2, e) || e). Uses protocol buffers to store IV and MAC.
	 * 
	 * @param is
	 * @param iv
	 * @param mac
	 * @return
	 * @throws NoSuchProviderException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	public byte[] computeFTFileHash(InputStream is, byte[] iv, byte[] mac) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, IllegalStateException, IOException{
		java.security.MessageDigest hasher = java.security.MessageDigest.getInstance(FILE_HASH_ALG);
		
		// Build {IV + MAC} structure that prepends ciphertext.
		UploadFileEncryptionInfo.Builder b = UploadFileEncryptionInfo.newBuilder();
		b.setIv(ByteString.copyFrom(iv));
		b.setMac(ByteString.copyFrom(mac));
		
		// Build structure & write to the output stream.
		final UploadFileEncryptionInfo info = b.build();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		info.writeDelimitedTo(bos);
		bos.close();
		
		final byte[] prepMeta = bos.toByteArray();
		hasher.update(prepMeta, 0, prepMeta.length);
		
    	int numBytes;
        byte[] bytes = new byte[8192];
        while ((numBytes = is.read(bytes)) != -1) {
        	hasher.update(bytes, 0, numBytes);
		}
        
		return hasher.digest();
	}
	
	/**
	 * Computes hash on the file according to the protocol.
	 * Produces hash(iv || mac(iv, nonce2, e) || e)
	 * 
	 * @param file
	 * @param iv
	 * @param mac
	 * @return
	 * @throws NoSuchProviderException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	public byte[] computeFTFileHash(File file, byte[] iv, byte[] mac) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, IllegalStateException, IOException{
		InputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file));
			return computeFTFileHash(is, iv, mac);
		} finally {
            MiscUtils.closeSilently(is);
		}
	}
	
	/**
	 * Sanitize file file name. Cuts filename length 
	 * only to allowed size (preserving extension, converted to lower-case), 
	 * allows only alpha-numerical+{_-} characters.
	 * 
	 * Same checks holds also for extension.
	 * 
	 * @param fileName
	 * @return
	 */
	public String sanitizeFileName(String fileName){
		// At first get real extension. 
		String extension = FilenameUtils.getExtension(fileName).toLowerCase();
		String basename = FilenameUtils.getBaseName(fileName);
	
		// Truncate file length to the maximal size
		basename = MiscUtils.getStringMaxLen(basename, MAX_FILENAME_LEN);
		extension = MiscUtils.getStringMaxLen(extension, 12);
		
		// Remove not allowed characters
		basename = basename.replaceAll(FILENAME_REGEX, "_");
		// Same for extension
		extension = extension.replaceAll("[^a-zA-Z0-9_\\-]", "_");
		
		return TextUtils.isEmpty(extension) ? basename : basename + "." + extension;
	}

	/**
	 * Returns directory where to store generated and received files.
	 * @return
	 * @throws IOException 
	 */
	public static File getStorageDirectory(Context ctxt, boolean preferExternal) throws IOException{
		File storageDir = ctxt.getExternalFilesDir(null);
		if (storageDir==null){
			throw new FileNotFoundException("External file directory is null");
		}
		
		if (storageDir.exists()==false){
			boolean success = storageDir.mkdirs();
			if (!success){
				throw new IOException("Cannot nor get neither create storage directory: [" + storageDir.getAbsolutePath() + "]");
			} else {
				Log.vf(TAG, "Storage directory created at: [%s]", storageDir.getAbsolutePath());
			}
		}
		
		// Write test.
		if (!storageDir.canWrite()){
			Log.vf(TAG, "Cannot write to the storage directory: [%s]", storageDir.getAbsolutePath());
			return PreferencesManager.getTempFolder(ctxt, true);
		}
		
		if (preferExternal){
			return PreferencesManager.getTempFolder(ctxt, true);
		}
		
		return storageDir;
	}

	/**
	 * Returns directory for storing thumbnail files.
	 * TODO: move to internal storage for increasing security level.
	 * @return
	 * @throws IOException
	 */
	public static File getThumbDirectory(Context ctxt)  throws IOException {
		return getStorageDirectory(ctxt, false);
	}

    /**
     * Computes CRC32 file checksum.
     * @param fl
     * @return
     */
    public static Long checksumFile(File fl){
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fl);
            CRC32 crc = new CRC32();
            byte[] buff = new byte[8192];
            int read = 0;

            while((read = fis.read(buff)) > 0){
                crc.update(buff, 0, read);
            }

            return crc.getValue();
        } catch (Exception ex){
            Log.ef(TAG, ex, "Cannot compute CRC32 of a file: %s", fl);
        } finally {
            MiscUtils.closeSilently(fis);
        }

        return null;
    }

    /**
     * Check a given file name for duplicate.
     * If a duplicate is found in mutable set, new non-conflicting one file name is returned.
     */
    public String checkFileNameDuplicate(Set<String> fnames, String fname) {
        final String fn = fname;
        boolean didCollide = false;
        if (!fnames.contains(fn.toLowerCase())){
            return fn;
        }

        didCollide = true;
        boolean success = false;
        String candidate = FilenameUtils.removeExtension(fn);
        String extension = FilenameUtils.getExtension(fn);
        String suffix    = MiscUtils.isEmpty(extension) ? "" : "." + extension;
        String prefix    = candidate;
        int idx          = 0;

        Pattern regex    = Pattern.compile("^(.+?)_([0-9]+)$", Pattern.CASE_INSENSITIVE);
        Pattern imgRegex = Pattern.compile("^IMG_([0-9]+)$",   Pattern.CASE_INSENSITIVE);
        Matcher m        = regex.matcher(candidate);
        Matcher mImg     = imgRegex.matcher(candidate);

        // Match only non-image sequences, in order to avoid confusion with sending images.
        if (m.matches() && !mImg.matches()){
            prefix = m.group(1);
            idx    = Integer.parseInt(m.group(2));
        }

        // Try to continue with sequence.
        for(int retry=0; retry < 100; retry++){
            candidate = String.format("%s_%02d%s", prefix, idx+1, suffix);
            if (!fnames.contains(candidate.toLowerCase())){
                success = true;
                break;
            }
        }

        if (success){
            return candidate;
        }

        // Generate some random name so collision is not probable.
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        for(int retry=0; retry < 100; retry++) {
            final String newDate = dateFormatter.format(new Date());
            final String uuid    = UUID.randomUUID().toString();
            final String rndPart = String.format("%s_%s", newDate, uuid.substring(0, 8));
            candidate = String.format("%s%s%s", prefix, rndPart, suffix);

            if (!fnames.contains(candidate.toLowerCase())){
                success = true;
                break;
            }
        }

        if (!success){
            throw new RuntimeException("Unable to generate unique file name");
        }

        return candidate;
    }

    /**
     * Loads data for a file specified by PEXFileToSendEntry.
     * Computes its hash, file size, file name, extension.
     * If file is from assets, it is handled differently - need to be accessed directly.
     */
    protected FTHolder.FileEntry getFtFileEntry(FTHolder holder, FTHolder.FileToSendEntry toSend, PhotoResizeOptions photoResizeOptions) {
        MimeTypeMap mime = MimeTypeMap.getSingleton();
		// the constructor will set file, filename, fileSystemName, isSecure
        FTHolder.FileEntry fe = new FTHolder.FileEntry(toSend.fileUri);
        fe.doGenerateThumb = true;
        fe.ext      = FilenameUtils.getExtension(fe.filename);
		// fe.getSize depends on correct setting of isSecure!
		fe.fileSize = fe.getSize(getCtxt().getContentResolver());
		// file size is used by resizeToDisk to check, if resizing helped

        // Check filename against existing fnames in archive to avoid collisions.
        final String origFname = fe.filename;
        fe.filename = checkFileNameDuplicate(holder.fnames, origFname);

		// in case photo is resized, these are the original values, that we use for ReceivedFile record
		// (for sent file list). then we can delete the temporary resized images
		fe.originalFilename = fe.filename;
		fe.originalFileSize = fe.fileSize;
		fe.originalFile = fe.file;
		fe.originalUri = fe.getUri();

		if (photoResizeOptions.doResize() && FileUtils.isImage(fe.filename)) {
			Log.d(TAG, "Resizing to disk");
			// also will compute fe.originalSha256
			long crc = resizeToDisk(fe, photoResizeOptions);
			Log.df(TAG, "Finished resizing attempt, filename [%s] crc %d", fe.filename, crc);
		}

        holder.fnames.add(fe.filename.toLowerCase());
        holder.orderedFnames.add(fe.filename);
        holder.fnameCollisionFound |= !origFname.equalsIgnoreCase(fe.filename);

        // Build meta information storage.
        fe.metaB = MetaFileDetail.newBuilder();
        fe.metaB.setExtension(fe.ext);
        fe.metaB.setFileName(fe.filename);
        fe.metaB.setFileSize(fe.fileSize);
        fe.metaB.setThumbNameInZip(fe.filename);
        if (fe.sha256 != null) {
            fe.metaB.setHash(ByteString.copyFrom(fe.sha256));
        } else {
            fe.metaB.clearHash();
        }

        final String mimeType = !MiscUtils.isEmpty(toSend.mimeType) ? toSend.mimeType : mime.getMimeTypeFromExtension(fe.ext);
        fe.metaB.setMimeType(mimeType == null ? "application/octet-stream" : mimeType);

        if (!MiscUtils.isEmpty(toSend.title)) {
            fe.metaB.setTitle(toSend.title);
        }
        if (!MiscUtils.isEmpty(toSend.desc)) {
            fe.metaB.setDesc(toSend.desc);
        }
        if (toSend.fileDate != null) {
            fe.metaB.setFileTimeMilli(toSend.fileDate.getTime());
        }

        return fe;
    }

    /**
     * Prepares FtHolder for upload, specifying all paths needed during upload phase.
     */
    public void prepareHolderPaths(FTHolder holder) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, InvalidAlgorithmParameterException, IOException {
        if (holder==null){
            throw new IllegalArgumentException("Holder cannot be null");
        }

        holder.initFileStruct();

        // Generate IVs & store to FTholder.
        holder.fileIv[META_IDX] = new byte[AESCipher.AES_BLOCK_SIZE];
        holder.fileIv[ARCH_IDX] = new byte[AESCipher.AES_BLOCK_SIZE];
        rand.nextBytes(holder.fileIv[META_IDX]);
        rand.nextBytes(holder.fileIv[ARCH_IDX]);

        // Encrypt given data by AES-CBC
        holder.fileCipher[META_IDX] = prepareCipher(holder, true, META_IDX);
        holder.fileCipher[ARCH_IDX] = prepareCipher(holder, true, ARCH_IDX);

        final String nonce2 = Base64.encodeBytes(holder.nonce2);
        final String pathNonce = getFilenameFromBase64(nonce2);
        final File cacheDir = getStorageDirectory(getCtxt(), true);
        Log.vf(TAG, "Cache dir=[%s] exists=%s", cacheDir.getAbsolutePath(), cacheDir.exists());

        // Create temporary files.
        // Create in external storage - can do it, since files are protected (encrypted content).
        holder.file[META_IDX] = File.createTempFile("ft_meta_"+pathNonce+"_", ".tmp", cacheDir);
        holder.file[ARCH_IDX] = File.createTempFile("ft_arch_"+pathNonce+"_", ".tmp", cacheDir);
        holder.filePath[META_IDX] = holder.file[META_IDX].getAbsolutePath();
        holder.filePath[ARCH_IDX] = holder.file[ARCH_IDX].getAbsolutePath();

        final File thumbZipFile = File.createTempFile("ft_thumb_"+pathNonce+"_", ".tmp", cacheDir);
        holder.thumbZipPath = thumbZipFile.getAbsolutePath();
    }

	/**
	 * Process list of FileEntries, if some of them are for photos, that were resized, delete the resized files.
	 * Used at the end of file upload or after error in file upload.
	 * @param files2send
	 */
	public void deleteResizedFiles(List<FTHolder.FileEntry> files2send) {
		if (files2send == null) {
			return;
		}
		for (FTHolder.FileEntry fe : files2send) {
			if (fe.tempUri != null) {
				try {
					FileStorage storage = FileStorage.getFileStorageByUri(fe.tempUri.getUri(), ctxt.getContentResolver());
					boolean deleted = false;
					if (storage != null) {
						deleted = storage.delete(ctxt.getContentResolver(), false);
					}
					Log.df(TAG, "Temporary resized photo [%s] was deleted? [%b]", fe.tempUri, deleted);
				} catch (FileStorage.FileStorageException e) {
					Log.wf(TAG, "Temporary resized photo [%s] does not exist", fe.tempUri);
				}

			}
		}
	}

    /**
     * Deletes all stored temporary files associated with this holder record.
     * @param holder
     */
    public void deleteArtifacts(FTHolder holder){
        final File tempMetaFile = holder.file[META_IDX];
        final File tempArchFile = holder.file[ARCH_IDX];
        if (tempMetaFile != null){
            try {
                tempMetaFile.delete();
            } catch(Exception ex){
                Log.e(TAG, "Cannot remove meta file", ex);
            }
        }

        if (tempArchFile != null){
            try {
                tempArchFile.delete();
            } catch(Exception ex){
                Log.e(TAG, "Cannot remove pack file", ex);
            }
        }

        if (!MiscUtils.isEmpty(holder.thumbZipPath)){
            try {
                final File thumbZip = new File(holder.thumbZipPath);
                thumbZip.delete();
            } catch(Exception ex){
                Log.e(TAG, "Cannot remove thumbnail zip", ex);
            }
        }
    }

    /**
     * Builds PEXPbUploadFileKey structure needed for file upload and serializes it to NSData.
     * Has to be done before file upload.
     */
    public UploadFileKey buildUkeyData(FTHolder holder) throws IOException {
        // Construct uKey according to the protocol.
        UploadFileKey.Builder keyBuilder = UploadFileKey.newBuilder();
        keyBuilder.setSaltb(ByteString.copyFrom(holder.saltb));
        keyBuilder.setGy(ByteString.copyFrom(holder.getGyData()));
        keyBuilder.setSCiphertext(ByteString.copyFrom(holder.encXB));
        keyBuilder.setMac(ByteString.copyFrom(holder.macEncXB));
        final UploadFileKey ukey = keyBuilder.build();

        holder.ukeyData = ProtoBuffHelper.writeTo(ukey);
        return ukey;
    }

	/**
	 * Creates a thumb for given file and stores it in the thumb ZIP.
	 * @param holder
	 * @param fe
	 */
	protected void createThumbInMemory(FTHolder holder, FTHolder.FileEntry fe){
		BufferedInputStream bis = null;
		ByteArrayOutputStream byteArrayOS = null;
		if (fe == null || fe.file == null || !fe.file.exists()){
			throw new RuntimeException("Thumbnail cannot be created, cannot obtain file");
		}

		try {
			byteArrayOS = new ByteArrayOutputStream();
			bis = new BufferedInputStream(fe.getInputStream(getCtxt().getContentResolver()));
			boolean success = MiscUtils.createThumb(getCtxt(), bis, byteArrayOS, THUMBNAIL_LONG_EDGE, 90);

			if (!success || byteArrayOS.size() == 0){
				throw new RuntimeException("Thumbnail cannot be created");
			}

			byte thumbnail[] = byteArrayOS.toByteArray();

			final String thumbFilename = FilenameUtils.getBaseName(fe.filename) + ".jpg";
			ZipEntry entry = new ZipEntry(thumbFilename);

			CRC32 crc = new CRC32();
			crc.update(thumbnail, 0, thumbnail.length);
			Long checkSum = crc.getValue();
			entry.setSize(thumbnail.length);
			entry.setCompressedSize(thumbnail.length);
			entry.setCrc(checkSum);
			entry.setTime(fe.file.lastModified());
			entry.setMethod(ZipEntry.STORED);

			fe.metaB.setThumbNameInZip(thumbFilename);
			holder.thumbZip.putNextEntry(entry);
			MiscUtils.closeSilently(bis);

			holder.thumbZip.write(thumbnail, 0, thumbnail.length);

			holder.thumbZip.closeEntry();
			holder.thumbZip.flush();
		} catch(Exception e){
			Log.e(TAG, "Exception in generating a thumbnail", e);
		} finally {
			MiscUtils.closeSilently(bis);
			MiscUtils.closeSilently(byteArrayOS);
		}
	}

	/**
	 * Trying to save one read of file, if the EXIF is in the first two blocks of JPEG
	 * (if this even is a JPEG), then we should be able to get the EXIF orientation.
	 */
	protected class ExifInputStream extends FilterInputStream {

		private final int JPEG_EXIF_SIZE_LIMIT = 64*1024;
		private final int ARRAY_SIZE = 2 * JPEG_EXIF_SIZE_LIMIT;

		private byte[] fileBeginning;
		private int bytesRead;
		private boolean wasClosed;

		/**
		 * Constructs a new {@code FilterInputStream} with the specified input
		 * stream as source.
		 * <p>
		 * <p><strong>Warning:</strong> passing a null source creates an invalid
		 * {@code FilterInputStream}, that fails on every method that is not
		 * overridden. Subclasses should check for null in their constructors.
		 *
		 * @param in the input stream to filter reads on.
		 */
		protected ExifInputStream(InputStream in) {
			super(in);
			fileBeginning = new byte[ARRAY_SIZE];
			bytesRead = 0;
			wasClosed = false;
		}

		@Override
		public int read() throws IOException {
			int read = super.read();
			if (read != -1 && bytesRead < ARRAY_SIZE) {
				fileBeginning[bytesRead] = (byte) read;
				bytesRead++;
			}
			return read;
		}

		@Override
		public int read(@NonNull byte[] buffer) throws IOException {
			int read = super.read(buffer);
			if (read != -1 && bytesRead < ARRAY_SIZE) {
				int lengthToCopy = Math.min(read, ARRAY_SIZE - bytesRead);
				System.arraycopy(buffer, 0, fileBeginning, bytesRead, lengthToCopy);
				bytesRead += lengthToCopy;
			}
			return read;
		}

		@Override
		public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
			int read = super.read(buffer, byteOffset, byteCount);
			if (read != -1 && bytesRead < ARRAY_SIZE) {
				int lengthToCopy = Math.min(read, ARRAY_SIZE - bytesRead);
				System.arraycopy(buffer, byteOffset, fileBeginning, bytesRead, lengthToCopy);
				bytesRead += lengthToCopy;
			}
			return read;
		}

		@Override
		public void close() throws IOException {
			wasClosed = true;
			super.close();
		}

		public int getOrientation() {
			if (!wasClosed) {
				return -1;
			} else {
				// Exif.getOrientation(byte[]) returns 0 for wrong format as well
				return Exif.getOrientation(fileBeginning);
			}
		}
	}

	/**
	 *
	 * @param fe the file entry being downsized
	 * @param resizeOptions
	 * @return crc of the file or <0 if downsizing was skipped or failed
	 */
	protected long resizeToDisk(FTHolder.FileEntry fe, PhotoResizeOptions resizeOptions) {
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		if (fe == null || fe.file == null || !fe.file.exists()){
			throw new RuntimeException("Cannot resize, cannot obtain file");
		}

		if (resizeOptions.getJpegQuality() < 0 || resizeOptions.getJpegQuality() > 100) resizeOptions.setJpegQuality(90);

		FileStorage tempStorage = null;

		try {
			// first get dimensions of the image
			bis = new BufferedInputStream(fe.getInputStream(getCtxt().getContentResolver()));

			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			options.inPreferQualityOverSpeed = true;
			/* null == */ BitmapFactory.decodeStream(bis, null, options);

			MiscUtils.closeSilently(bis);

			int realWidth = options.outWidth;
			int realHeight = options.outHeight;

			if (realWidth <= 0 || realHeight <= 0) {
				throw new IllegalArgumentException("Image dimensions are invalid");
			}

			int longEdge = Math.max(realWidth, realHeight);

			// if the image is smaller than requested, do nothing
			if (longEdge <= resizeOptions.getLongEdgePixels()) {
				Log.df(TAG, "Bitmap resize: Image will not be resized to %d, because it is already %d by %d", resizeOptions.getLongEdgePixels(), realWidth, realHeight);
				return -1;
			}

			// decoder can downsample by power of 2
			// this is useful, if image is at least twice as big - it will save initial memory

			double dimensionFactor = (double) longEdge / resizeOptions.getLongEdgePixels();

			Log.df(TAG, "Bitmap resize: Image's longer edge is %.1f times larger than requested (%d vs %d)", dimensionFactor, longEdge, resizeOptions.getLongEdgePixels());

			// if e.g. 20 % is tolerated, then picture 1.2-times as big is okay
			if ((dimensionFactor - 1) <= resizeOptions.getUpperBoundPercent()) {
				Log.d(TAG, "Bitmap resize: Dimension imprecision is within tolerated bounds");
				Log.df(TAG, "Bitmap resize: Original size %d by %d, desired long edge %d", realWidth, realHeight, resizeOptions.getLongEdgePixels());
				return -1;
			}

			// factor of 2, by which we can resize the image
			int samplingFactor = 1;
			boolean samplingFactorInBound = false;

			// multiply the sampling factor by two, while upper bound is broken (image too big)
			while (longEdge / (2 * samplingFactor) > resizeOptions.getLongEdgePixels() * (1 + resizeOptions.getUpperBoundPercent())) {
				samplingFactor *= 2;
			}
			// scaled image will be larger or equal to upper bound
			// if we further reduce image size to half, it will be smaller than upper bound

			if (longEdge / samplingFactor == resizeOptions.getLongEdgePixels() * (1 + resizeOptions.getUpperBoundPercent())) {
				// we have hit upper bound
				samplingFactorInBound = true;
				Log.d(TAG, "Bitmap resize: Hit upper bound");
			} else {
				if (longEdge / (2 * samplingFactor) >= resizeOptions.getLongEdgePixels() * (1 - resizeOptions.getLowerBoundPercent())) {
					// by halving, we have passed lower bound and upper bound
					// no need to resize additionally
					samplingFactorInBound = true;
					samplingFactor *= 2;
					Log.d(TAG, "Bitmap resize: Passed upper and lower bound");
				} else {
					// image is larger than upper bound and half of image is smaller than lower bound
					// we need to resize additionally
					samplingFactorInBound = false;
					Log.d(TAG, "Bitmap resize: Missed upper and lower bound");
				}
			}

			Log.df(TAG, "Bitmap resize: Sampling by factor of %d will reduce the original bitmap from %d by %d to %d by %d",
					samplingFactor, realWidth, realHeight, realWidth / samplingFactor, realHeight / samplingFactor);
			Log.df(TAG, "Bitmap resize: The bound for longer edge are %f and %f. In bounds? %b",
					resizeOptions.getLongEdgePixels() * (1 + resizeOptions.getUpperBoundPercent()), resizeOptions.getLongEdgePixels() * (1 - resizeOptions.getLowerBoundPercent()), samplingFactorInBound);

			options = new BitmapFactory.Options();
			if (samplingFactor != 1) {
				options.inSampleSize = samplingFactor;
			}

			final String resizedFilename = sanitizeFileName(FilenameUtils.getBaseName(fe.filename) + "_" + resizeOptions.getLongEdgePixels() + "px.jpg");

			tempStorage = FileStorage.newFileStorageResolveNameConflicts(PreferencesManager.getSecureTempFolder(ctxt).getAbsolutePath(), resizedFilename, ctxt.getContentResolver());
			ExifInputStream exifIS = new ExifInputStream(fe.getInputStream(getCtxt().getContentResolver()));
			MessageDigestInputStream messageDigestInputStream = new MessageDigestInputStream(exifIS);
			messageDigestInputStream.setHashAlgorithm(FILE_HASH_ALG);
			bis = new BufferedInputStream(messageDigestInputStream);

			Log.df(TAG, "Bitmap resize: Decoding bitmap [%s] resizedFilename: [%s]", fe.filename, resizedFilename);
			Bitmap decoded = BitmapFactory.decodeStream(bis, null, options);
			if (decoded == null) {
				throw new RuntimeException("Could not decode bitmap");
			}

			MiscUtils.closeSilently(bis);
			fe.originalSha256 = messageDigestInputStream.digest();

			int orientation = exifIS.getOrientation();

			Log.df(TAG, "Bitmap resize: orientation is %d", orientation);

			Matrix matrix = new Matrix();

			// assign as if sampling solved the problem
			int width = decoded.getWidth();
			int height = decoded.getHeight();

			if (!samplingFactorInBound) {
				// additional resizing is needed

				width = resizeOptions.getLongEdgePixels();
				height = resizeOptions.getLongEdgePixels();
				if (realWidth < realHeight) {
					width = (int) (width * ((double) realWidth / realHeight));
				} else {
					height = (int) (height * ((double) realHeight / realWidth));
				}

				Log.d(TAG, "Bitmap resize: Scaling sampled bitmap");
				Log.df(TAG, "Bitmap resize: Original size %d by %d", realWidth, realHeight);
				Log.df(TAG, "Bitmap resize: Sampled size %d by %d", decoded.getWidth(), decoded.getHeight());
				Log.df(TAG, "Bitmap resize: Resizing to %d by %d", width, height);

				matrix.postScale((float) width / decoded.getWidth(), (float) height / decoded.getHeight());

				//Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, false);
			}

			if (orientation != 0) {
				matrix.postRotate(orientation);
				Log.df(TAG, "Bitmap resize: Image will be rotated by %d", orientation);
			}

			if (!samplingFactorInBound || orientation != 0) {
				Log.df(TAG, "Bitmap resize: Resizing to %d by %d, downsampled bitmap was %d by %d", width, height, decoded.getWidth(), decoded.getHeight());
				// decoded bitmap, start at [0,0], process process entire image, scale and/or rotate as needed
				Bitmap scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);

				if (scaled != decoded) {
					// createScaledBitmap can reuse the bitmap if we are scaling by 1 (by mistake?)
					// no longer need the decoded bitmap
					decoded.recycle();
				}

				if (scaled == null) {
					throw new RuntimeException("Could not scale bitmap");
				}

				Log.df(TAG, "Bitmap resize: Scaled and rotated to %d by %d", scaled.getWidth(), scaled.getHeight());

				decoded = scaled;
			}

			bos = new BufferedOutputStream(tempStorage.getOutputStream(ctxt.getContentResolver(), true));

			Log.d(TAG, "Bitmap resize: Compressing bitmap");
			boolean success = decoded.compress(Bitmap.CompressFormat.JPEG, resizeOptions.getJpegQuality(), bos);
			// no longer need the decoded (or scaled, if scaling was done) bitmap
			decoded.recycle();
			Log.df(TAG, "Bitmap resize: Finished bitmap, success? %b", success);
			if (!success){
				throw new RuntimeException("Could not compress bitmap into jpeg");
			}

			// necessary to close now in order to compute CRC
			MiscUtils.closeSilently(bos);

			if (fe.fileSize < tempStorage.getFileSize()) {
				Log.df(TAG, "Transformed image is larger than original image %d vs %d", tempStorage.getFileSize(), fe.fileSize);
				tempStorage.delete(ctxt.getContentResolver(), false);
				return -1;
			}

			Log.df(TAG, "Bitmap resize: path to resized file [%s], filename [%s]", tempStorage.getPath(), fe.filename);
			fe.file = new File(tempStorage.getPath(), tempStorage.getFileSystemName());
			fe.filename = tempStorage.getFilename();
			fe.fileSystemName = tempStorage.getFileSystemName();
			fe.isSecure = true;
			fe.fileSize = tempStorage.getFileSize();
			fe.ext = "jpg";
			fe.tempUri = new FileStorageUri(tempStorage.getUri());
			return tempStorage.getCrc32();
		} catch(Throwable e) {
			Log.e(TAG, "Bitmap resize: Cannot resize", e);
			if (e instanceof OutOfMemoryError) {
				Log.d(TAG, "Bitmap resize: Original or requested image size too large");
			}
			if (tempStorage != null) {
				boolean deleted = tempStorage.delete(ctxt.getContentResolver(), false);
				Log.df(TAG, "Bitmap resize: Deleted temp file? %b", deleted);
			}
			return -1;
		} finally {
			MiscUtils.closeSilently(bis);
			MiscUtils.closeSilently(bos);
		}
	}

    /**
     * Upload process. Takes file specified in PEXFtFileEntry and:
     *  a) generates its thumbnail and writes it to the meta zip.
     *  b) adds it to the ZIP arch + computes its SH256 hash.
     * Progress monitoring.
     */
    protected void processNormalFile(FTHolder holder, FTHolder.FileEntry fe, long bytesDoneSoFar, TransmitProgressI progressBlock) throws IOException, NoSuchAlgorithmException {
        // Do not add compression since most of the real world formats already do own compression.
        // Beware the library builds a seekable ZIP file (non-stream), so it can fill local header with
        // file size, compressed size and CRC32 after writing file data so streamed reader can read
        // file content with no compression. (With DEFLATE stream reader knows where data stream ends).
        try {
            createThumbInMemory(holder, fe);
        } catch(Throwable th){
            fe.metaB.clearThumbNameInZip();
        }

        java.security.MessageDigest md = java.security.MessageDigest.getInstance(FILE_HASH_ALG);
        ZipEntry entry = new ZipEntry(fe.filename);

        // If file being sent is a file that is already compressed,
        // do not waste CPU times for another layer of compression. Typically
        // do not compress images & PDF files.
        boolean compress = useCompression(fe.ext);
		Long checkSum = compress ? null : fe.getChecksum(getCtxt().getContentResolver());

		//boolean normalFile = true;

		if (compress || checkSum == null){
            entry.setMethod(ZipEntry.DEFLATED);
            Log.vf(TAG, "Using deflated, compress=%s, checksum=%s, name=%s", compress, checkSum, fe.filename);
        } else {
            // In streamed mode the size is required to be added.
            entry.setSize(fe.fileSize);
            entry.setCompressedSize(fe.fileSize);
            entry.setCrc(checkSum);
            entry.setTime(fe.file.lastModified());
            entry.setMethod(ZipEntry.STORED);
            Log.vf(TAG, "Using stored mode, size=%d, checksum=%s, name=%s", fe.fileSize, checkSum, fe.filename);

        }

        checkIfCancelled();
        holder.archZip.putNextEntry(entry);

		// Reopens input stream for encryption.
		InputStream fis = fe.getInputStream(getCtxt().getContentResolver());

        // Read file by bytes and write if possible
        int numBytes;
		long totalBytes = 0;
        byte[] bytes = new byte[8192];
        while ((numBytes = fis.read(bytes)) != -1) {
            holder.archZip.write(bytes, 0, numBytes);
            md.update(bytes, 0, numBytes);

            // Cancellation?
            if (isCancelled()){
                break;
            }

            // Progress - easier to monitor input, we know length of the input files...
            bytesDoneSoFar += numBytes;
			totalBytes     += numBytes;
            if (progressBlock != null){
                progressBlock.updateTxProgress(null, bytesDoneSoFar);
            }
        }

        holder.archZip.closeEntry();
        MiscUtils.closeSilently(fis);
        holder.archZip.flush();

        fe.fileSize = totalBytes;
        fe.sha256 = md.digest();
		fe.metaB.setHash(ByteString.copyFrom(fe.sha256));
    }

    /**
	 * Sets files to be sent to the remote party.
	 * Files have to exist, otherwise IOException is thrown.
	 * Can append textual title and description, optional.
	 * 
	 * Generates all needed information to the holder (IVs, MACs,
	 * file names).
	 * 
	 * Created files (meta, archive) are placed to externalCacheDir,
	 * file names are stored in holder. Generated files are E_m, E_p.
	 * IV and MAC is not prepended to the files - it has to be done manually during sending!
	 * 
	 * Supports operation cancellation.
	 *  
	 * @param holder 		Initialized by initFTHolder() method.
	 * @throws IOException 
	 */
	public FTHolder.FtPreUploadFilesHolder ftSetFilesToSend(FTHolder holder, UploadFileParams params) throws Exception{
		if (params == null || params.getFileUris() == null){
			throw new IllegalArgumentException("Paths cannot be null");
		}
        prepareHolderPaths(holder);
        final File tempMetaFile = holder.file[META_IDX];
        final File tempArchFile = holder.file[ARCH_IDX];
		
		// Build file list & check for existence
		final List<FTHolder.FileEntry> files2send = new ArrayList<>(params.getFileUris().size());

		// Total size of files to send - for progress monitoring.
		long files2sendTotalSize = 0;
		
		// Whole meta file container.
		MetaFile.Builder metaBuilder = MetaFile.newBuilder();
		metaBuilder.setTimestamp(System.currentTimeMillis());
		metaBuilder.setNumberOfFiles(params.getFileUris().size());
		
		// Set title if not empty - user defined, optional.
		if (!TextUtils.isEmpty(params.getTitle())){
			metaBuilder.setTitle(params.getTitle());
		}
		
		// Set description if not empty - user defined, optional.
		if (!TextUtils.isEmpty(params.getDesc())){
			metaBuilder.setDescription(params.getDesc());
		}
		
		// 
		// Processing individual files to send. 
		// Existence check, hashing, thumbnail generation.
		// Builds meta file.
		//
		Exception excToThrow = null;
		boolean success = false;
        FTHolder.FtPreUploadFilesHolder toReturn = new FTHolder.FtPreUploadFilesHolder();
		
		// Set progress to 0, this particular phase (meta file building)
		if (txprogress!=null){
			txprogress.setTotalOps(6);
			txprogress.setCurOp(0);
			updateProgress(txprogress, null, 0);
		}

        // Build meta archive.
		try {
			final int filesNum = MiscUtils.collectionSize(params.getFileUris());
			int curFile = 0;
			for (String fileUri : params.getFileUris()) {
				// Was operation cancelled?
				checkIfCancelled();
				FTHolder.FileToSendEntry toSend = new FTHolder.FileToSendEntry(fileUri);

				PreferencesConnector preferencesConnector = new PreferencesConnector(ctxt);

				// getFtFileEntry will generate thumbnails
				FTHolder.FileEntry fe = getFtFileEntry(holder, toSend, preferencesConnector.getPhotoResizeOptions());
				if (!fe.file.exists()) {
					throw new IOException("File does not exist: " + fileUri);
				}
				fe.metaB.setPrefOrder(curFile);

				files2send.add(fe);
				files2sendTotalSize += fe.fileSize;

				Log.v(TAG, String.format("File meta prepared, filename=[%s] len=[%d] extension=[%s] mime[%s]",
						fe.filename, fe.metaB.getFileSize(), fe.ext, fe.metaB.getMimeType()));

				// Progress
				updateProgress(txprogress, (double)curFile / (double)filesNum, 0.05 * ((double)curFile / (double)filesNum));
				curFile+=1;
			}
			// Operation was successful - we can proceed to the next step.
			success=true;

            // Update new file names (potential duplicates fix).
            if (holder.fnameCollisionFound){
                Log.v(TAG, "Fname collision found, going to update fnames");
				MessageManager.fileNotificationMessageFnameUpdate(getCtxt(), params, holder.orderedFnames);
            }

            // Operation was successful - we can proceed to the next step.
            metaBuilder.setNumberOfFiles(curFile);
			
		} catch(Exception e){
			Log.e(TAG, "Exception in processing files for sending.", e);
			excToThrow = e;
		} // End of generating a meta file.
		
		// If some exception to throw
		if (!success){
			deleteResizedFiles(files2send);
            deleteArtifacts(holder);
		}
		
		// If exception occurred, throw it.
		if (excToThrow!=null){
			throw excToThrow;
		}
		
		//
		// MetaFile contains timestamp, title, description, number of files
		// and individual MiteFileDetail messages.
		//
		// Now create archive of a given files & encrypt it on-the-fly (piped streams).
		//
		
		// Actual encryption, MAC computation, ...
		success = false;	// It is needed to set to false again, previous step set to true.
		OperationCancelledException cancelledExc=null;
		Exception e = null;
		
		// Outer references to close them outside try block if needed (e.g., catch, finally).
		BufferedOutputStream bos0 = null;
		BufferedOutputStream bosMetaEnc0 = null;

		try {
			Log.v(TAG, String.format("Temporary files abs=[%s] meta=[%s]",
                    tempMetaFile.getAbsolutePath(),
                    tempArchFile.getAbsoluteFile()));
			bos0 = new BufferedOutputStream(new FileOutputStream(tempArchFile));
			bosMetaEnc0 = new BufferedOutputStream(new FileOutputStream(tempMetaFile));

			// Create an archive file (ZIP) on-the-fly
			// in a separate writing thread. One Has to use two different threads
			// when using PipedStreams (otherwise deadlock may happen).
			final long files2sendTotalSizeF = files2sendTotalSize;
			if (txprogress!=null){
				txprogress.setCurOp(1);
			}

            // Reporting progress from the processing of the file.
            TransmitProgressI mainProgress = new TransmitProgressI() {
                @Override
                public void updateTxProgress(Double partial, double total) {
                    updateProgress(txprogress,
                            total / (double)files2sendTotalSizeF,
                            0.05 + (0.85)*(total / (double)files2sendTotalSizeF));
                }
            };

            // Write to: ZipOutputStream(NotClosingOutputStream(CipherOutputStream(BufferOutputStream(File))))
            CipherMacOutputStream cos = new CipherMacOutputStream(bos0, holder.fileCipher[ARCH_IDX], getFTMacObject(holder.ci[CI_MAC_ARCH], holder.fileIv[ARCH_IDX], holder.nonce2));
            holder.archZip = new ZipOutputStream(new CryptoHelper.NotClosingOutputStream(cos));

            // Separate ZIP archive for thumbnails -- will be appended to the meta file record in order to create meta file.
            final BufferedOutputStream bosThumb = new BufferedOutputStream(new FileOutputStream(holder.thumbZipPath));
            holder.thumbZip = new ZipOutputStream(bosThumb);

            // Process files to send individually, generating thumbnail, adding to the Zip archive.
            try {
                long bytesDoneSoFar = 0;
                for (FTHolder.FileEntry fe : files2send) {
                    processNormalFile(holder, fe, bytesDoneSoFar, mainProgress);
                    bytesDoneSoFar += fe.fileSize;

                    // Build meta detail and add to the meta message.
                    MetaFileDetail metaFileDetailMsg = fe.metaB.build();
                    fe.metaMsg = metaFileDetailMsg;
                    metaBuilder.addFiles(metaFileDetailMsg);
                }

            } catch (Exception exx) {
                Log.e(TAG, "Exception during creating a compressed archive in a separate thread", exx);
            } finally {
                MiscUtils.closeSilently(holder.archZip);
                cos.flush();
                MiscUtils.closeSilently(cos);
                MiscUtils.closeSilently(holder.thumbZip);
            }

			// Cancellation?
			checkIfCancelled();
			Log.v(TAG, "Archive file was encrypted.");
			
			// Compute MAC, hash, get size.
            holder.filePath[ARCH_IDX] = tempArchFile.getAbsolutePath();
			holder.fileMac[ARCH_IDX]  = cos.getMac().doFinal();
            if (txprogress!=null){
                txprogress.setCurOp(3);
                updateProgress(txprogress, null, 0.90);
            }

			holder.fileHash[ARCH_IDX] = computeFTFileHash(tempArchFile, holder.fileIv[ARCH_IDX], holder.fileMac[ARCH_IDX]);
			holder.fileSize[ARCH_IDX] = tempArchFile.length();
            holder.filePrepRec[ARCH_IDX] = getFilePrependRecord(holder, ARCH_IDX);
			if (txprogress!=null){
				txprogress.setCurOp(3);
				updateProgress(txprogress, null, 0.91);
			}
			
			// Add missing fields to the meta & dump meta to the piped stream - avoid writing 
			// unprotected files to the storage.
			// Meta file contains hash of the pack file to create binding between them.
			metaBuilder.setArchiveHash(ByteString.copyFrom(holder.fileHash[ARCH_IDX]));
			final MetaFile mf = metaBuilder.build();

            //
			// Create meta-file on-the-fly
            //
            CipherOutputStream cos2 = new CipherOutputStream(bosMetaEnc0, holder.fileCipher[META_IDX]);
            BufferedInputStream bis = null;
            try {
                // Part 1 = MetaFile protocol buffer record.
                mf.writeDelimitedTo(cos2);
                cos2.flush();

                // Part 2 = Thumb zip file.
                final File thumbFile = new File(holder.thumbZipPath);
                bis = new BufferedInputStream(new FileInputStream(thumbFile));
                byte buff[] = new byte[8192];
                for(;;){
                    int numBytes = bis.read(buff);
                    if (numBytes <= 0){
                        break;
                    }

                    cos2.write(buff, 0, numBytes);
                }
                cos2.flush();

                // Remove thumb zip, not needed anymore since it was copied to the meta file.
                thumbFile.delete();

                Log.v(TAG, "Meta file built sucessfully.");
            } catch (Exception exxx) {
                Log.e(TAG, "Exception during creating a meta file", exxx);
            } finally {
                MiscUtils.closeSilently(cos2);
                MiscUtils.closeSilently(bis);
            }

			if (txprogress!=null){
				txprogress.setCurOp(4);
				updateProgress(txprogress, null, 0.95);
			}

			// Cancellation?
			checkIfCancelled();
			Log.v(TAG, "Meta file was encrypted.");
			
			// Compute MAC for meta file.
			holder.fileMac[META_IDX] = generateFTFileMac(tempMetaFile, 0, holder.ci[CI_MAC_META], holder.fileIv[META_IDX], holder.nonce2, null, null);
			holder.filePath[META_IDX] = tempMetaFile.getAbsolutePath();

            if (txprogress!=null){
                txprogress.setCurOp(4);
                updateProgress(txprogress, null, 0.97);
            }
			
			// Can finally compute hashes & determine file size
			holder.fileHash[META_IDX] = computeFTFileHash(tempMetaFile, holder.fileIv[META_IDX], holder.fileMac[META_IDX]);
			holder.fileSize[META_IDX] = tempMetaFile.length();
            holder.filePrepRec[META_IDX] = getFilePrependRecord(holder, META_IDX);
			
			// Success.
			Log.v(TAG, "Files were processed successfully.");
			if (txprogress!=null){
				txprogress.setCurOp(5);
				updateProgress(txprogress, null, 1.0);
			}

            // Prepare structures for upload.
            buildUkeyData(holder);

            toReturn.files2send = files2send;
            toReturn.mf = mf;
            success = true;
			
		} catch(OperationCancelledException cex){
			Log.i(TAG, "Prepare files operation was cancelled");
			cancelledExc = cex;
			
		} catch(Exception ex){
			Log.e(TAG, "Exception in creating file transfer archives.", e);
			e = ex;

		} finally {
            MiscUtils.closeSilently(bos0);
            MiscUtils.closeSilently(bosMetaEnc0);
        }
		
		// If operation was not success, delete temporary files and reset holder.
		if (!success){
			Log.v(TAG, "Operation was not successful.");
			deleteResizedFiles(files2send);
            deleteArtifacts(holder);
			
			// Files cleanup.
			cleanFiles(holder);
            holder.resetFileData();
		}
		
		// Throw exception to inform about cancellation, if any happened.
		if (cancelledExc != null){
			throw new OperationCancelledException();
		}
		
		// If exception was thrown, propagate it to upper layer.
		if (e != null){
			throw e;
		}

		// at this point all files were zipped, so resized temporary photos can be deleted
		deleteResizedFiles(files2send);

        return toReturn;
	}
	
	/**
	 * Prepares cipher for file encryption from FTholder.
	 * Valid IVs and Ci are assumed to be stored in holder.
	 * 
	 * Used mainly for internal purposes.
	 * 
	 * @param holder
	 * @param encryption
	 * @param fileIdx
	 * @return
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchProviderException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidAlgorithmParameterException 
	 * @throws InvalidKeyException 
	 */
	protected Cipher prepareCipher(FTHolder holder, boolean encryption, int fileIdx) throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException{
		if (fileIdx<0 || fileIdx>=2){
			throw new IllegalArgumentException("Invalid file index");
		}
		
		final int keyIdx = fileIdx == META_IDX ? CI_ENC_META : CI_ENC_ARCH;
		throwIfNull(holder.ci[keyIdx]);
		throwIfNull(holder.fileIv[fileIdx]);
		
		// Convert ci keys to the AES encryption keys.
		// Ci length has to correspond to the AES encryption key size.
		SecretKey keys = new SecretKeySpec(holder.ci[keyIdx], 0, holder.ci[keyIdx].length, "AES");
		
		// IV parameter specs
		IvParameterSpec ivspec = new IvParameterSpec(holder.fileIv[fileIdx]);
		
		// Encrypt given data by AES-CBC
		Cipher aes = Cipher.getInstance(AESCipher.AES, BC);
		aes.init(encryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keys, ivspec);
		
		return aes;	     
	}

    /**
     * Returns data that should be prepended meta/archive file before sending.
     * @param holder
     * @param fileIdx
     * @return Returns byte[], length delimited UploadFileEncryptionInfo encoded message.
     */
    protected byte[] getFilePrependRecord(FTHolder holder, int fileIdx) throws IOException {
        if (fileIdx < 0 || fileIdx>=2){
            throw new IllegalArgumentException("Invalid file index");
        }

        // Build {IV + MAC} structure that prepends ciphertext.
        UploadFileEncryptionInfo.Builder b = UploadFileEncryptionInfo.newBuilder();
        b.setIv(ByteString.copyFrom(holder.fileIv[fileIdx]));
        b.setMac(ByteString.copyFrom(holder.fileMac[fileIdx]));
        final UploadFileEncryptionInfo msg = b.build();
        return ProtoBuffHelper.writeDelimitedTo(msg);
    }
	
	/**
	 * Writes particular file (meta/archive) to the output stream in a format
	 * specified by the protocol.
	 * 
	 * IV, MAC is taken from holder, protocol buffers message is built and written to 
	 * output stream, ciphertext is read from the file pointed by holder file path and written
	 * to the output stream. File has to be already encrypted, no encryption happens here.
	 * 
	 * Used mainly for internal purposes - writes file to the uploading stream.
	 * 
	 * Supports operation cancellation.
	 * 
	 * @param os
	 * @param fileIdx
	 * @throws IOException 
	 */
	protected void writeFileToStream(FTHolder holder, OutputStream os, int fileIdx, TransmitProgress progress) throws IOException{
		if (fileIdx<0 || fileIdx>=2){
			throw new IllegalArgumentException("Invalid file index");
		}
		
		// File existence check.
		final File file = new File(holder.filePath[fileIdx]);
		if (file.exists()==false){
			throw new IOException("Specified file does not exist, cannot continue.");
		}
		
		long totalSize = file.length();
		long written = 0;
		
		// Build {IV + MAC} structure that prepends ciphertext.
		os.write(holder.filePrepRec[fileIdx]);
		
		// Some additional progress information that may be useful.
		if (progress!=null){
			progress.reset();
			progress.setTotal(totalSize);
		}
		
		// Was operation cancelled?
		if (isCancelled()){
			Log.v(TAG, "write operation cancelled");
			throw new OperationCancelledException();
		}
		
		// Write encrypted file 
		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(new FileInputStream(file));
			
			int numBytes;
	   	 	byte[] bytes = new byte[1024];
	   	 	while ((numBytes = bis.read(bytes)) != -1) {
	   	 		os.write(bytes, 0, numBytes);
	   	 		
	   	 		written += numBytes;
	   	 		updateProgress(progress, null, (double)written / (double)totalSize);
	   	 		if (progress!=null){
	   	 			os.flush();
	   	 		}
	   	 		
	   	 		// Was operation cancelled?
	   	 		checkIfCancelled();
			}
	   	 	
	   	 	os.flush();
		} catch(IOException e){
            MiscUtils.closeSilently(bis);
			bis=null;
			
			throw e;
		} finally {
            MiscUtils.closeSilently(bis);
		}
	}

	/**
	 * Returns file to store downloaded files.
	 * @param fileIdx
	 * @param nonce2
	 * @return
	 * @throws IOException
	 */
	public static File getFileNameForDownload(Context ctxt, int fileIdx, String nonce2) throws IOException{
		File cacheDir = getStorageDirectory(ctxt, true);
		File tempFile = null;
		final String pathNonce = getFilenameFromBase64(nonce2);

		tempFile = new File(cacheDir, "ft_recv_"+fileIdx+"_"+pathNonce + ".tmp");
		return tempFile;
	}

	/**
	 * Returns file to store downloaded files.
	 * 
	 * @throws IOException 
	 */
	public File getFileNameForDownload(int fileIdx, String nonce2) throws IOException{
		return getFileNameForDownload(getCtxt(), fileIdx, nonce2);
	}

	/**
	 * Returns file name to store decrypted versions.
	 */
	public File getFileNameForDecrypted(int fileIdx, String nonce2) throws IOException {
		File cacheDir = getStorageDirectory(getCtxt(), true);
		File tempFile = null;
		final String pathNonce = getFilenameFromBase64(nonce2);

		tempFile = new File(cacheDir, "ft_dec_"+fileIdx+"_"+pathNonce + ".tmp");
		return tempFile;
	}

	/**
	 * Returns file name to store packed file.
	 */
	public File getFileNameForPacked(int fileIdx, String nonce2) throws IOException {
		File cacheDir = getStorageDirectory(getCtxt(), true);
		File tempFile = null;
		final String pathNonce = getFilenameFromBase64(nonce2);

		tempFile = new File(cacheDir, "ft_zip_"+fileIdx+"_"+pathNonce + ".tmp");
		return tempFile;
	}
	
	/**
	 * Reads given file from the stream in a specific format defined by protocol.
	 * IV, MAC, Ciphertext.
	 * 
	 * IV and MAC are stored to holder (parsed from Protocol Buffer message that prepends
	 * ciphertext in the given InputStream), ciphertext is stored to a new temporary file,
	 * path is stored to holder. File is not decrypted.
	 * 
	 * It totalSize is null, the size of the given file is unknown, thus updates number of bytes read, not percents.
	 * 
	 * Used mainly for internal purposes - reads file from the downloading stream.
	 * 
	 * If allow re-download is enabled, fixed file name is used and file is appended.
	 * 
	 * Supports canceling operation in progress.
	 * 
	 * @param holder ftHolder
	 * @param is InputStread to read file from.
	 * @param fileIdx file index to read data from input stream is to file.
	 * @param allowReDownload if true continued download is allowed for the transfer.
	 * @param progress progress monitoring object.
	 * @param totalSize total size of the file in input stream. If not null, it is used for relative progress monitoring.
	 * @throws IOException 
	 */
	protected void readFileFromStream(FTHolder holder, InputStream is, int fileIdx, boolean allowReDownload, TransmitProgress progress, Long totalSize) throws IOException{
		if (fileIdx<0 || fileIdx>=2){
			throw new IllegalArgumentException("Invalid file index");
		}
		
		final String nonce2 = Base64.encodeBytes(holder.nonce2);
		
		// Create temporary files.
		// Create in external storage - can do it, since files are protected (encrypted content).
		//
		// If allow re-download is set, create file name with specified filename since it 
		// may already exist.
		File tempFile = getFileNameForDownload(fileIdx, nonce2);
		
		// If file does not exist and allowReDownload==true, create new one
		if (allowReDownload && !tempFile.exists()){
			tempFile.createNewFile();
		}
		
		// Read ciphertext to a file, set new file to FTholder.
		holder.filePath[fileIdx] = tempFile.getAbsolutePath();
		final long alreadyRead = tempFile.length();
		long read = alreadyRead; // May be continued download.
		long readNow = 0;
		boolean ok = false;
		
		// Was operation cancelled?
		if (isCancelled()){
			tempFile.delete();
			throw new OperationCancelledException();
		}
		
		BufferedOutputStream bos = null;
		try {
			bos = new BufferedOutputStream(new FileOutputStream(tempFile, allowReDownload));
			
			boolean cancelled = false;
			int numBytes;
			final byte[] bytes = new byte[2048];
	   	 	while ((numBytes = is.read(bytes)) != -1) {
	   	 		bos.write(bytes, 0, numBytes);
	   	 		
	   	 		read += numBytes;
	   	 		readNow += numBytes;
	   	 		updateProgress(progress, null, totalSize==null ? read : (double)(read) / (double)(totalSize + alreadyRead));
	   	 		
	   	 		// Was operation cancelled?
	   			if (isCancelled()){
	   				cancelled=true;
	   				break;
	   			}
			}

			if (totalSize != null && readNow != totalSize){
				Log.ef(TAG, "Download size does not match! Downloaded %s vs. content length %s", readNow, totalSize);
			}

	   	 	bos.flush();
            MiscUtils.closeSilently(bos);
			Log.vf(TAG, "Download has finished, startOffset=%d, readNow=%d, totalSize=%d, totalWithOffset=%d, newTempFileSize: %d",
					alreadyRead, readNow, totalSize, totalSize + alreadyRead, tempFile.length());

	   	 	// If numbytes != -1, stream was not entirely read -> cancelled.
	   	 	// But to be sure, new variable cancelled is used;
	   	 	if (cancelled){
	   	 		Log.v(TAG, "Reading cancelled");
	   	 		tempFile.delete();
	   	 		
	   	 		throw new OperationCancelledException();
	   	 	}
	   	 	
	   	 	holder.fileSize[fileIdx] = tempFile.length();
	   	 	ok=true;

		} catch(IOException e){
            MiscUtils.closeSilently(bos);
			throw e;

		} finally {
			// If process was not finished successfully, delete temporary file.
			if (!ok && !allowReDownload){
				Log.vf(TAG, "Reading was not successful, deleting temporary file %s", tempFile.getAbsolutePath());
				tempFile.delete();
			}
		}
	}
	
	/**
	 * Verifies MAC of file pointed by holder.
	 * After verification holder.archiveOffset is set to point at the start of the encrypted archive.
	 *
	 * @param holder
	 * @throws IOException
	 * @throws IllegalStateException
	 * @throws NoSuchProviderException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws MACVerificationException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchPaddingException
	 */
	public boolean partialDecryptFile(FTHolder holder, int fileIdx) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, IllegalStateException, IOException, MACVerificationException, NoSuchPaddingException, InvalidAlgorithmParameterException{
		if (fileIdx<0 || fileIdx>=2){
			throw new IllegalArgumentException("Invalid file index");
		}

		// File existence check.
		final File file = new File(holder.filePath[fileIdx]);
		if (!file.exists()){
			throw new IOException("Specified file does not exist, cannot continue.");
		}

		// At first read meta information from the beginning of the file.
		final long origFileLen = file.length();
		final CountingInputStream input = new CountingInputStream(new FileInputStream(file));
		int structSize = ProtoBuffHelper.getDelimitedMessageSize(input);

		final long offsetForSize = input.getCtr();
		InputStream limitedInput = new LimitedInputStream(input, structSize);
		UploadFileEncryptionInfo info = UploadFileEncryptionInfo.parseFrom(limitedInput);
		holder.fileIv[fileIdx]  = info.getIv().toByteArray();
		holder.fileMac[fileIdx] = info.getMac().toByteArray();

		final long offset = offsetForSize + structSize;
		MiscUtils.closeSilently(limitedInput);
		Log.vf(TAG, "Decrypting file, offset=%s, fullLen=%s", offset, origFileLen);

		// At first compute MAC.
		final long fileLen = origFileLen - offset;
		final int macKey = fileIdx == META_IDX ? CI_MAC_META : CI_MAC_ARCH;

		updateProgress(txprogress, null, 0.001);
		byte[] computedMac = generateFTFileMac(file, offset, holder.ci[macKey], holder.fileIv[fileIdx], holder.nonce2, canceller, new TransmitProgress() {
			@Override
			public void updateTxProgress(Double partial, double total) {
				updateProgress(txprogress, null, 0.1 * (total / (double) fileLen));
			}
		});

		// Check if HMAC matches the given value.
		if (!java.util.Arrays.equals(computedMac, holder.fileMac[fileIdx])){
			throw new MACVerificationException("MAC does not match");
		}

		holder.archiveOffset = offset;

		return true;
	}
	
	/**
	 * Builds meta file from the holder.
	 * 
	 * Assumes file was already partially decrypted and holder.archiveOffset points at start of encrypted meta archivede.
	 * 
	 * @param holder
	 * @return
	 * @throws IOException 
	 */
	public MetaFile reconstructEncryptedMetaFile(FTHolder holder) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, InvalidAlgorithmParameterException {
		if (holder==null || holder.archiveOffset==null){
			throw new IllegalArgumentException("Holder or meta file null");
		}

		// File existence check.
		final File file = new File(holder.filePath[META_IDX]);
		if (!file.exists() || !file.canRead()){
			throw new IOException("Specified file does not exist, cannot continue.");
		}

		CountingInputStream countingIS = null;
		CountingInputStream decryptedCountingIS = null;
		MetaFile mf = null;

		try {
			Cipher aes = prepareCipher(holder, false, META_IDX);

			countingIS = new CountingInputStream(new FileInputStream(file));
			if (holder.archiveOffset != countingIS.skip(holder.archiveOffset)) {
				throw new IOException("Unexpected end of file, cannot continue.");
			}
			if (holder.archiveOffset != countingIS.getCtr()) {
				throw new IOException("Incorrect position in stream.");
			}

			decryptedCountingIS = new CountingInputStream(new CipherInputStream(countingIS, aes));
			try {
				mf = MetaFile.parseDelimitedFrom(decryptedCountingIS);
			} catch (Exception e) {
				Log.w(TAG, "Meta file could not be parsed, trying backward compatible", e);
				aes = prepareCipher(holder, false, META_IDX);
				countingIS = new CountingInputStream(new FileInputStream(file));
				if (holder.archiveOffset != countingIS.skip(holder.archiveOffset)) {
					throw new IOException("Unexpected end of file, cannot continue.");
				}
				decryptedCountingIS = new CountingInputStream(new CipherInputStream(countingIS, aes));
				mf = MetaFile.parseFrom(decryptedCountingIS);
			}

			holder.metaInArchiveOffset = decryptedCountingIS.getCtr();
		} finally {
			MiscUtils.closeSilently(countingIS);
			MiscUtils.closeSilently(decryptedCountingIS);
		}

		return mf;
	}

	/**
	 * TODO JavaDoc
	 * Extracts encrypted ZIP archive at given file with options.
	 *
	 * The encrypted archive starts at given offset
	 */
	public FTHolder.UnpackingResult unzipEncryptedArchiveAtFile(
			FTHolder holder, int fileTypeIdx, String filePath, long offset, FTHolder.UnpackingOptions options) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, InvalidAlgorithmParameterException {
		if (filePath == null){
			throw new IllegalArgumentException("Holder or meta file null");
		}

		if (options == null){
			throw new IllegalArgumentException("Options is null");
		}

		if (options.destinationDirectory == null) {
			throw new IllegalArgumentException("Destination directory is null");
		}

		FTHolder.UnpackingResult res = new FTHolder.UnpackingResult();
		res.setFinishedOK(true);

		// File existence check.
		final File file = new File(filePath);
		if (!file.exists() || !file.canRead()){
			throw new IOException("Specified file does not exist, cannot continue.");
		}

		// Create destination directory if missing.
		final File destDir = new File(options.destinationDirectory);
		if (!destDir.exists() && options.createDirIfMissing){
			destDir.mkdirs();
		}

		// Check if destination directory is usable (exists, is directory and is writable).
		if (!destDir.exists() || !destDir.isDirectory() || !destDir.canWrite()){
			Log.wf(TAG, "Problem with destination directory: %s", destDir.getCanonicalPath());
			throw new IOException("Problem with destination directory, probably does not exist.");
		}

		BufferedInputStream bis = null;
		ZipInputStream zis = null;

		// Store all created files so they can be removed in case of an error / exception.
		List<FileStorage> tmpFiles = new ArrayList<>();

		// Final extraction result stored here.
		List<FTHolder.UnpackingFile> createdFiles = new ArrayList<>();
		IOException e = null;

		// Prepare decryption
		Cipher aes = prepareCipher(holder, false, fileTypeIdx);

		// Extracting the archive in try block.
		try {
			Log.df(TAG, "Going to extract archive from offset %d", offset);
			bis = new BufferedInputStream(new FileInputStream(file));

			// skip meta
			if (offset != bis.skip(offset)) {
				Log.wf(TAG, "Unexpected end of file %s", file.getAbsolutePath());
				throw new IOException("Unexpected end of file");
			}

			// estimate
			long size = file.length();

			CipherInputStream cis = new CipherInputStream(new CryptoHelper.NotClosingInputStream(bis), aes);

			if (DHKeyHelper.META_IDX == fileTypeIdx) {
				cis.skip(holder.metaInArchiveOffset);
			}

			zis = new ZipInputStream(cis);

			long processedSize = 0;

			// Read the archive
			ZipEntry ze = zis.getNextEntry();
			if (ze == null) {
				Log.wf(TAG, "No entries in archive");
			}
			while(ze!=null){
				if (ze.isDirectory()){
					throw new UnknownArchiveStructureException("Directories are not allowed");
				}
				Log.df(TAG, "Processing zip entry %s", ze.getName());
				// Sanitize filename & create file instance.
				final String fname = options.fnamePrefix == null ? ze.getName() : options.fnamePrefix + ze.getName();
				String sanitizedFname = sanitizeFileName(fname);
				//String extension = FilenameUtils.getExtension(sanitizedFname);
				if (fname.contains("/")){
					throw new IOException("Invalid file name");
				}

				FileStorage fileStorage;

				try {
					fileStorage = FileStorage.newFileStorage(destDir.getAbsolutePath(), sanitizedFname, getCtxt().getContentResolver());
				} catch(FileStorage.FileStorageException ex) {
					if (FTHolder.FilenameConflictCopyAction.THROW_EXCEPTION.equals(options.actionOnConflict)){
						throw new IOException("File already exists");
					} else if (FTHolder.FilenameConflictCopyAction.OVERWRITE.equals(options.actionOnConflict)){
						fileStorage = FileStorage.getFileStorageByPathAndName(destDir.getAbsolutePath(), sanitizedFname, getCtxt().getContentResolver());
						if (fileStorage == null) {
							Log.ef(TAG, "Creation of FileStorage failed but corresponding FileStorage does not exist yet %s %s", destDir.getAbsolutePath(), sanitizedFname);
							continue;
						}
					} else {
						// rename on conflict
						fileStorage = FileStorage.newFileStorageResolveNameConflicts(destDir.getAbsolutePath(), sanitizedFname, getCtxt().getContentResolver());
					}
				}

				// Read file from ZIP archive and write it to the new file.
				BufferedOutputStream curBos = null;
				try {
					curBos = new BufferedOutputStream(fileStorage.getOutputStream(getCtxt().getContentResolver())); //new BufferedOutputStream(new FileOutputStream(cFile));
					// File is already created now, add to created file list so it can be
					// deleted in case of exception.
					tmpFiles.add(fileStorage);
					Log.v(TAG, String.format("Going to read file [%s] from the archive and write as [%s]", sanitizedFname, fileStorage));

					// Copy one stream to another.
					int numBytes;
					byte[] bytes = new byte[8192];
					while ((numBytes = zis.read(bytes)) != -1) {
						curBos.write(bytes, 0, numBytes);
						checkIfCancelled();
						processedSize += numBytes;
						// not precise, since processedSize is cleartext size
						updateProgress(txprogress, null, 0.1 + 0.9 * ((offset + processedSize) / (double) size));
					}
					curBos.flush();
				} catch(IOException ioe){
					e = ioe;
					Log.wf(TAG, "Exception when going to read file [%s] from the archive and write as [%s] ", e, sanitizedFname, fileStorage);
					break;
				} catch(Exception eoe){
					Log.wf(TAG, "Exception when going to read file [%s] from the archive and write as [%s] ", eoe, sanitizedFname, fileStorage);
					e = new IOException(eoe);
					break;
				} finally {
					// Stream has to be closed in both situations.
					MiscUtils.closeSilently(curBos);
				} // end of try-catch-finally block.

				if (isCancelled()){
					res.setFinishedOK(false);
					break;
				}

				Log.vf(TAG, "File %s successfully extracted", sanitizedFname);
				FTHolder.UnpackingFile fl = new FTHolder.UnpackingFile(ze.getName(), fileStorage.getFileSystemName(), fileStorage.getUri().toString(), fileStorage.getFileSize());//cFile.getAbsolutePath());
				createdFiles.add(fl);

				// Move on the next ZIP entry in the archive.
				ze = zis.getNextEntry();
			} // End of file in ZIP archive iteration.

		} catch(Exception ex){
			res.setEx(new IOException(ex));
			res.setFinishedOK(false);

		} finally {
			MiscUtils.closeSilently(zis);

			// In case of some exception.
			if (!res.isFinishedOK() || res.getEx() != null){
				Log.v(TAG, "Exception thrown during archive extraction");
				if (options.deleteNewFilesOnException){
					Log.vf(TAG, "Going to throw exception, deleting extracted files, len=%d", tmpFiles.size());
					for(FileStorage fl : tmpFiles){
						fl.delete(getCtxt().getContentResolver(), false);
					}
				}
			}
		} // end of try-catch-finally

		// Exception has to be thrown eventually.
		if (res.getEx() != null){
			Log.v(TAG, "Exception during extraction");
			throw res.getEx();
		}

		// Copy file names as result.
		res.setFiles(createdFiles);
		return res;
	}

	/**
	 * Extracts meta thumb ZIP archive at given file with options.
	 * Thumbnails are saved in DB (Thumbnail)
	 */
	public void unzipEncryptedMetaArchiveAtFile(FTHolder holder, List<FTHolder.DownloadFile> files, FTHolder.UnpackingOptions options) throws IOException {
		// File existence check.
		final File file = new File(holder.filePath[DHKeyHelper.META_IDX]);
		if (!file.exists() || !file.canRead()){
			throw new IOException("Specified file does not exist, cannot continue.");
		}

		// Store all created thumbnails so they can be removed in case of an error / exception.
		List<FTHolder.DownloadFile> tmpFiles = new ArrayList<>();

		ZipInputStream zis = null;

		try {
			// Prepare decryption
			Cipher aes = prepareCipher(holder, false, META_IDX);

			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
			if (holder.archiveOffset != bis.skip(holder.archiveOffset)) {
				Log.wf(TAG, "Unexpected end of file %s", file.getAbsolutePath());
				throw new IOException("Unexpected end of file");
			}
			CipherInputStream cis = new CipherInputStream(new CryptoHelper.NotClosingInputStream(bis), aes);
			if (holder.metaInArchiveOffset != cis.skip(holder.metaInArchiveOffset)) {
				Log.wf(TAG, "Unexpected end of file %s", file.getAbsolutePath());
				throw new IOException("Unexpected end of file");
			}
			zis = new ZipInputStream(cis);
			ZipEntry ze = zis.getNextEntry();
			if (ze == null) {
				Log.wf(TAG, "No entries in archive");
			}
			long processedSize = 0;
			// only estimate!
			long size = file.length() - holder.archiveOffset - holder.metaInArchiveOffset;
			while (ze != null) {
				if (ze.isDirectory()) {
					throw new UnknownArchiveStructureException("Directories are not allowed");
				}
				checkIfCancelled();
				Log.df(TAG, "Processing zip entry %s", ze.getName());
				// Sanitize filename & create file instance.
				final String fname = options.fnamePrefix == null ? ze.getName() : options.fnamePrefix + ze.getName();
				String sanitizedFname = sanitizeFileName(fname);
				if (fname.contains("/")) {
					throw new IOException("Invalid file name");
				}
				boolean fileFound = false;
				FTHolder.DownloadFile downloadFile = null;
				for (FTHolder.DownloadFile fldwn : files) {
					if (ze.getName() == null || !ze.getName().equals(fldwn.thumbNameInZip)) {
						continue;
					}
					downloadFile = fldwn;
					downloadFile.thumbFname = FilenameUtils.getName(ze.getName());
					downloadFile.thumbFileStorageUriString = FileStorageUri.newThumbnailUriString(holder.messageId + sanitizedFname, downloadFile.thumbFname);
					fileFound = true;
					break;
				}
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
				int numBytes;
				byte[] bytes = new byte[8192];
				while ((numBytes = zis.read(bytes)) != -1) {
					byteArrayOutputStream.write(bytes, 0, numBytes);
					checkIfCancelled();
					processedSize += numBytes;
					// not precise, since processedSize is cleartext size
					updateProgress(txprogress, null, 0.1 + 0.9 * (processedSize / (double) size));
				}
				if (!fileFound) {
					continue;
				}

				Thumbnail thumbnail = new Thumbnail();
				// We need properly formed URI here!
				thumbnail.setUri(downloadFile.thumbFileStorageUriString);
				thumbnail.setSender(holder.sender);
				thumbnail.setMessageId(holder.messageId);
				if (FTHolder.FilenameConflictCopyAction.OVERWRITE.equals(options.actionOnConflict)) {
					int deleted = getCtxt().getContentResolver().delete(Thumbnail.URI, Thumbnail.FIELD_URI + "=?", new String[]{thumbnail.getUri()});
					Log.df(TAG, "Deleted %d old thumbnails for uri %s", deleted, sanitizedFname);
				} else if (FTHolder.FilenameConflictCopyAction.THROW_EXCEPTION.equals(options.actionOnConflict)) {
					Cursor cursor = getCtxt().getContentResolver().query(Thumbnail.URI, Thumbnail.FULL_PROJECTION, Thumbnail.FIELD_URI + "=?", new String[]{thumbnail.getUri()}, null);
					if (cursor != null && cursor.getCount() != 0) {
						throw new IOException("Thumbnail already exists");
					}
				}
				thumbnail.setThumbnail(byteArrayOutputStream.toByteArray());
				MiscUtils.closeSilently(byteArrayOutputStream);

				getCtxt().getContentResolver().insert(Thumbnail.URI, thumbnail.getDbContentValues());

				tmpFiles.add(downloadFile);
				ze = zis.getNextEntry();
			}
			checkIfCancelled();
		} catch (Exception e) {
			List<String> tmpUris = new ArrayList<>();
			for (FTHolder.DownloadFile fldwn : tmpFiles) {
				tmpUris.add(fldwn.thumbFileStorageUriString);
				fldwn.thumbFileStorageUriString = null;
				fldwn.thumbFname = null;
				fldwn.thumbNameInZip = null;
			}
			getCtxt().getContentResolver().delete(Thumbnail.URI, Thumbnail.FIELD_URI + "=?", tmpUris.toArray(new String[tmpUris.size()]));
			throw new IOException(e);
		} finally {
			MiscUtils.closeSilently(zis);
		}
	}

	/**
	 * Unzips encrypted archive file sent in file transfer protocol.
	 * Archive is assumed to have only flat structure (no directories are allowed).
	 * Files from archive are saved to secure FileStorage.
 	 */
	public FTHolder.UnpackingResult unzipEncryptedArchive(FTHolder holder, int fileTypeIdx, FTHolder.UnpackingOptions options) throws IOException, NoSuchProviderException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException {
		if (holder == null || holder.filePath[fileTypeIdx] == null){
			throw new IllegalArgumentException("Holder or meta file null");
		}

		if (options == null){
			throw new IllegalArgumentException("Options is null");
		}

		if (options.destinationDirectory == null){
			throw new IllegalArgumentException("Directory null");
		}

		String               filename = holder.filePath[fileTypeIdx];
		FTHolder.UnpackingResult res  = new FTHolder.UnpackingResult();

		try {
			res = unzipEncryptedArchiveAtFile(holder, fileTypeIdx, filename, holder.archiveOffset, options);

			// No exception -> delete old archive file.
			if (options.deleteArchiveOnSuccess){
				Log.v(TAG, "Going to delete archive (onSuccess)");

				final File fl = new File(filename);
				if (fl.delete()){
					holder.filePath[ARCH_IDX] = null;
				}
			}

			// Delete meta archive on success
			if (options.deleteMetaOnSuccess && !TextUtils.isEmpty(holder.filePath[META_IDX])){
				Log.v(TAG, "Going to delete meta (onSuccess)");

				File mFile = new File(holder.filePath[META_IDX]);
				if (mFile.exists() && mFile.delete()){
					holder.filePath[META_IDX] = null;
				}
			}

			updateProgress(txprogress, null, 100);

			return res;
		} catch(IOException e){
			Log.vf(TAG, e, "Exception during extraction");
			res.setEx(e);
			res.setFinishedOK(false);
			res.setFiles(new ArrayList<FTHolder.UnpackingFile>());
			throw e;
		}
	}

    /**
	 * Uploads prepared files for the given user (uses userSip attribute).
	 * Files have to be already encrypted and prepared (with IV, MAC computed in FTHolder).
	 * Upload progress will be reported to txprogress.
	 * 
	 * @param holder
	 * @param ks			KeyStore with user private key, needed for HTTPs connection.
	 * @param password		KeyStore password.
	 * @return
	 * @throws Exception 
	 */
	public FTHolder.UploadResult uploadFile(FTHolder holder, KeyStore ks, String password, FTHolder.UploadResult res) throws Exception{
		if (res == null){
			res = new FTHolder.UploadResult();
		}
		
		// Get my domain
		ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(userSip, true));
		final String domain = mytsinfo.domain;
		
		// Determine full domain.
		String url2send = ServiceConstants.getDefaultRESTURL(domain, ctxt) + REST_UPLOAD_URI;
		Log.vf(TAG, "Going to upload file, url=[%s]", url2send);
		
		// Was operation cancelled?
		checkIfCancelled();
		
		// Set default HTTPS parameters - custom trustVerifier, keystore with user private key.
        SSLSOAP.SSLContextHolder sslHolder = null;
		try {
            sslHolder = SSLSOAP.installTrustManager4HTTPS(ks, password != null ? password.toCharArray() : null, ctxt);
			Log.v(TAG, "Initialized default ssl socket factory");
		}catch(Exception e){
			Log.e(TAG, "Cannot configure default HTTPS.");
		}
		
		// Was operation cancelled?
		checkIfCancelled();
		
		// Start upload process.
		Uploader upld = new Uploader(this, url2send, holder, SipUri.getCanonicalSipContact(userSip, false));
        upld.setConnectionTimeoutMilli(connectionTimeoutMilli);
        upld.setReadTimeoutMilli(readTimeoutMilli);
        upld.setSslContextHolder(sslHolder);
		upld.setProgress(txprogress);
		upld.setDebug(true);

		try {
			upld.runUpload();
		} catch(OperationCancelledException cex){
			res.cancelDetected = true;
			throw cex;

		} catch(IOException ioexc){
			res.ioErrorDetected = true;
			throw ioexc;
		}
		
		// Copy result back
		res.code = upld.getResponseCode();
		res.message = upld.getResponseMsg();
		res.response = upld.getResponse();
		return res;
	}
	
	/**
	 * Downloads file sent by user specified in holder. 
	 * After this method finishes, IV & MAC are parsed from prepended protocol buffers message
	 * from the input stream, ciphertext is stored to a file, its filename is stored appropriately 
	 * in the FTHolder. File is not decrypted.
	 * 
	 * @param holder		FTHolder with prepared keys, nonce2.
	 * @param ks			KeyStore with user private key, needed for HTTPs connection.
	 * @param password		KeyStore password.
	 * @param fileIdx		Which particular file to download.
	 * @param allowReDownload If true, re-download is allowed, using continued download, from position of already downloaded file.
	 * @throws IOException 	
	 */
	public int downloadFile(FTHolder holder, KeyStore ks, String password, int fileIdx, boolean allowReDownload) throws IOException{
		if (fileIdx<0 || fileIdx>=2){
			throw new IllegalArgumentException("Invalid file index");
		}
		
		final String nonce2 = Base64.encodeBytes(holder.nonce2);
		final String pathNonce = getFilenameFromBase64(nonce2);
		final String fileType = fileIdx==META_IDX ? "meta" : "pack"; // Magic strings defined on the server.
		
		// Get my domain
		final ParsedSipUriInfos mytsinfo = SipUri.parseSipUri(SipUri.getCanonicalSipContact(userSip, true));
		final String domain = mytsinfo.domain;
		
		// Determine full domain.
		final String url2send = ServiceConstants.getDefaultRESTURL(domain, ctxt) + REST_DOWNLOAD_URI + "/" + pathNonce + "/" + fileType;
		Log.vf(TAG, "Going to download file, url=[%s]", url2send);
		
		// Was operation cancelled?
		checkIfCancelled();
		
		// Set default HTTPS parameters - custom trustVerifier, keystore with user private key.
		try {
			SSLSOAP.installTrustManager4HTTPS(ks, password != null ? password.toCharArray() : null, ctxt);                            	
			Log.v(TAG, "Initialized default ssl socket factory");
		}catch(Exception e){
			Log.e(TAG, "Cannot configure default HTTPS.");
		}
		
		// Was operation cancelled?
		checkIfCancelled();
		
		// Continued download.
		long alreadyDownloaded = 0;
		if (allowReDownload){
			File toDownload = getFileNameForDownload(fileIdx, nonce2);
			if (toDownload.exists()){
				alreadyDownloaded = toDownload.length();
				Log.inf(TAG, "Re-downloading, file exists fileIdx=%s, filesize=%s", fileIdx, alreadyDownloaded);
			}
		}
		
		// Was operation cancelled?
		checkIfCancelled();
		
		// HTTPs GET download
		final URL url = new URL(url2send);
		final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        // Allow Inputs & Outputs
        connection.setDoInput(true); 
        connection.setDoOutput(false); // Has to be like this, otherwise POST is called.
        connection.setUseCaches(false);
        connection.setConnectTimeout(this.connectionTimeoutMilli);
        connection.setReadTimeout(this.readTimeoutMilli);
        
        // Not chunked mode -> we want to know content length in order to show progress.
        //connection.setRequestProperty("Transfer-Encoding","chunked");
        
        // Set GET method & required headers, connect. 
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "PhoneX");
        connection.setRequestProperty("Connection", "Keep-Alive");
        
        // Continued downloaded range.
        if (alreadyDownloaded>0){
        	connection.setRequestProperty("Range", "bytes="+(alreadyDownloaded)+"-");
        }
        
        // Was operation cancelled?
     	checkIfCancelled();

		// Opening new connection happens here.
        final InputStream is = connection.getInputStream();

		// Process response.
		final int resCode = connection.getResponseCode();
		if ((resCode / 100) != 2){
			Log.wf(TAG, "HTTP Response code is not 200 but [%s], assuming error, cannot continue.", resCode);
			return resCode;
		}

		final long lengthOfFile = connection.getContentLength();
		Log.v(TAG, String.format("Connected, code=%s. Download file length=%d", resCode, lengthOfFile));
        
        // Set length of the file if progress is set.
        if (txprogress!=null){
        	txprogress.setTotal(lengthOfFile);
        }
        
        // Was operation cancelled?
     	checkIfCancelled();
        
        InputStream readIs = is;
        if (debug){
        	final byte[] readd = readInputStream(is);
        	
        	@SuppressWarnings("resource")
			ByteArrayInputStream bis = new ByteArrayInputStream(readd);
        	final String recvd = MiscUtils.bytesToHex(readd, 64);
        	
        	Log.vf(TAG, "Received len=%s", readd.length);
        	Log.vf(TAG, "Received: %s", recvd);
        	readIs = bis;
        }
        
        // Was operation cancelled?
     	checkIfCancelled();
        
        // Read message given in input stream - use provided function.
        readFileFromStream(holder, readIs, fileIdx, allowReDownload, txprogress, lengthOfFile);
        
        MiscUtils.closeSilently(readIs);
		return resCode;
	}

	/**
	 * Removes download artifacts.
	 *
	 * @param ctxt
	 * @param holder
	 * @param fileIdx
	 */
	public static boolean cleanDownloadFile(Context ctxt, FTHolder holder, int fileIdx){
		if (holder.nonce2 == null){
			return false;
		}

		boolean deleted = false;
		try {
			final File fl = getFileNameForDownload(ctxt, fileIdx, Base64.encodeBytes(holder.nonce2));
			if (MiscUtils.fileExistsAndIsAfile(fl)){
				deleted = fl.delete();
			}

		} catch(Exception e){
			Log.ef(TAG, e, "Exception in clean download, idx: %d", fileIdx);
		}

		return deleted;
	}

	/**
	 * Download file.
	 *
	 * @param ctxt
	 * @param holder
	 * @param fileIdx
	 * @return
	 */
	public static boolean cleanFile(Context ctxt, FTHolder holder, int fileIdx){
		if (holder.filePath==null || TextUtils.isEmpty(holder.filePath[fileIdx])) {
			return false;
		}

		try {
			final File fl = new File(holder.filePath[fileIdx]);
			if (MiscUtils.fileExistsAndIsAfile(fl)){
				return fl.delete();
			}

		} catch(Exception e){
			Log.ef(TAG, e, "Exception in clean, idx: %d", fileIdx);
		}

		return false;
	}

	/**
	 * Cleans all files related to this index.
	 *
	 * @param ctxt
	 * @param holder
	 * @param fileIdx
	 * @return
	 */
	public static boolean cleanAllFiles(Context ctxt, FTHolder holder, int fileIdx){
		return cleanDownloadFile(ctxt, holder, fileIdx) || cleanFile(ctxt, holder, fileIdx);
	}

	/**
	 * Cleans all temporary files related to this session.
	 * @param holder FTholder object.
	 */
	public void cleanFiles(FTHolder holder){
		cleanAllFiles(getCtxt(), holder, META_IDX);
		cleanAllFiles(getCtxt(), holder, ARCH_IDX);
	}

	/**
	 * Deletes files that might be left over from failed download.
	 * @param holder
	 */
	public void deleteDownloadResiduals(FTHolder holder){
		if (holder.nonce2 == null){
			return;
		}

		cleanDownloadFile(getCtxt(), holder, META_IDX);
		cleanDownloadFile(getCtxt(), holder, ARCH_IDX);
	}
	
	/**
	 * Converts base64 string to a file name (removes /)
	 * Substitution: 
	 * / --> _ 
	 * + --> -
	 * 
	 * @param based
	 * @return
	 */
	public static String getFilenameFromBase64(String based){
		return based.replace("/", "_").replace("+", "-");
	}

    /**
	 * Determines whether was upload sucessful.
	 * @param res
	 * @return
	 */
	public static boolean wasUploadSuccessful(FTHolder.UploadResult res){
		if (res==null) return false;
		if (res.code!=200) return false;
		if (res.response==null) return false;
		if (res.response.hasErrorCode()==false) return false;
		return res.response.getErrorCode() == 0;
	}
	
	/**
	 * Returns service error code, if available, null otherwise.
	 * @param res
	 * @return
	 */
	public static Integer getUploadErrorCode(FTHolder.UploadResult res){
		if (res==null) return null;
		if (res.code!=200) return null;
		if (res.response==null) return null;
		if (res.response.hasErrorCode()==false) return null;
		return res.response.getErrorCode();
	}

    /**
	 * Reads whole input stream to a byte array.
	 * 
	 * @param is
	 * @return
	 * @throws IOException 
	 */
	public static byte[] readInputStream(InputStream is) throws IOException{
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int nRead;
		byte[] data = new byte[16384];

		while ((nRead = is.read(data, 0, data.length)) != -1) {
		  buffer.write(data, 0, nRead);
		}

		buffer.flush();
		return buffer.toByteArray();
	}
	
	/**
	 * Dumps holder to a string. 
	 * For debugging. Do not use in production (deprecation flag).
	 * 
	 * @deprecated
	 * @param holder
	 * @return
	 */
	public static String dumpHolder(FTHolder holder){
		if (holder==null) return "";
		StringBuilder sb = new StringBuilder();
		sb.append("nonceb: ").append(MiscUtils.bytesToHex(holder.nonceb)).append("\n")
		  .append("nonce2: ").append(MiscUtils.bytesToHex(holder.nonce2)).append("\n")
		  .append("salt1: ").append(MiscUtils.bytesToHex(holder.salt1)).append("\n")
		  .append("c: ").append(MiscUtils.bytesToHex(holder.c)).append("\n");
		
		// ci keys
		if (holder.ci!=null){
			for(int i=0; i<holder.ci.length; i++){
				sb.append("c_").append(i).append(": ").append(MiscUtils.bytesToHex(holder.ci[i])).append("\n");
			}
		}
		  
		sb.append("mb: ").append(MiscUtils.bytesToHex(holder.MB)).append("\n")
		  .append("xb: ").append(MiscUtils.bytesToHex(holder.XB.toByteArray())).append("\n")
		  .append("encXb: ").append(MiscUtils.bytesToHex(holder.encXB)).append("\n")
		  .append("macEncXb: ").append(MiscUtils.bytesToHex(holder.macEncXB)).append("\n");

		// Files
		for(int i=0; i<2; i++){
			if (holder.fileHash!=null)
				sb.append("fileHash[").append(i).append("]: ").append(MiscUtils.bytesToHex(holder.fileHash[i])).append("\n");
			if (holder.fileIv!=null)
				sb.append("fileIV[").append(i).append("]: ").append(MiscUtils.bytesToHex(holder.fileIv[i])).append("\n");
			if (holder.fileMac!=null)
				sb.append("fileMAC[").append(i).append("]: ").append(MiscUtils.bytesToHex(holder.fileMac[i])).append("\n");
			if (holder.fileSize!=null)
				sb.append("fileSize[").append(i).append("]: ").append(holder.fileSize[i]).append("\n");
			if (holder.filePath!=null)
				sb.append("filePath[").append(i).append("]: ").append(holder.filePath[i]).append("\n");
		}
		
		
		return sb.toString();
	}
	
	/**
	 * Determines whether to use compression in the file transfer according to the file transfer.
     * Most of the file formats already use internal compression.
     *
	 * @param extension
	 * @return
	 */
	public boolean useCompression(String extension){
		return ("txt".equalsIgnoreCase(extension) || "log".equalsIgnoreCase(extension));
	}
	
	/**
	 * Returns true if the local canceller signalizes a canceled state.
	 * @return
	 */
	public boolean isCancelled(){
		return this.canceller != null && this.canceller.isCancelled();
	}
	
	/**
	 * Throws exception if operation was cancelled.
	 * @return
	 * @throws OperationCancelledException
	 */
	public void checkIfCancelled() throws OperationCancelledException {
		if (this.canceller != null && this.canceller.isCancelled()){
			throw new OperationCancelledException();
		}
	}
	
	public String getUserSip() {
		return userSip;
	}

	public void setUserSip(String userSip) {
		this.userSip = userSip;
	}

	public String getMySip() {
		return mySip;
	}

	public void setMySip(String mySip) {
		this.mySip = mySip;
	}

	public X509Certificate getMyCert() {
		return myCert;
	}

	public void setMyCert(X509Certificate myCert) {
		this.myCert = myCert;
	}

	public X509Certificate getSipCert() {
		return sipCert;
	}

	public void setSipCert(X509Certificate sipCert) {
		this.sipCert = sipCert;
	}

	public PrivateKey getPrivKey() {
		return privKey;
	}

	public void setPrivKey(PrivateKey privKey) {
		this.privKey = privKey;
	}

	public SecureRandom getRand() {
		return rand;
	}

	public void setRand(SecureRandom rand) {
		this.rand = rand;
	}

	public Context getCtxt() {
		return ctxt;
	}

	public void setCtxt(Context ctxt) {
		this.ctxt = ctxt;
	}

	public TransmitProgress getTxprogress() {
		return txprogress;
	}

	public void setTxprogress(TransmitProgress txprogress) {
		this.txprogress = txprogress;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public int getConnectionTimeoutMilli() {
		return connectionTimeoutMilli;
	}

	public void setConnectionTimeoutMilli(int connectionTimeoutMilli) {
		this.connectionTimeoutMilli = connectionTimeoutMilli;
	}

	public int getReadTimeoutMilli() {
		return readTimeoutMilli;
	}

	public void setReadTimeoutMilli(int readTimeoutMilli) {
		this.readTimeoutMilli = readTimeoutMilli;
	}

	public Canceller getCanceller() {
		return canceller;
	}

	public void setCanceller(Canceller canceller) {
		this.canceller = canceller;
	}

}
