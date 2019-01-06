package net.phonex.util.crypto;

import android.content.Context;
import android.database.Cursor;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;

import net.phonex.PhonexSettings;
import net.phonex.core.MemoryPrefManager;
import net.phonex.pref.PhonexConfig;
import net.phonex.core.SipUri;
import net.phonex.core.SipUri.ParsedSipContactInfos;
import net.phonex.db.DBProvider;
import net.phonex.db.entity.UserCertificate;
import net.phonex.pref.PreferencesConnector;
import net.phonex.pub.parcels.StoredCredentials;
import net.phonex.soap.AuthCheckTask.KeyDerivationException;
import net.phonex.soap.AuthHashGenerator;
import net.phonex.soap.CertGenParams;
import net.phonex.soap.IUserIdentityHolder;
import net.phonex.soap.UserPrivateCredentials;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.pki.TrustVerifier;

import org.spongycastle.asn1.x500.RDN;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.asn1.x500.style.IETFUtils;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.jcajce.JcaX500NameUtil;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;


/**
 * Helper utility class for manipulation with certificates and keys
 * 
 * @author ph4r05
 */
public class CertificatesAndKeys {
	private static final String THIS_FILE="CertsAndKeys";
	
	/**
	 * Default key alias used to associate keys in keystore
	 */
	public static final String DEFAULT_KEY_ALIAS="1";
	
	/**
	 * How often should be performed valid certificate re-check? (default 3 minutes)
	 */
	public static final long CERTIFICATE_OK_RECHECK_PERIOD = 1000 * 60;
	
	/**
	 * How often should be performed invalid certificate re-check? (default 10 seconds)
	 */
	public static final long CERTIFICATE_NOK_RECHECK_PERIOD = 1000 * 10;
	
	/**
	 * Minimum timeout for push certificate notifications (default 2 minutes).
	 */
	public static final long CERTIFICATE_PUSH_TIMEOUT = 1000 * 60 * 2;
	
	/**
	 * Maximal number of push updates for one contact in 24 hours.
	 */
	public static final long CERTIFICATE_PUSH_MAX_UPDATES = 10;

	/**
	 * Path in assets to CA certificate in PEM format
	 */
	public static final String CA_ASSET_PATH = "ca-crt.pem";
	
	/**
	 * CA list file in internal storage
	 */
	public static final String CA_LIST_FILE = "ca-crt.pem";

    /**
     * CA file for web page.
     */
    public static final String WEB_CA_FILE = "web-crt.pem";

	public static final String PKCS12Container_OLD = "client.p12";
	public static final String CERT_FILE_OLD = "user-crt.pem";
	public static final String PUBKEY_FILE_OLD = "user-pub.pem";
	public static final String PRIVKEY_FILE_OLD = "user-privkey.pem";
	
	/**
	 * PKCS12 KeyStore identifier for Security Provider
	 */
	public static final String KEYSTORE_PKCS12 = "PKCS12";
	
	/**
	 * BKS KeyStore identifier for Security Provider
	 */
	public static final String KEYSTORE_BKS = "BKS";

	/**
	 * Date of deprecation of the old CA
	 */
	public static final Date OLD_CA_DEPRECATED_DATE = new Date(1477384358000L);

	public static String deriveDbFilename(String sip) throws NoSuchAlgorithmException{
		return deriveFilenameFromSip(sip, ".db");
	}

	public static String derivePkcs12Filename(String sip) throws NoSuchAlgorithmException{
		return deriveFilenameFromSip(sip, ".p12");
	}

	public static String derivePrivKeyFilename(String sip) throws NoSuchAlgorithmException{
		return deriveFilenameFromSip(sip, "_user_privkey.pem");
	}

	public static String derivePubKeyFilename(String sip) throws NoSuchAlgorithmException{
		return deriveFilenameFromSip(sip, "_user_pub.pem");
	}

	public static String deriveUserCertFilename(String sip) throws NoSuchAlgorithmException{
		return deriveFilenameFromSip(sip, "_user_crt.pem");
	}

	private static String deriveFilenameFromSip(String sip, String suffix) throws NoSuchAlgorithmException {
        String sipHash = MessageDigest.hashHexSha1(sip);
        // take first 15 characters, probability of collision is low
        // filename is <hash prefix>.<extension>
        String filename = sipHash.substring(0, 15) + suffix;
		Log.vf(THIS_FILE, "deriveFilenameFromSip; sip=%s, suffix=%s, filename=%s", sip, suffix, filename);
		return filename;
    }

	/**
	 * Password scheme for PKCS KeyStore v1.
	 * 
	 * @author ph4r05
	 *
	 */
	public static class PkcsPasswordSchemeV1 {
		/**
		 * Identity
		 * 
		 * @param ctxt
		 * @param user
		 * @param key
		 * @return
		 */
		public static String getStoragePass(Context ctxt, String user, String key) {
			return key;
		}
	}
	
