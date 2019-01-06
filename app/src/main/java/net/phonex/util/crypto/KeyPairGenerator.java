package net.phonex.util.crypto;

import android.content.Context;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.ASN1OctetString;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.DERNull;
import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.spongycastle.asn1.pkcs.PrivateKeyInfo;
import org.spongycastle.asn1.x500.RDN;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.asn1.x500.style.IETFUtils;
import org.spongycastle.asn1.x509.AlgorithmIdentifier;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.cms.CMSAlgorithm;
import org.spongycastle.cms.CMSException;
import org.spongycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.openssl.PEMReader;
import org.spongycastle.openssl.PEMWriter;
import org.spongycastle.openssl.PKCS8Generator;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.GenericKey;
import org.spongycastle.operator.OutputEncryptor;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.pkcs.PKCS10CertificationRequest;
import org.spongycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.spongycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.spongycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder;
import org.spongycastle.util.io.pem.PemGenerationException;
import org.spongycastle.util.io.pem.PemObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class KeyPairGenerator {	
	// new provider name
    private static final String BC = org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    
    // it is necessary to pass provider this way, string does not work
	private static final BouncyCastleProvider prov = new org.spongycastle.jce.provider.BouncyCastleProvider();
    
    // SpongyCastle static initialization
    static {
    	Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
    }
    
    // key pair generated
    private KeyPair keyPair = null;
    // certificate signing request
	private PKCS10CertificationRequest csr = null;
	// encryption key used to encrypt private file
	private GenericKey encKey = null;
	// signed certificate returned from server is stored here
	private X509Certificate cert = null;
	
	/**
	 * Generates public-private RSA key pair
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 */
	public void generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException{
		/*RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
		generator.init(new RSAKeyGenerationParameters(
		        new BigInteger("10001", 16),//publicExponent
		        SecureRandom.getInstance("SHA1PRNG"),//prng
		        2048,//strength
		        140//certainty
		    ));
		this.keyPair = generator.generateKeyPair();*/
		
		//
		// KeyPair generation with java security, bouncy castle as provider
		//
		java.security.KeyPairGenerator kpGen = java.security.KeyPairGenerator.getInstance("RSA",  prov);
		kpGen.initialize(2048, new SecureRandom());
		this.keyPair = kpGen.generateKeyPair();
	}
	
	/**
	 * Generates certificate signing request
	 * @param sip
	 * @throws Exception 
	 */
	public void generateCSR(String sip) throws Exception{
		this.csr = KeyPairGenerator.generateRequest("EMAILADDRESS="+sip+", CN="+sip+", OU=PhoneX, O=PhoneX, L=Gibraltar, ST=Gibraltar, C=GI", this.keyPair);
		//this.csr = KeyPairGenerator.generateRequest("C=CY, ST=Larnaca, L=Larnaca, O=Phonex ltd, OU=Phonex, CN="+sip+", EMAILADDRESS="+sip, this.keyPair);
	}
	
	/**
	 * Returns PEM format of CSR
	 * @return
	 * @throws IOException 
	 */
	public byte[] getCSRAsPEM() throws IOException{
		final String type = "CERTIFICATE REQUEST";
		byte[] encoding = csr.getEncoded();
		PemObject pemObject = new PemObject(type, encoding);
		return createPEM(pemObject);
	}
	
	/**
	 * Returns PEM format of Certificate
	 * @return
	 * @throws IOException 
	 * @throws CertificateEncodingException 
	 */
	public byte[] getCertificateAsPEM(X509Certificate cert) throws IOException, CertificateEncodingException{
		final String type = "CERTIFICATE";
		byte[] encoding = cert.getEncoded();
		PemObject pemObject = new PemObject(type, encoding);
		return createPEM(pemObject);
	}
	
	/**
	 * Reads PEM encoded certificate
	 * @param pemEncodedCert
	 * @return
	 * @throws IOException
	 */
	public X509Certificate readCertFromPEM(byte[] pemEncodedCert) throws IOException{
		// certificate request is in PEM -> PEM reader to read it. Interpret it as
        // base64 encoded string, encoding should not matter here
        PEMReader pemReader = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemEncodedCert)));
        
        // PEM reader returns still old certification request - getEncoded() and constructor
        X509Certificate tmpObj = (X509Certificate) pemReader.readObject();
        pemReader.close();
		return tmpObj;
	}
	
	/**
	 * Creates PEM object representation and returns byte array
	 * @param obj
	 * @return
	 * @throws IOException 
	 */
	public byte[] createPEM(Object obj) throws IOException {
		ByteArrayOutputStream barrout = new ByteArrayOutputStream();
		this.createPEM(new OutputStreamWriter(barrout), obj);
		// return encoded PEM data - collect bytes from ByteArrayOutputStream		
		return barrout.toByteArray();
	}
	
	/**
	 * Creates PEM file from passed object
	 * @param writer
	 * @throws IOException
	 */
	public void createPEM(Writer writer, Object obj) throws IOException{
		PEMWriter pemWrt = new PEMWriter(writer, BC);
	    pemWrt.writeObject(obj);
	    pemWrt.flush();
	    pemWrt.close();
	}
	
	/**
	 * Builds CertificateSigningRequest from passed CN and key pair
	 * @param CN
	 * @param pair
	 * @return
	 * @throws Exception
	 */
	public static PKCS10CertificationRequest generateRequest(String CN, KeyPair pair) throws Exception {
		X500Name x500name = new X500Name(CN);
		
		// prepare public key as pkinfo
		byte[] publickeyb = pair.getPublic().getEncoded();
	    SubjectPublicKeyInfo subPkInfo = new SubjectPublicKeyInfo((ASN1Sequence)ASN1ObjectIdentifier.fromByteArray(publickeyb));
	    
	    // construct builder in right way
		PKCS10CertificationRequestBuilder builder = new PKCS10CertificationRequestBuilder(x500name, subPkInfo);
		
		// signer
        ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA")
                .setProvider(new BouncyCastleProvider())
                .build(pair.getPrivate());
        
        // build CSR with signer
        return builder.build(signer);
	  }
	
	/**
	 * Encrypted RSA private key
	 * @deprecated
	 * @return
	 * @throws IOException
	 * @throws CMSException 
	 */
	public PKCS8EncryptedPrivateKeyInfo encryptPrivateKey() throws IOException, CMSException{
		//new PrivateKeyInfo
		AlgorithmIdentifier algID = new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, new DERNull());
		//new PrivateKeyInfo
		PrivateKeyInfo pki = new PrivateKeyInfo(algID, ASN1OctetString.fromByteArray(this.keyPair.getPrivate().getEncoded()));  
		// encryption builder
		PKCS8EncryptedPrivateKeyInfoBuilder builder = new PKCS8EncryptedPrivateKeyInfoBuilder(pki);
		//OutputEncryptor
		// here is implementation of this output encryptor
		// http://www.jarvana.com/jarvana/view/org/bouncycastle/bcmail-jdk14/1.46/bcmail-jdk14-1.46-sources.jar!/org/bouncycastle/cms/jcajce/JceCMSContentEncryptorBuilder.java?format=ok
		// Encryption key used to encrypt stream is generated at random in constructor;
		OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.DES_EDE3_CBC).setProvider(prov).build();
		// store encryption key locally
		encKey = encryptor.getKey();
		// and finally - build encrypted private key with random encryption key
		PKCS8EncryptedPrivateKeyInfo ret = builder.build(encryptor);
		return ret;
	}
	
	/**
	 * Creates PKCS8 PEM object with encrypted private key
	 * 
	 * @param pk
	 * @param password
	 * @return
	 * @throws PemGenerationException
	 * @throws NoSuchProviderException
	 * @throws NoSuchAlgorithmException
	 */
	public PemObject encryptPrivateKey(PrivateKey pk, char[] password) throws PemGenerationException, NoSuchProviderException, NoSuchAlgorithmException{
		// extract the encoded private key, this is an unencrypted PKCS#8 private key
		//byte[] encodedprivkey = pk.getEncoded();
		
		// TODO: using deprecated methods, but SpongyCastle is a bit late in release by BouncyCastle with proper
		// PKCS8 support.
		PKCS8Generator pkcs8generator = new PKCS8Generator(pk, PKCS8Generator.PBE_SHA1_3DES, BC);
		pkcs8generator.setIterationCount(CertificatesAndKeys.PemPasswordSchemeV2.PKCS_ITERATIONS);
		pkcs8generator.setPassword(password);
		PemObject pemObj = pkcs8generator.generate();
		return pemObj;
	}
	
	/**
	 * Assembles new key store with passed certificate and private key
	 * @param cert
	 * @param privkey
	 * @param password
	 * @throws KeyStoreException 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 */
	public KeyStore buildNewKeyStore(X509Certificate cert, PrivateKey privkey, char[] password) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
     	// open new file in internal storage as output stream
     	KeyStore keyStore = KeyStore.getInstance("pkcs12", KeyPairGenerator.getProv());
     	// storage password
     	keyStore.load(null, password); 
     	// add one cert-key pair to storage
     	keyStore.setKeyEntry("default", privkey, password, new Certificate[] { cert }); 
		return keyStore;
	}
	
	/**
	 * Reads PKCS12 KeyStore from storage - input stream.
	 * Closing stream is up to stream creator
	 * @param fis
	 * @param key
	 * @return
	 * @throws KeyStoreException 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 */
	public KeyStore readKeyStore(InputStream fis, char[] key) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		// try to read now from internal storage
     	KeyStore keyStoreLoaded = KeyStore.getInstance(CertificatesAndKeys.KEYSTORE_PKCS12, KeyPairGenerator.getProv());
     	keyStoreLoaded.load(fis, key);
     	return keyStoreLoaded;
	}
	
	/**
	 * Reads PKCS12 KeyStore from file in local storage (context needed)
	 * @param filepath
	 * @param ctxt
	 * @param key
	 * @return
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public KeyStore readKeyStore(String filepath, Context ctxt, char[] key) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
      	FileInputStream fis = ctxt.openFileInput(filepath);
      	KeyStore ks = readKeyStore(fis, key);
      	
      	try {
      		fis.close();
      	} catch(Exception ex){
      		
      	}
      	
      	return ks;
	}
	
	/**
	 * Store initialized key store to outputStream
	 * @param keyStore
	 * @param fos
	 * @param key
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public void saveKeyStore(KeyStore keyStore, OutputStream fos, char[] key) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		keyStore.store(fos, key);
	}
	
	/**
     * Extracts CN from X500Name
     * 
     * @deprecated
     * @param x500name
     * @return 
     */
    public static String getCNfromX500Name(X500Name x500name) throws CertificateException{
         try {
            RDN cn = x500name.getRDNs(BCStyle.CN)[0];
            return IETFUtils.valueToString(cn.getFirst().getValue());
        } 
        catch(Exception ex){
            throw new CertificateException("Problem with client certificate, cannot get user ID", ex);
        }
    }

    /**
     * Copies CA certificate in PEM format from assets
     * 
     * @throws IOException 
     */
    public static void copyCAFromAssets(Context context) throws IOException{
    	String DestinationFile = context.getFilesDir().getPath() + File.separator + CertificatesAndKeys.CA_LIST_FILE;
    	if (!new File(DestinationFile).exists()) {
    		copyFromAssetsToStorage(context, CertificatesAndKeys.CA_ASSET_PATH, DestinationFile);    	  
    	}
    }
    
    /**
     * Copies file from Assets directory to internal storage
     * 
     * @param Context
     * @param SourceFile
     * @param DestinationFile
     * @throws IOException
     */
    public static void copyFromAssetsToStorage(Context Context, String SourceFile, String DestinationFile) throws IOException {
		InputStream IS = Context.getAssets().open(SourceFile);
		OutputStream OS = new FileOutputStream(DestinationFile);
		copyStream(IS, OS);
		OS.flush();
		OS.close();
		IS.close();
	}
    
    /**
     * Helper method for copying input stream to output stream\
     * 
     * @param Input
     * @param Output
     * @throws IOException
     */
    public static void copyStream(InputStream Input, OutputStream Output) throws IOException {
		byte[] buffer = new byte[5120];
		int length = Input.read(buffer);
		while (length > 0) {
		  Output.write(buffer, 0, length);
		  length = Input.read(buffer);
		}
    }
    
    /**
     * Creates PEM CA-List, Cert, Private key files in internal storage (PJSIP needs them).
     * Filenames are static, taken from CertificatesAndKeys class.
     * 
     * @param context
     * @param cert
     * @param pk
     * @param password
     * @throws IOException 
     * @throws CertificateEncodingException 
     * @throws NoSuchAlgorithmException 
     * @throws NoSuchProviderException 
     */
    public static void createPemFiles(Context context, X509Certificate cert, PrivateKey pk, char[] password, String sip)
    		throws IOException, CertificateEncodingException, NoSuchProviderException, NoSuchAlgorithmException{
    	
    	KeyPairGenerator kpg = new KeyPairGenerator();
    	KeyPairGenerator.copyCAFromAssets(context);
     	
     	// Convert the user certificate to PEM format and store to the internal storage
     	byte[] certPem = kpg.getCertificateAsPEM(cert);
//     	FileOutputStream fos = context.openFileOutput(CertificatesAndKeys.CERT_FILE, Context.MODE_PRIVATE);
     	FileOutputStream fos = context.openFileOutput(CertificatesAndKeys.deriveUserCertFilename(sip), Context.MODE_PRIVATE);
     	fos.write(certPem);
     	fos.flush();
     	fos.close();
     	
     	// Create user's public key file in PEM format for PJSIP signature generation.
     	// It is cached variant, public key from certificate. Should be consistent 
     	// with public key in certificate!
     	PublicKey pubkey = cert.getPublicKey();
     	byte[] pubkeyPem = kpg.createPEM(pubkey);
//     	fos = context.openFileOutput(CertificatesAndKeys.PUBKEY_FILE, Context.MODE_PRIVATE);
     	fos = context.openFileOutput(CertificatesAndKeys.derivePubKeyFilename(sip), Context.MODE_PRIVATE);
     	fos.write(pubkeyPem);
     	fos.flush();
     	fos.close();
     	
     	// and finally the private key
     	PemObject pobj = kpg.encryptPrivateKey(pk, password);
     	byte[] privkeyPem = kpg.createPEM(pobj);
//     	fos = context.openFileOutput(CertificatesAndKeys.PRIVKEY_FILE, Context.MODE_PRIVATE);
     	fos = context.openFileOutput(CertificatesAndKeys.derivePrivKeyFilename(sip), Context.MODE_PRIVATE);
     	fos.write(privkeyPem);
     	fos.flush();
     	fos.close();
    }
    
	public KeyPair getKeyPair() {
		return keyPair;
	}

	public PKCS10CertificationRequest getCsr() {
		return csr;
	}

	public GenericKey getEncKey() {
		return encKey;
	}

	public static String getBc() {
		return BC;
	}

	public static BouncyCastleProvider getProv() {
		return prov;
	}

	public X509Certificate getCert() {
		return cert;
	}

	public void setCert(X509Certificate cert) {
		this.cert = cert;
	}
}
