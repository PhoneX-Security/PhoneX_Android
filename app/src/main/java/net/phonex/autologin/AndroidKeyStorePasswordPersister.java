package net.phonex.autologin;

import android.content.Context;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import net.phonex.autologin.exceptions.LoginStorageException;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.SecretKeyStorageException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;
import net.phonex.pub.a.Compatibility;
import net.phonex.pub.proto.AutoLogin;
import net.phonex.util.Log;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Saves password encrypted with random secret key.
 * Key is saved to SecretKey storage (typically secure remote server).
 * Secret key, initialization vector for symmetric encryption and authentication nonce
 * are encrypted using asymmetric crypto, keys are stored using AndroidKeyStore provider.
 * Requires API 18+
 */
public class AndroidKeyStorePasswordPersister implements PasswordPersister {
    private static final String TAG = "PasswordPersister";

    // the corresponding crypto provider is "AndroidOpenSSL";
    private static final String SECURE_RANDOM_PROVIDER = "SHA1PRNG";

    // because of ALG_RSA_PKCS1 padding, see Java Crypto API documentation for explanation
    public static final int RSA_PADDING_OVERHEAD = 11;

    // overhead caused by protocol buffer serialization, estimate
    public static final int PROTOBUF_OVERHEAD = 16;    //

    private static final String AES = "AES";
    private static final int AES_KEY_SIZE_BIT = 128;
    private static final int AES_BLOCK_SIZE = AES_KEY_SIZE_BIT / 8; // bytes
    private static final int IV_LENGTH = AES_BLOCK_SIZE; // bytes

    public static final int NONCE_LENGTH_BYTE = 64;

    /**
     * Alias for keypair stored in KeyStore
     */
    private static final String KEY_PAIR_ALIAS = "RememberedPasswordKey";


    private Context context;
    private KeyPairStorage keyPairStorage;

    /**
     * @param context May be used by Android to pop up some UI
     *                to ask the user to unlock or initialize the Android KeyStore facility.
     */
    public AndroidKeyStorePasswordPersister(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }

        // API smaller than 18 doesn't support KeyStore
        if (Compatibility.isApiGreaterOrEquals(18)){
            keyPairStorage = new KeyPairStorage18(KEY_PAIR_ALIAS);
        } else {
            keyPairStorage = new KeyPairStorage14(context);
        }