	/**
	 * Password scheme for PKCS v2.
	 *   input = username + | + userpasswd + | + MAGIC_KEY + | + ANDROID_UNIQUE_ID
	 *   salt = random 32byte salt stored for given user in shared preferences
	 *   step1 = scrypt(input, salt, N=2^15, r=1, p=1, dkeylen=32)
	 *   step2 = pbkdf2(step1, salt, iterations=8192, mac=MACWithSHA1)
	 *   step1_64 = base64.encode(step1)
	 *   step2_64 = base64.encode(step2)
	 *   step3 = base64.encode(SHA512(step1_64 + | + step2_64))
	 *   return step3
	 * 
	 * Proposal of scheme v3 for more security - not able to brute-force:
	 * Basic ideas: 
	 * 	* Server assisted encryption, similar to card assisted, but server can be compromised.
	 *  * Layered encryption
	 *  
	 * Will work in this way: 
	 * 
	 * Initialization phase: 
	 * 	* User generates a random 256-bit AES encryption key only for this purpose and uploads it to the server
	 * 
	 * Decryption of the private key file stored locally:
	 *  1. Generate decryption key (1. layer), using v2 scheme (scrypt, PBKDF2, {cpu, ram}-heavy computation)
	 *  2. Ask server to decrypt file from step 1 to decrypt it for you (2. layer)
	 *  3. Generate another decryption key (another magic value, hash algorithm), using v2 finally obtaining 
	 *  final encryption key for the private key file. 
	 *  
	 *  Benefits: 
	 *   * Bruteforcing is hard since 2. layer needs server communication (possible blocking like PIN),
	 *  may have exponential increase in timeout on server side.
	 *   * User cannot know after peeling layer 1 off whether his password is correct or not, has to finish
	 *   whole step.
	 *   * If server gets compromised, attacker won't learn private key of the user.
	 *   * User can have AES server key stored somewhere for backup case (printed QR code locked in the bank).
	 *   
	 *   Another possible work-factor based idea:
	 *   * Do simple scrypt() crypto-currency mining on each auth step, proof-of-work to the server
	 *   before server allows you to verify password.
	 * 
	 * @author ph4r05
	 *
	 */
	public static class PkcsPasswordSchemeV2 {
		/**
		 * PKCS container key salt size in bytes
		 */
		public static final int SALT_SIZE = 32;
		
		/**
		 * Key for shared preferences holding the salt value.
		 */
		public static final String SALT_SHARED_PREFS_KEY = "PKCSPasswdSaltV2";
		
		/**
		 * Magic string appended to the source key to make hashchain differ
		 * from other hash chains using the same source passphrase.
		 */
		private static final String MAGIC_KEY = "PhoneX-PKCS12";
		
		/**
		 * Scrypt recommended attributes.
		 */
		public static final int SCRYPT_N = 32768; // 2^15
		public static final int SCRYPT_R = 1; // 
		public static final int SCRYPT_P = 1; //
		public static final int SCRYPT_KEYLEN = 32; // 256-bit key derived
		
		/**
		 * Number of iterations of PBKDF2 after scrypt() derivation.
		 */
		public static final int PBKDF2_ITERATIONS = 8192;
		
		/**
		 * PBKDF2 key length derived
		 */
		public static final int PBKDF2_KEYLEN = 32; // 256-bit key derived
        private static final String TAG = "PkcsPasswordSchemeV2";

        /**
		 * Returns preference key for salt for the given user.
		 * @param user
		 * @return
		 */
		public static String getSaltPrefsKey(String user){
			return SharedPrefsSalt.getSaltPrefsKey(SALT_SHARED_PREFS_KEY, user);
		}
		
		/**
		 * Returns true if there is stored a salt value in a shared preferences.
		 * @param ctxt
		 * @return
		 */
		public static boolean saltExists(Context ctxt, String user){
			return SharedPrefsSalt.saltExists(ctxt, SALT_SHARED_PREFS_KEY, user);
		}
		
		/**
		 * Generates new salt to the shared preferences.
		 * @param ctxt
		 * @param user
		 */
		public static void generateNewSalt(Context ctxt, String user){
			generateNewSalt(ctxt, user, new SecureRandom());
		}
		
		/**
		 * Generates new salt to the shared preferences.
		 * @param ctxt
		 * @param rand
		 */
		public static void generateNewSalt(Context ctxt, String user, Random rand){
			SharedPrefsSalt.generateNewSalt(ctxt, SALT_SHARED_PREFS_KEY, user, SALT_SIZE, rand);
		}
		
		/**
		 * Loads salt from preferences
		 * @param ctxt
		 * @return
		 * @throws IOException
		 */
		public static byte[] getSalt(Context ctxt, String user) throws IOException{
			return SharedPrefsSalt.getSalt(ctxt, SALT_SHARED_PREFS_KEY, user);
		}
		
		/**
		 * Generates a new storage password. 
		 * Uses scrypt().
		 * 
		 * Can take very long to evaluate (units of seconds).
		 * 
		 * @param ctxt
		 * @param user
		 * @param key
		 * @return
		 * @throws IOException 
		 * @throws GeneralSecurityException 
		 */
		public static String getStoragePass(Context ctxt, String user, String key) throws IOException, GeneralSecurityException{
			// 1. At first get salt.
			final byte salt[] = getSalt(ctxt, user);
			if (salt==null || salt.length != SALT_SIZE){
				throw new IllegalArgumentException("Salt does not exist or is invalid, generate the new one");
			}
			// 2. Prepare input
			final String pass = user.toLowerCase().trim() + "|" + key + "|" + MAGIC_KEY + "|" + getDeviceId(ctxt);

			// 3. Scrypt
            // old
//            final byte[] scrypted = Scrypt.scrypt(pass.getBytes(), salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_KEYLEN);
            byte[] scrypted = new byte[SCRYPT_KEYLEN];
            NativeCryptoHelper.getInstance().scrypt(pass.getBytes(), pass.getBytes().length, salt, salt.length, SCRYPT_N, SCRYPT_R, SCRYPT_P, scrypted, SCRYPT_KEYLEN);

            // 4. Cosmetic post-processing by few PBKDF2 iterations
            final String scryptedBase64 = Base64.encodeBytes(scrypted);
            // old
//            final byte[] der = CryptoHelper.pbkdf2(scryptedBase64, salt, PBKDF2_ITERATIONS, PBKDF2_KEYLEN*8);
            byte[] der = new byte[PBKDF2_KEYLEN];
            NativeCryptoHelper.getInstance().pbkdf2_hmac_sha1(scryptedBase64, salt, PBKDF2_ITERATIONS, der);

            final String der64 = Base64.encodeBytes(der);

			// 5. Build final block to hash
			final String res64 = Base64.encodeBytes(MessageDigest.hashSha512(scryptedBase64 + "|" + der64));
			return res64;
		}
	}

