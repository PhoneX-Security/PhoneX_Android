package net.phonex.autologin;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;

import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.util.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Calendar;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

/**
 * KeyPair storage for API 18+, backed up by Android's KeyStore
 * Created by miroc on 2.2.16.
 */
public class KeyPairStorage18 implements KeyPairStorage {
    private static final String TAG = "KeyPairStorage18";

    private static final String X500_PRINCIPAL_DISTINGUISHED_NAME = "CN=RememberedPasswordKey";
    private static final int SERIAL_NUMBER = 1;
    private static final int DEFAULT_VALID_HOURS = 24;
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private final String keyPairAlias;

    public KeyPairStorage18(String keyPairAlias) {
        this.keyPairAlias = keyPairAlias;
    }

    @Override
    public PrivateKey retrieveKey() throws PasswordPersisterException {
        Log.df(TAG, "retrieveKey");
        KeyStore keyStore = loadAndroidKeyStore();
        PrivateKey privateKey;
        try {
            if (!keyStore.containsAlias(keyPairAlias)) {
                Log.d(TAG, "The key pair is not saved");
                throw new PasswordPersister.MissingKeyPairException("The key pair is not saved");
            }

            Certificate certificate = keyStore.getCertificate(keyPairAlias);

            if (certificate != null && certificate.getType().equals("X.509")) {
                try {
                    ((X509Certificate) certificate).checkValidity();
                } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                    keyStore.deleteEntry(keyPairAlias);
                    throw new PasswordPersisterException("Certificate not valid");
                }
            } else {
                throw new PasswordPersisterException("Certificate not available or incorrect certificate typeee");
            }

            Key key = keyStore.getKey(keyPairAlias, null);
            if (!(key instanceof PrivateKey)) {
                throw new PasswordPersister.InvalidKeyPairException(
                        String.format("The saved key is not of expected type (expected=%s, retrieved=%s)",
                        PrivateKey.class.getSimpleName(), key.getClass().getSimpleName()));
            } else {
                privateKey = (PrivateKey) key;
            }
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
            throw new PasswordPersisterException(e);
        }
        return privateKey;
    }

    /**
     * Generate a new entry in the KeyStore by using the
     * KeyPairGenerator API. We have to specify the attributes for a
     * self-signed X.509 certificate here so the KeyStore can attach
     * the public key part to it. It can be replaced later with a
     * certificate signed by a Certificate Authority (CA) if needed.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public KeyPair generateKeyPair(Context context, Date end) throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator;
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();

        if (end == null) {
            calendar.add(Calendar.HOUR, DEFAULT_VALID_HOURS);
            end = calendar.getTime();
        } else if (end.before(now)) {
            throw new IllegalArgumentException("end < now");
        }

        KeyPairGeneratorSpec.Builder builder = new KeyPairGeneratorSpec.Builder(context);
        builder.setAlias(keyPairAlias)
                .setStartDate(now)
                .setEndDate(end)
                .setSerialNumber(BigInteger.valueOf(SERIAL_NUMBER))
                .setSubject(new X500Principal(X500_PRINCIPAL_DISTINGUISHED_NAME));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            builder.setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSA_PUBLIC_EXPONENT));
        }

        keyPairGenerator = KeyPairGenerator.getInstance(RSA, ANDROID_KEY_STORE);
        keyPairGenerator.initialize(builder.build());
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Method for storing certificate depends on current API version
     * @return public key
     * @throws PasswordPersisterException
     */
    public PublicKey retrievePublicKey() throws PasswordPersisterException {
        KeyStore keyStore = loadAndroidKeyStore();
        try {
            if (keyStore.containsAlias(keyPairAlias)) {
                Certificate certificate = keyStore.getCertificate(keyPairAlias);
                if ("X.509".equals(certificate.getType())) {
                    try {
                        // if the certificate is valid, use the public key
                        ((X509Certificate) certificate).checkValidity();
                        Log.df(TAG, "Existing certificate is valid until %s", ((X509Certificate) certificate).getNotAfter());
                        return certificate.getPublicKey();
                    } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                        // make a new certificate later
                        Log.df(TAG, e, "Certificate validity problem");
                        keyStore.deleteEntry(keyPairAlias);
                        Log.d(TAG, "Deleted stored key pair");
                    }
                } else {
                    Log.df(TAG, "Certificate is not X.509, but [%s]", certificate.getType());
                    keyStore.deleteEntry(keyPairAlias);
                    Log.d(TAG, "Deleted stored key pair");
                }
            }
        } catch (KeyStoreException e) {
            Log.d(TAG, "KeyStoreException" + e);
            throw new PasswordPersisterException("KeyStoreException", e);
        }
        return null;
    }

    /**
     * Check if required key pair is stored in AndroidKeyStore
     *
     * @return true if key is available and valid
     */
    @Override
    public boolean isKeyPairAvailable() {
        try {
            Key key = retrieveKey();
            return true;
        } catch (PasswordPersisterException e) {
            Log.wf(TAG, e, "isKeyPairAvailable; exception thrown");
            return false;
        }
    }

    /*
     * Load the Android KeyStore instance using the "AndroidKeyStore" provider
     * to check if entry for this purpose is currently stored.
     */
    private KeyStore loadAndroidKeyStore() throws PasswordPersisterException {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            Log.w(TAG, "Failed to initialize AndroidKeyStore in spite of isKeyStoreAvailable() returning true " + e);
            throw new PasswordPersisterException("Exception during initialization of AndroidKeyStore", e);
        }
        return keyStore;
    }
}