        this.context = context;
    }

    /**
     * Verify if AndroidKeyStore provider is available on this device.
     * Should be called before saving or loading login.
     *
     * @return true if AndroidKeyStore provider is available
     */
    public boolean isKeyStoreAvailable() {
        return true;
//        if (keyStoreAvailable) {
//            // using previous result, it is unlikely that AndroidKeyStore becomes unavailable
//            return true;
//        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
//            try {
//                KeyStore keyStore = loadAndroidKeyStore();
//                keyStoreAvailable = true;
//            } catch (PasswordPersisterException e) {
//                Log.i(TAG, "isKeyStoreAvailable() Key store is not available");
//                return false;
//            }
//            return true;
//        }
//        return false;
    }

    /**
     * Returns maximal size of data in bytes, that can be saved using this method.
     * This should be called before trying to save long passwords.
     *
     * @return maximal size of data in bytes, that can be saved using this method.
     */
    public int getMaxPayloadLength() {
        // can only encrypt one block of RSA, there has to be space for IV and nonce (in clear)
        // and AES encryption can add one more block
        // Because of padding, the block size is decreased by RSA_PADDING_OVERHEAD as well
        return KeyPairStorage.RSA_KEY_SIZE / 8 - AES_BLOCK_SIZE - IV_LENGTH - NONCE_LENGTH_BYTE - RSA_PADDING_OVERHEAD - PROTOBUF_OVERHEAD;
    }

    /**
     * Securely stores given password using the AndroidKeyStore and server.
     * Before calling this method, verify that the key store is available by isKeyStoreAvailable().
     *
     * @param loginStorage the storage to which encrypted serialized login information will be written
     * @param password     the password or derived key to be saved
     * @param end          end of validity of keypair that protects this password, or DEFAULT_VALID_HOURS
     * @throws PasswordPersisterException  for errors with crypto and wrong order of operations
     * @throws LoginStorageException       for errors when writing to login storage
     * @throws ServiceUnavailableException server is not reachable, try later
     * @throws SecretKeyStorageException   when unable to store keys
     */
    public void storePassword(LoginStorage loginStorage,
                              SecretKeyStorage secretKeyStorage, byte[] password, byte[] userName, Date end)
            throws PasswordPersisterException, ServiceUnavailableException,
            SecretKeyStorageException, LoginStorageException {
        Log.vf(TAG, "storePassword");

        if (!isKeyStoreAvailable()) {
            throw new PasswordPersisterException(
                    "AndroidKeyStore is not available, verify it programmatically by isKeyStoreAvailable()");
        }

        if (loginStorage == null) {
            throw new IllegalArgumentException("loginStorage == null");
        }
        if (password == null) {
            throw new IllegalArgumentException("password == null");
        }
        if (secretKeyStorage == null) {
            throw new IllegalArgumentException("secretKeyStorage == null");
        }

        if (getMaxPayloadLength() < password.length + userName.length) {
            throw new PasswordPersisterException(
                    "Password is too long, verify it programmatically by getMaxPayloadLength()");
        }

        //KeyPair keyPair = null;
        PublicKey publicKey = keyPairStorage.retrievePublicKey();

        if (publicKey == null) {
            Log.df(TAG, "storePassword; null publicKey therefore generating new one");
            try {
                KeyPair keyPair = keyPairStorage.generateKeyPair(context, end);
                publicKey = keyPair.getPublic();
                Log.df(TAG, "storePassword;  public key generation successful.");
            } catch (GeneralSecurityException e) {
                Log.d(TAG, "KeyPairGenerator exception" + e);
                throw new PasswordPersisterException("KeyPairGenerator exception", e);
            }
        }

        /*
         * Initialize the secure random, it is seeded automatically.
         */
        SecureRandom secureRandom;
        try {
            secureRandom = SecureRandom.getInstance(SECURE_RANDOM_PROVIDER);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Secure random provider not available" + e);
            throw new PasswordPersisterException("Secure random provider not available", e);
        }

        /*
         * Generate initialization vector.
         */
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        /*
         * Generate a new secret key and initialize cipher.
         * Encrypt Password with secret key and IV iv.
         */
        SecretKey secretKey;
        final byte[] passwordEncrypted;
        try {
            secretKey = AESHelper.generateSecretKey(secureRandom);
            passwordEncrypted = AESHelper.encrypt(secretKey, password, iv);
        } catch (GeneralSecurityException e) {
            Log.d(TAG, "Symmetric encryption exception" + e);
            throw new PasswordPersisterException("Symmetric encryption exception", e);
        }

        /*
         * Generate random nonce.
         */
        byte[] nonce = new byte[NONCE_LENGTH_BYTE];
        secureRandom.nextBytes(nonce);

        final byte[] encryptedLogin;
        {
            AutoLogin.AutoLoginData data = AutoLogin.AutoLoginData.newBuilder()
                    .setPasswordEncrypted(ByteString.copyFrom(passwordEncrypted))
                    .setIv(ByteString.copyFrom(iv))
                    .setNonce(ByteString.copyFrom(nonce))
                    .setUserName(ByteString.copyFrom(userName))
                    .build();
            byte[] serializedLogin = data.toByteArray();

            if (serializedLogin.length > RSAHelper.getClearBlockSize(KeyPairStorage.RSA_KEY_SIZE)) {
                throw new PasswordPersisterException("Too much data for RSA");
            }

            /*
             * Encrypt serialized login (encrypted password, iv, and nonce) with public key.
             */
            try {
                encryptedLogin = RSAHelper.encrypt(publicKey, serializedLogin);
            } catch (GeneralSecurityException e) {
                Log.d(TAG, "Exception during public key encryption" + e);
                throw new PasswordPersisterException("Exception during public key encryption", e);
            }
        }
        /*
         * Send secret key and nonce to server.
         * Key and nonce must be uploaded now. We cannot do it later, that would mean storing
         * them insecurely.
         */
        byte[] encodedSecretKey = secretKey.getEncoded();
        if (encodedSecretKey == null) {
            throw new PasswordPersisterException("SecretKey does not support encoding");
        }
        secretKeyStorage.uploadEncodedKeyAndNonce(encodedSecretKey, nonce);

        /*
         * Save encrypted serialized login.
         * IOException is propagated to caller.
         */
        loginStorage.storeLogin(encryptedLogin);
    }

    /**
     * Securely loads given password stored using the AndroidKeyStore and server.
     * Before calling this method, verify that the key store is available by isKeyStoreAvailable().
     *
     * @param loginStorage the storage from which encrypted serialized login information will be read
     * @return the recovered password
     * @throws PasswordPersisterException  for errors with crypto and wrong order of operations
     * @throws LoginStorageException       for errors when reading from login storage
     * @throws ServiceUnavailableException server is not reachable, try
     * @throws SecretKeyStorageException   when unable to load keys
     */
    public LoginCredentials loadPassword(LoginStorage loginStorage, SecretKeyStorage secretKeyStorage)
            throws PasswordPersisterException, ServiceUnavailableException,
            SecretKeyStorageException, LoginStorageException {
        if (!isKeyStoreAvailable()) {
            throw new PasswordPersisterException(
                    "AndroidKeyStoreAndroidKeyStore is not available, verify it programmatically by isKeyStoreAvailable()");
        }

        if (loginStorage == null) {
            throw new IllegalArgumentException("inputStream == null");
        }
        if (secretKeyStorage == null) {
            throw new IllegalArgumentException("secretKeyStorage == null");
        }

        PrivateKey privateKey = keyPairStorage.retrieveKey();

        // there must be at least one block from AES and space for iv and nonce
        final int minRSAClearLength = AES_BLOCK_SIZE + IV_LENGTH + NONCE_LENGTH_BYTE;

        byte[] encryptedLogin = loginStorage.loadLogin(KeyPairStorage.RSA_KEY_SIZE);

        final byte[] serializedLogin;
        try {
            serializedLogin = RSAHelper.decrypt(privateKey, encryptedLogin);
        } catch (GeneralSecurityException e) {
            throw new PasswordPersisterException("Exception during public decryption", e);
        }

        if (serializedLogin == null) {
            Log.d(TAG, "nul value after RSA decryption");
            throw new PasswordPersisterException("null value after RSA decryption");
        } else if (serializedLogin.length < minRSAClearLength) {
            Log.d(TAG, "Incorrect size of RSA decrypted persisted data: " + serializedLogin.length
                    + " expected at least " + minRSAClearLength);
            throw new PasswordPersisterException("Incorrect size of persisted data after RSA decryption");
        }

        Log.d(TAG, "Size of login data after decryption " + serializedLogin.length);

        //LoginData data = deserializeLogin(serializedLogin, IV_LENGTH, NONCE_LENGTH_BYTE);
        AutoLogin.AutoLoginData data = null;
        try {
            data = AutoLogin.AutoLoginData.parseFrom(serializedLogin);
        } catch (InvalidProtocolBufferException e) {
            Log.d(TAG, "Failed to deserialize stored password", e);
            throw new PasswordPersisterException("Failed to deserialize stored password", e);
        }

        byte[] encodedSecretKey = secretKeyStorage.downloadEncodedKey(data.getNonce().toByteArray(), data.getUserName().toByteArray());

        if (encodedSecretKey == null) {
            throw new PasswordPersisterException("downloadEncodedKey() returned null key");
        }

        SecretKey secretKey = new SecretKeySpec(encodedSecretKey, 0, encodedSecretKey.length, AES);

        try {
            // TODO
            String domain = "phone-x.net";
            return new LoginCredentials(new String(AESHelper.decrypt(secretKey, data.getPasswordEncrypted().toByteArray(), data.getIv().toByteArray())), new String(data.getUserName().toByteArray()), domain);
        } catch (GeneralSecurityException e) {
            Log.d(TAG, "Failed to decrypt stored password, key store probably contains different certificate", e);
            throw new PasswordPersisterException("Failed to decrypt stored password", e);
        }
    }

    @Override
    public boolean isKeyPairAvailable() {
        return keyPairStorage.isKeyPairAvailable();
    }
}