	/**
	 * Password scheme for PEM files v1
	 * @author ph4r05
	 *
	 */
	public static class PemPasswordSchemeV1 {
		/**
		 * Number of iterations of a hash function
		 */
		public static final int HASH_ITERATIONS = 20;
		
		/**
		 * Key derivation algorithm for PKCS container using user password.
		 * 
		 * @param key
		 * @return
		 * @throws NoSuchAlgorithmException
		 */
		public static String getStoragePass(String key) throws NoSuchAlgorithmException{
			return MessageDigest.generateHashSha256(key, false, HASH_ITERATIONS);
		}
	}
	
	/**
	 * Password scheme for PEM files v2.
	 * 
	 * Deriving keys for PEM files from storage password (derived by PkcsPasswordSchemeV2).
	 * @author ph4r05
	 *
	 */
	public static class PemPasswordSchemeV2 {
		/**
		 * PKCS container key salt size in bytes
		 */
		public static final int SALT_SIZE = 32;
		
		/**
		 * Key for shared preferences holding the salt value.
		 */
		public static final String SALT_SHARED_PREFS_KEY = "PEMPasswdSaltV2";
	
		/**
		 * Number of iterations for generating password for private key file.
		 */
		public static final int PKCS_ITERATIONS = 256;
		
		/**
		 * Number of iterations for generating password for private key file.
		 */
		public static final int ITERATIONS = 1024;
		
		/**
		 * Magic string appended to the source key to make hashchain differ
		 * from other hash chains using the same source passphrase.
		 */
		private static final String MAGIC_KEY = "PhoneX-PEM";

		/**
		 * PBKDF2 key length
		 */
		private static final int PBKDF2_KEYLEN = 32; // 256b
		
		/**
		 * Returns preference key for salt for the given user.
		 * @param user
		 * @return
		 */
		public static String getSaltPrefsKey(String user){
			return SharedPrefsSalt.getSaltPrefsKey(SALT_SHARED_PREFS_KEY, user);
		}
		
		/**
		 * Returns true if there is stored a salt value in a shared preferences.
		 * @param ctxt
		 * @return
		 */
		public static boolean saltExists(Context ctxt, String user){
			return SharedPrefsSalt.saltExists(ctxt, SALT_SHARED_PREFS_KEY, user);
		}
		
		/**
		 * Generates new salt to the shared preferences.
		 * @param ctxt
		 * @param user
		 */
		public static void generateNewSalt(Context ctxt, String user){
			generateNewSalt(ctxt, user, new SecureRandom());
		}
		
		/**
		 * Generates new salt to the shared preferences.
		 * @param ctxt
		 * @param rand
		 */
		public static void generateNewSalt(Context ctxt, String user, Random rand){
			SharedPrefsSalt.generateNewSalt(ctxt, SALT_SHARED_PREFS_KEY, user, SALT_SIZE, rand);
		}
		
		/**
		 * Loads salt from preferences
		 * @param ctxt
		 * @return
		 * @throws IOException
		 */
		public static byte[] getSalt(Context ctxt, String user) throws IOException{
			return SharedPrefsSalt.getSalt(ctxt, SALT_SHARED_PREFS_KEY, user);
		}
		
		/**
		 * Generates a new storage password. 
		 * Uses PBKDF2.
		 * 
		 * Can take very long to evaluate (units of seconds).
		 * 
		 * @param ctxt
		 * @param user
		 * @param key
		 * @return
		 * @throws IOException 
		 * @throws GeneralSecurityException 
		 */
		public static String getStoragePass(Context ctxt, String user, String key) throws IOException, GeneralSecurityException{
			// 1. At first get salt.
			final byte salt[] = getSalt(ctxt, user);
			if (salt==null || salt.length != SALT_SIZE){
				throw new IllegalArgumentException("Salt does not exist or is invalid, generate the new one");
			}
			
			// 2. Prepare input 
			final String pass = user.toLowerCase().trim() + "|" + key + "|" + MAGIC_KEY + "|" + getDeviceId(ctxt);
			
			// 4. Cosmetic post-processing by few PBKDF2 iterations
//            final byte[] der = CryptoHelper.pbkdf2(pass, salt, ITERATIONS, PBKDF2_KEYLEN);
            byte[] der = new byte[PBKDF2_KEYLEN];
            NativeCryptoHelper.getInstance().pbkdf2_hmac_sha1(pass, salt, ITERATIONS, der);

			// 5. Build final block to hash
			final String res64 = Base64.encodeBytes(MessageDigest.hashSha512(der));
			return res64;
		}
		
		/**
		 * Generates a new storage password. 
		 * Uses PBKDF2.
		 * 
		 * Can take very long to evaluate (units of seconds).
		 * 
		 * @param ctxt
		 * @param user
		 * @param key
		 * @param full if true returns complete PEM key (2nd. stage). false returns only 1st stage (one more call is needed to get 2nd)
		 * @return
		 * @throws IOException 
		 * @throws GeneralSecurityException 
		 */
		public static String getStoragePass(Context ctxt, String user, String key, boolean full) throws IOException, GeneralSecurityException{
			if (!full){
				return getStoragePass(ctxt, user, key);
			} else {
				final String pemStoragePass1 = getStoragePass(ctxt, user, key);
    			final String pemStoragePass2 = getStoragePass(ctxt, user, pemStoragePass1);
    			return pemStoragePass2;
			}
		}
	}
	
	/**
	 * Password scheme for XMPP server.
	 * Password = sha_256_hexcoded(ha1b(user, password))
	 * @author ph4r05
	 */
	public static class XmppPasswordSchemeV1 {
		public static String getStoragePass(Context ctxt, String user, String key) throws IOException, GeneralSecurityException{
			// Normalize user string
			final String userSip = SipUri.getCanonicalSipContact(user, false);
			
			// Get domain of the user
			String domain;
			try {
        		ParsedSipContactInfos in = SipUri.parseSipContact(userSip);
        		domain = in.domain;
        	} catch(Exception e){
        		Log.e(THIS_FILE, "Exception: cannot parse domain from SIP name", e);
        		return null;
        	}
			
			final String ha1b = AuthHashGenerator.getHA1(userSip, domain, key);
			
			// Use SHA 256
			final String toReturn = MessageDigest.hashHexSha256(ha1b.getBytes("UTF-8"));
			return toReturn;
		}
	}
	
	/**
	 * Simple helper class for KeyStore containers. Helps mainly with re-keying of the KeyStore.
	 * 
	 * @author ph4r05
	 */
	public static class KeyStoreHelper {
		
		/**
		 * Changes encryption key for the KeyStore.
		 * KeyStore has to exists, be readable and writable.
		 * 
		 * @param ksFile
		 * @param oldPass
		 * @param oldPass
		 * @param ksType
		 * @throws IOException
		 * @throws KeyStoreException 
		 * @throws CertificateException 
		 * @throws NoSuchAlgorithmException 
		 */
		public static void changeKey(File ksFile, String oldPass, String newPass, String ksType) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException{
			if (ksFile==null)
				throw new IllegalArgumentException("Cannot accept null file");
			if (ksFile.exists()==false)
				throw new IOException("File does not exist");
			if (ksFile.canWrite()==false || ksFile.canRead()==false)
				throw new IOException("KeyStore cannot be written or read");
			
			final char[] oldPassCh = oldPass.toCharArray();
			final char[] newPassCh = newPass.toCharArray();
			
			// 1. Load KeyStore from the FS
			FileInputStream fis = new FileInputStream(ksFile);
			KeyStore ks = KeyStore.getInstance(ksType, KeyPairGenerator.getProv());
			ks.load(fis, oldPassCh);
			
			// 2. Store key store with new password
			fis.close();
			FileOutputStream fos = new FileOutputStream(ksFile);
			ks.store(fos, newPassCh);
			fos.close();
			
			// 3. Ensure nobody else can neither read nor write the key store
			ksFile.setReadable(true, true);
			ksFile.setWritable(true, true);
		}
		
		/**
		 * Initializes new KeyStore for PhoneX.
		 *  - Inserts certificate cert using setCertificateEntry() under alias CertificatesAndKeys.DEFAULT_KEY_ALIAS.
		 *  - Inserts key using setKeyEntry under alias CertificatesAndKeys.DEFAULT_KEY_ALIAS together with provided cert chain.
		 *   
		 * @param ksFile
		 * @param pass
		 * @param ksType
		 * @param cert
		 * @param key
		 * @param chain
		 * @throws IOException 
		 * @throws KeyStoreException 
		 * @throws CertificateException 
		 * @throws NoSuchAlgorithmException 
		 */
		public static void initNewKeyStore(File ksFile, String pass, String ksType, Certificate cert, Key key, Certificate chain[]) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException{
			if (ksFile==null)
				throw new IllegalArgumentException("Cannot accept null file");
			if (ksFile.exists() && (!ksFile.canWrite() || !ksFile.canRead()))
				throw new IOException("KeyStore cannot be written or read");
			final char[] passCh = pass.toCharArray();
			
         	KeyStore keyStore = KeyStore.getInstance(ksType);
         	keyStore.load(null, passCh);
         	
         	// Add one certificate-key pair to storage
         	keyStore.setCertificateEntry(CertificatesAndKeys.DEFAULT_KEY_ALIAS, cert);
         	keyStore.setKeyEntry(CertificatesAndKeys.DEFAULT_KEY_ALIAS, key, passCh, chain);
         	
         	FileOutputStream fos = new FileOutputStream(ksFile);
         	keyStore.store(fos, passCh);
         	fos.close();
         	
         	// 3. Ensure nobody else can neither read nor write the key store
 			ksFile.setReadable(true, true);
 			ksFile.setWritable(true, true);
		}
	}
	
	/**
	 * Helper class loads keystore and extracts useful information.
	 * 
	 * @author ph4r05
	 */
	public static class IdentityLoader {
		private UserPrivateCredentials privateCredentials;
		private boolean certificateExistsForUser = false;
		private int code = 0;
		private boolean upgraded=false;
		private Random rand=null;
		
		public static final int CODE_EXISTS = 1;
		public static final int CODE_DOES_NOT_EXIST = 0;
		public static final int CODE_NO_FILE = -1;
		public static final int CODE_BAD_KEY = -2;
		public static final int CODE_EMPTY = -3;
		public static final int CODE_BAD_CN = -4;
		public static final int CODE_INVALID = -5;
		public static final int CODE_NONE = -10;
		
		/**
		 * Check if the certificate exists for current user.
	     * CN has to match username.
	     * 
	     * Loads identity files for the user to attributes.
	     * 
		 * @param ctxt
		 * @param params
		 * @param allowVersionUpgrade
		 * @return
		 */
		public int loadIdentityKeys(Context ctxt, CertGenParams params, boolean allowVersionUpgrade) {
			StoredCredentials creds = new StoredCredentials();
			creds.setUserSip(params.getUserSIP());
			creds.setUsrPass(params.getUserPass());
			creds.setUsrStoragePass(params.getStoragePass());
			creds.setUsrPemPass(params.getPemPass());
			
			return loadIdentityKeys(ctxt, creds, allowVersionUpgrade);
		}
		
		/**
	     * Check if the certificate exists for current user.
	     * CN has to match username.
	     * 
	     * Loads identity files for the user to attributes.
	     * 
	     * Conditions:
	     *  - params has to have filled in storagePass.
	     *  - params has to have filled in pemPass (in case PEM files are needed to be re-generated).
	     *  - params has to have filled in all other parameters: {username, userpass} 
	     * 
	     * @param params 
	     * @return
	     */
		public int loadIdentityKeys(Context ctxt, StoredCredentials params, boolean allowVersionUpgrade) {
			if (params==null){
				throw new IllegalArgumentException("Null parameters are not allowed");
			}
			
			KeyPairGenerator kpg = new KeyPairGenerator();
			privateCredentials = new UserPrivateCredentials();
			certificateExistsForUser = false;
			code = CODE_NONE;
			try {
				do {
					File certPKCS12 = getKeyStoreFile(ctxt, CertificatesAndKeys.derivePkcs12Filename(params.getUserSip()));
					if (certPKCS12 == null || !certPKCS12.exists() || !certPKCS12.canRead()) {
						code = CODE_NO_FILE;
						break;
					}
					
					// Read KeyStore - may fail, if allowed, try to upgrade key store password version
					upgraded = false;
					try {
						// KeyStore reading with given storage password.
						privateCredentials.pkKey = params.getUsrStoragePass().toCharArray();
						privateCredentials.ks = kpg.readKeyStore(derivePkcs12Filename(params.getUserSip()), ctxt, privateCredentials.pkKey);
					} catch(Throwable e){
						Log.w(THIS_FILE, "Exception in opening KeyStore", e);
						
						if (!allowVersionUpgrade){
							code = CODE_BAD_KEY;
							break;
							
						} else {
							Log.v(THIS_FILE, "Trying to upgrade password version for KeyStore.");
							int upgrade = tryVersionUpgradeToV2(ctxt, params);
							if (upgrade <= 0){
								Log.w(THIS_FILE, "Version upgrade failed.");
								code = CODE_BAD_KEY;
								break;
							} else {
								upgraded = true;
								Log.v(THIS_FILE, "Converting KeyStore to password scheme v2 was successful.");
							}
						}
					}
					
					// Obtain certificate chain stored with private key. 
					// Certificate associated with private key is needed for digital signature.
					privateCredentials.certChain = privateCredentials.ks.getCertificateChain(CertificatesAndKeys.DEFAULT_KEY_ALIAS);
					if (privateCredentials.certChain == null || privateCredentials.certChain.length == 0) {
						code = CODE_EMPTY;
						break;
					}
					
					// Checking CN to verify the certificate is for provided user.
					privateCredentials.cert = (X509Certificate) privateCredentials.certChain[0];
					String CNfromCert = getCNfromX500Name(JcaX500NameUtil.getSubject(privateCredentials.cert));
					Log.df(THIS_FILE, "CN from cert: %s", CNfromCert);
					if (CNfromCert == null || !CNfromCert.equalsIgnoreCase(params.getUserSip())) {
						code = CODE_BAD_CN;
						break;
					}

					// Checking certificate validity (time, deprecated CA)
					try {
						checkCredentialsValidity(privateCredentials);
					} catch(Exception e){
						Log.ef(THIS_FILE, e, "Exception in certificate verification");
						code = CODE_INVALID;
						break;
					}

					// Load private key from key store.
					privateCredentials.pk = (PrivateKey) privateCredentials.ks.getKey(CertificatesAndKeys.DEFAULT_KEY_ALIAS, privateCredentials.pkKey);
					
					//
					// Backward-compatibility & consistency preservation: generate PEM alternatives from p12 key store
					// PJSIP library needs PEM files for TLS connection.
					//
					generatePEMFromKS(ctxt, params, upgraded);
					Log.v(THIS_FILE, "TLS set");
					
					certificateExistsForUser = true;
				} while (false);
				if (!certificateExistsForUser) {
					privateCredentials = null;
				}
	
				return certificateExistsForUser ? CODE_EXISTS : code;
			} catch (Exception e) {
				Log.e(THIS_FILE, "Unable to load user identity", e);
				privateCredentials = null;
				return CODE_DOES_NOT_EXIST;
			}
		}

		/**
		 * Checks calidity of the current private credentials.
		 * @throws CertificateException
         */
		public void checkCredentialsValidity() throws CertificateException {
			checkCredentialsValidity(privateCredentials);
		}

		/**
		 * Checks for validity of the stored credentials.
		 * @param creds credentials with certificate
         */
		public void checkCredentialsValidity(UserPrivateCredentials creds) throws CertificateException {
			final X509Certificate cert = creds.getCert();
			// Classic cert validity
			try {
				cert.checkValidity();
			} catch(CertificateNotYetValidException e){
				// OK;
			}

			// Was certificate created before CA change?
			final Date notBefore = cert.getNotBefore();
			if (notBefore.before(OLD_CA_DEPRECATED_DATE)){
				throw new CertificateException("Certificate of the old CA, notBefore: " + notBefore);
			}

			// TODO: add to certificate validator, validate with our trust anchors. w.r.t. time of roots.
		}
		
		/**
		 * Generates PEM files from opened KeyStore
		 * @param ctxt
		 * @param params
		 * @return
		 * @throws IOException 
		 * @throws NoSuchAlgorithmException 
		 * @throws NoSuchProviderException 
		 * @throws CertificateEncodingException 
		 */
		public int generatePEMFromKS(Context ctxt, StoredCredentials params, boolean force) throws CertificateEncodingException, NoSuchProviderException, NoSuchAlgorithmException, IOException{
			if (this.privateCredentials==null || this.privateCredentials.ks==null)
				throw new IllegalStateException("Has to load key store first");
			
			File caFile      = getKeyStoreFile(ctxt, CertificatesAndKeys.CA_LIST_FILE);
			File certFile    = getKeyStoreFile(ctxt, CertificatesAndKeys.deriveUserCertFilename(params.getUserSip()));
			File privkeyFile = getKeyStoreFile(ctxt, CertificatesAndKeys.derivePrivKeyFilename(params.getUserSip()));
			
			// generate PEM files if does not exist (backward compatibility, for updated versions)
			if (!caFile.exists() || !certFile.exists() || !privkeyFile.exists() || force){
				Log.d(THIS_FILE, "One of PEM files does not exist. Need to regenerate from P12 file");
				
				KeyPairGenerator.createPemFiles(ctxt, privateCredentials.cert, privateCredentials.pk, params.getUsrPemPass().toCharArray(), params.getUserSip());
				Log.v(THIS_FILE, "PEM files generated");
			}
			
			// TLS settings, refresh SharedPreferences values for TLS.
			PreferencesConnector prefs = new PreferencesConnector(ctxt);
			prefs.setBoolean(PhonexConfig.ENABLE_TLS, PhonexSettings.useTLS());
			prefs.setString(PhonexConfig.CA_LIST_FILE, caFile.getAbsolutePath());
			prefs.setString(PhonexConfig.CERT_FILE, certFile.getAbsolutePath());
			prefs.setString(PhonexConfig.PRIVKEY_FILE, privkeyFile.getAbsolutePath());
			prefs.setString(PhonexConfig.TLS_USER, params.getUserSip());
			
			return 1;
		}
		
		/**
		 * Stores loaded keystore in private credentials structure to a file 
		 * with storage password in params. Updates the key store.
		 * 
		 * @param ctxt
		 * @param params
		 * @return
		 * @throws IOException 
		 * @throws CertificateException 
		 * @throws NoSuchAlgorithmException 
		 * @throws KeyStoreException 
		 */
		public int storeKSToFile(Context ctxt, StoredCredentials params) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
//			return storeKSToFile(ctxt, getKeyStoreFile(ctxt, CertificatesAndKeys.PKCS12Container), params);
			return storeKSToFile(ctxt, getKeyStoreFile(ctxt, CertificatesAndKeys.derivePkcs12Filename(params.getUserSip())), params);
		}
		
		/**
		 * Stores loaded keystore in private credentials structure to a file 
		 * with storage password in params.
		 * 
		 * @param ctxt
		 * @param ksFile
		 * @return
		 * @throws IOException 
		 * @throws CertificateException 
		 * @throws NoSuchAlgorithmException 
		 * @throws KeyStoreException 
		 */
		public int storeKSToFile(Context ctxt, File ksFile, StoredCredentials params) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
			if (this.privateCredentials==null || this.privateCredentials.ks==null)
				throw new IllegalStateException("Has to load key store first");
			if (params.getUsrStoragePass()==null)
				throw new IllegalArgumentException("Storage password cannot be null");
			
			FileOutputStream fos = new FileOutputStream(ksFile);
			privateCredentials.ks.store(fos, params.getUsrStoragePass().toCharArray());
			fos.close();
			
			// 3. Ensure nobody else can neither read nor write the key store
			ksFile.setReadable(true, true);
			ksFile.setWritable(true, true);
			
			return 1;
		}
		
		/**
		 * If load fails, this may be called to try to update from previous password version.
		 * 
		 * @return
		 */
		public int tryVersionUpgradeToV2(Context ctxt, StoredCredentials params){
			// 1. Derive old password, try to open with it
			final String prevStoragePass = PkcsPasswordSchemeV1.getStoragePass(ctxt, params.getUserSip(), params.getUsrPass());
			final char[] prevPKKey = prevStoragePass.toCharArray();
			KeyPairGenerator kpg = new KeyPairGenerator();
			KeyStore ks = null;
			
			// 2. Read KeyStore with password generated by previous password scheme (v1)
			try {
				ks = kpg.readKeyStore(CertificatesAndKeys.derivePkcs12Filename(params.getUserSip()), ctxt, prevPKKey);
				
				// 3. If here -> password is OK. Thus no salt is stored in the database.
				try {
	    			Log.v(THIS_FILE, "Going to generate storagePassword");
	    			PkcsPasswordSchemeV2.generateNewSalt(ctxt, params.getUserSip(), rand == null ? new SecureRandom() : rand);
	    			final String storagePass = PkcsPasswordSchemeV2.getStoragePass(ctxt, params.getUserSip(), params.getUsrPass());
	    			
	    			PemPasswordSchemeV2.generateNewSalt(ctxt, params.getUserSip(), rand == null ? new SecureRandom() : rand);
	    			final String pemStoragePass1 = PemPasswordSchemeV2.getStoragePass(ctxt, params.getUserSip(), storagePass);
	    			final String pemStoragePass2 = PemPasswordSchemeV2.getStoragePass(ctxt, params.getUserSip(), pemStoragePass1);
					
	    			params.setUsrStoragePass(storagePass);
	    			params.setUsrPemPass(pemStoragePass2);
				} catch(Exception e){
					Log.e(THIS_FILE, "Cannot generate strong encryption keys.");
					throw new KeyDerivationException(e);
				}
				
				// 4. save to v2 format.
				KeyStoreHelper.changeKey(getKeyStoreFile(ctxt, CertificatesAndKeys.derivePkcs12Filename(params.getUserSip())), prevStoragePass, params.getUsrStoragePass(), KEYSTORE_PKCS12);
				
				// 5. Load to private credentials
				privateCredentials.ks = ks;
				return 1;
			} catch(Exception e){
				Log.w(THIS_FILE, "Exception in opening KeyStore with password scheme v1", e);
				return -1;
			}
		}
		
		/**
		 * Loads StoredCredentials and user private credentials if possible.
		 * @param par
		 * @param rand
		 * @param ctx
		 * @return -1 if StoredCredentials are not valid, -2 if private credentials are not valid. 1 otherwise.
		 */
		public static int loadIdentity(IUserIdentityHolder par, SecureRandom rand, Context ctx){
			// 1. load shared info from in-memory database
			StoredCredentials creds = MemoryPrefManager.loadCredentials(ctx);
			if (creds.getUserSip()==null){
				return -1;
			}
			
			par.setStoredCredentials(creds);		
			
			// 2. load identity
			IdentityLoader il = new IdentityLoader();
			il.setRand(rand);
			
			int code = il.loadIdentityKeys(ctx, creds, false);
			if (code!=IdentityLoader.CODE_EXISTS){
				return -2;
			}
			
			// 3. transfer necessary data to parameters
			UserPrivateCredentials ucreds = il.getPrivateCredentials();
			par.setUserPrivateCredentials(ucreds);
			
			return 1;
		}
		
		public UserPrivateCredentials getPrivateCredentials() {
			return privateCredentials;
		}
		
		public boolean isCertificateExistsForUser() {
			return certificateExistsForUser;
		}
		
		public int getCode() {
			return code;
		}

		public boolean isUpgraded() {
			return upgraded;
		}

		public Random getRand() {
			return rand;
		}

		public void setRand(Random rand) {
			this.rand = rand;
		}
	}
	
	/**
	 * Holder for StoredCredentials & UserPrivateCredentials.
	 * 
	 * @author ph4r05
	 */
	public static class UserIdentity implements IUserIdentityHolder {
		private StoredCredentials creds;
		private UserPrivateCredentials ucreds;
		
		public StoredCredentials getStoredCredentials(){
			return creds;
		}
		
		public void setStoredCredentials(StoredCredentials creds) {
			this.creds = creds;
		}
		
		public UserPrivateCredentials getUserPrivateCredentials() {
			return ucreds;
		}
		
		public void setUserPrivateCredentials(UserPrivateCredentials ucreds) {
			this.ucreds = ucreds;
		}
	}

    /**
     * Check against user password stored for current user. Useful for changing sensitive settings like PIN lock code
     * @param context
     * @param pass
     * @return
     */
    public static boolean verifyUserPass(Context context, String pass){
        StoredCredentials creds = MemoryPrefManager.loadCredentials(context);
        if (creds != null && pass.equals(creds.getUsrPass())){
            creds = null;
            return true;
        } else {
            creds = null;
            return false;
        }
    }

	/**
	 * Returns File object initialized to directory for storing KeyStores
	 * @param ctxt
	 * @return
	 */
	public static File getKeyStoreDir(Context ctxt){
		return ctxt.getFilesDir();
	}
	
	/**
	 * Returns File object pointing to given key store file in key store directory
	 * @param ctxt
	 * @param keyStoreFileName
	 * @return
	 */
	public static File getKeyStoreFile(Context ctxt, String keyStoreFileName){
		if (keyStoreFileName==null)
			throw new IllegalArgumentException("Cannot accept null file names");
		if (keyStoreFileName.contains(File.separator))
			throw new SecurityException("Filename cannot contain file separator");
		return new File(ctxt.getFilesDir().getAbsolutePath() + File.separator + keyStoreFileName);
	}

	/**
     * Loads certificate for given SIP from peer certificate database
     * @param ctxt
     * @param sip
     * @return
     */
    public static UserCertificate getRemoteCertificate(Context ctxt, String sip){
    	 try {
    		String selection = UserCertificate.FIELD_OWNER + "=?";
            String[] selectionArgs = new String[] { sip };
    		Cursor c = ctxt.getContentResolver().query(UserCertificate.CERTIFICATE_URI, UserCertificate.FULL_PROJECTION, selection, selectionArgs, null);
    		if (c!=null && c.getCount() > 0){
    			while(c.moveToNext()) {
    				UserCertificate sipCert = new UserCertificate(c);
    				
    				try {
        				c.close();
        			} catch(Exception e) { }
    				
    				return sipCert;
    			}
    		} else if (c!=null){
    			try {
    				c.close();
    			} catch(Exception e) { }
    		}
    		return null;
         } catch(Exception e){
         	Log.ef(THIS_FILE, e, "Exception during loading stored certificate for: %s", sip);
         	return null;
         }
    }
    
    /**
     * Loads certificate for given SIP users from peer certificate database
     * @param ctxt
     * @param sip
     * @return
     */
    public static List<UserCertificate> getRemoteCertificates(Context ctxt, List<String> sip){
    	 List<UserCertificate> ret = new LinkedList<>();
    	 if (sip==null || sip.isEmpty()) return ret;
    	 
    	 try {
    		String selection = UserCertificate.FIELD_OWNER + " IN (" + DBProvider.getInPlaceholders(sip.size()) + ")";
            String[] selectionArgs = sip.toArray(new String[sip.size()]);
            
    		Cursor c = ctxt.getContentResolver().query(UserCertificate.CERTIFICATE_URI, UserCertificate.FULL_PROJECTION, selection, selectionArgs, null);
    		if (c!=null){
    			while(c.moveToNext()) {
    				UserCertificate sipCert = new UserCertificate(c);
    				ret.add(sipCert);
    			}

				MiscUtils.closeCursorSilently(c);
    		} else {
    			return ret;
    		}
         } catch(Exception e){
         	Log.ef(THIS_FILE, e, "Exception during loading stored certificate for: %s", sip);
         	return ret;
         }
    	 
    	 return ret;
    }
    
    /**
     * Removes certificate for given user from certificate database.
     * 
     * @param ctxt
     * @param sip
     * @return
     */
    public static int removeRemoteCertificate(Context ctxt, String sip){
    	int ret = 0;
    	
    	try {
    		ret = ctxt.getContentResolver().delete(
    				UserCertificate.CERTIFICATE_URI,
    				UserCertificate.FIELD_OWNER + "=?",
    				new String[] { sip });
    		
    		return ret;
         } catch(Exception e){
         	Log.ef(THIS_FILE, e, "Exception during loading stored certificate for: %s", sip);
         	return ret;
         }
    }
    
    /**
     * Rebuilds X509Certificate from its byte representation.
     * 
     * @param cert
     * @return
     * @throws IOException
     * @throws CertificateException
     */
    public static X509Certificate buildCertificate(byte[] cert) throws IOException, CertificateException{
    	X509CertificateHolder certHolder = new X509CertificateHolder(cert);
		X509Certificate realCert = new JcaX509CertificateConverter().setProvider(KeyPairGenerator.getProv()).getCertificate(certHolder);
		return realCert;
    }
    
    /**
     * Extracts CN from X500Name
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
     * Extracts CN from X509Certificate as a string.
     * @param cert
     * @return
     * @throws CertificateException
     */
    public static String getCertificateCN(X509Certificate cert) throws CertificateException{
    	return getCNfromX500Name(JcaX500NameUtil.getSubject(cert));
    }
    
    /**
     * Checks certificate CN.
     * 
     * @param cert
     * @param cn
     * @return
     * @throws CertificateException 
     */
    public static boolean checkCertCN(X509Certificate cert, String cn, boolean throwException) throws CertificateException{
    	try {
	     	String CNfromCert = getCertificateCN(cert);
	     	if (!cn.equals(CNfromCert)){
	     		return false;
	     	}
		} catch(CertificateException e){
			if (throwException) throw e;
			else return false;
		}
    	
    	return true;
    }
    
    /**
     * Verifies provided certificate with trust verifier.
     * If cn is provided, it is verified cn.equals(cert.cn).
     * If when is provided, certificate validity is checked at given point in time.
     * 
     * @param cert
     * @param throwException 	If true, exception is thrown on error otherwise exceptions are suppressed.
     * @param tv				Custom trust verifier to verify certificate velidity (time & cert path).
     * @param when				OPTIONAL Point in time to verify certificate validity in.
     * @param cn				OPTIONAL CN to verify with certificate CN.
     * @return					true if certificate is OK w.r.t. specified conditions, false otherwise.
     * @throws CertificateException
     */
    public static boolean verifyClientCertificate(X509Certificate cert, boolean throwException, TrustVerifier tv, Date when, String cn) throws CertificateException{
    	// Check CN match if cn provided
    	if (cn != null){
    		boolean cncheck = checkCertCN(cert, cn, throwException);
    		if (cncheck==false) return false;
    	}
		
		// Verify new certificate with provided trust verifier.
		try {
			if (when!=null){
				tv.checkTrusted(cert, when);
			} else {
				tv.checkTrusted(cert);
			}
			
		} catch(CertificateException e){
			if (throwException) throw e;
			else return false;
		}
		
		return true;
    }
    
    /**
	 * Returns unique string representing device application is running on.
	 * Used as salt when using PBKDF2.
	 * 
	 * @param ctxt
	 * @return
	 */
	public static String getDeviceId(Context ctxt){
    	// get IMEI
    	String IMEI = "";
    	try {
    		TelephonyManager mTelephonyMgr = (TelephonyManager)ctxt.getSystemService(Context.TELEPHONY_SERVICE);
    		IMEI = mTelephonyMgr.getDeviceId();
    	} catch(Exception ex){
    		Log.e(THIS_FILE, "Cannot get IMEI", ex);
    	}
    	
    	// android ID
    	String androidId="";
    	try {
    		 androidId = Secure.getString(ctxt.getContentResolver(), Secure.ANDROID_ID); 
    	} catch(Exception ex){
    		Log.e(THIS_FILE, "Cannot get android ID", ex);
    	}
	    	
	    return (IMEI+"|"+androidId);
	}
	
	/**
	 * Tells whether to re-check certificate with respect to the last certificate check.
	 * Implements basic DoS avoidance for certificate check (not to update certificates
	 * too often).
	 *   
	 * @param sipRemoteCert
	 * @return
	 */
	public static boolean recheckCertificateForUser(UserCertificate sipRemoteCert){
		boolean recheckNeeded = false;
		final Date lastQuery = sipRemoteCert.getDateLastQuery();
		
    	// is certificate stored in database OK?
    	if (sipRemoteCert.getCertificateStatus() == UserCertificate.CERTIFICATE_STATUS_OK){
    		// certificate is valid, maybe we still need some re-check (revocation status for example)
    		Date boudnary = new Date(System.currentTimeMillis() - CertificatesAndKeys.CERTIFICATE_OK_RECHECK_PERIOD);
    		recheckNeeded = lastQuery.before(boudnary);
    	} else {
    		// Certificate is invalid, missing or revoked or broken somehow.
    		// should re-check be performed?
    		Date boudnary = new Date(System.currentTimeMillis() - CertificatesAndKeys.CERTIFICATE_NOK_RECHECK_PERIOD);
    		recheckNeeded = lastQuery.before(boudnary);
    	}
    	
    	return recheckNeeded;
	}
}
