package net.phonex.autologin;

import android.content.Context;

import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CertificatesAndKeys;

import org.spongycastle.x509.X509V3CertificateGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
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
 * Fallback solution for KeyPair storage for API 17-
 * It stores keypair in local private storage. Used only for Android version 4.0 - 4.2
 * Created by miroc on 2.2.16.
 */
public class KeyPairStorage14 implements KeyPairStorage {
    private static final String TAG = "KeyPairStorage14";

    private static final String X500_PRINCIPAL_DISTINGUISHED_NAME = "CN=RememberedPasswordKey";
    private static final String X500_ISSUER_PRINCIPAL_DISTINGUISHED_NAME = "CN=PhoneX";
    private static final int SERIAL_NUMBER = 1;
    private static final int DEFAULT_VALID_HOURS = 24;
    private static final String OBF_SALT = "ootheeyaiDee4ich1hie";

    private static final String FILE_NAME = "rlg.pfx";

    private static final String BC = org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    private Context context;


    public KeyPairStorage14(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        this.context = context;
    }

    @Override
    public PrivateKey retrieveKey() throws PasswordPersisterException {
        FileInputStream inputStream = null;
        try {
            File keyStoreFile = CertificatesAndKeys.getKeyStoreFile(context, FILE_NAME);
            inputStream = new FileInputStream(keyStoreFile);
            net.phonex.util.crypto.KeyPairGenerator kpg = new net.phonex.util.crypto.KeyPairGenerator();
            KeyStore keyStore = kpg.readKeyStore(inputStream, OBF_SALT.toCharArray());
            return (PrivateKey) keyStore.getKey(CertificatesAndKeys.DEFAULT_KEY_ALIAS, OBF_SALT.toCharArray());
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException | IOException | CertificateException e) {
            throw new PasswordPersisterException(e);
        } finally {
            if (inputStream != null){
                MiscUtils.closeSilently(inputStream);
            }
        }
    }

    @Override
    public KeyPair generateKeyPair(Context context, Date end) throws GeneralSecurityException {
        Log.vf(TAG, "Generate key pair");
        KeyPairGenerator keyPairGenerator;
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();

        if (end == null) {
            calendar.add(Calendar.HOUR, DEFAULT_VALID_HOURS);
            end = calendar.getTime();
        } else if (end.before(now)) {
            IllegalArgumentException exception = new IllegalArgumentException("end < now");
            Log.wf(TAG, exception, "Error");
            throw exception;
        }

        KeyPair keyPair;

        try {
            RSAKeyGenParameterSpec rsaKeyGenParameterSpec = new RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSA_PUBLIC_EXPONENT);
            keyPairGenerator = KeyPairGenerator.getInstance(RSA, BC);
            keyPairGenerator.initialize(rsaKeyGenParameterSpec);
            keyPair = keyPairGenerator.generateKeyPair();

            // Create self-signed certificate
            X509V3CertificateGenerator certGenerator = new X509V3CertificateGenerator();
            certGenerator.setSerialNumber(BigInteger.valueOf(SERIAL_NUMBER));
            certGenerator.setSubjectDN(new X500Principal(X500_PRINCIPAL_DISTINGUISHED_NAME));
            certGenerator.setIssuerDN(new X500Principal(X500_ISSUER_PRINCIPAL_DISTINGUISHED_NAME));
            certGenerator.setNotBefore(now);
            certGenerator.setNotAfter(end);
            certGenerator.setPublicKey(keyPair.getPublic());
            certGenerator.setSignatureAlgorithm("SHA1withRSA");
            X509Certificate certificate = certGenerator.generate(keyPair.getPrivate(), BC);

            File ksFile = CertificatesAndKeys.getKeyStoreFile(context, FILE_NAME);
            CertificatesAndKeys.KeyStoreHelper.initNewKeyStore(ksFile,
                    OBF_SALT,
                    CertificatesAndKeys.KEYSTORE_PKCS12,
                    certificate,
                    keyPair.getPrivate(),
                    new Certificate[]{certificate}
            );
            Log.inf(TAG, "New key pair stored in a file.");
        } catch (Exception e) {
            Log.ef(TAG, e, "Exception occured when generating key pair.");
            throw new GeneralSecurityException(e);
        }

        return keyPair;
    }

    public PublicKey retrievePublicKey() throws PasswordPersisterException {
        Log.vf(TAG, "retrievePublicKey");
        FileInputStream inputStream = null;
        try {
            File keyStoreFile = CertificatesAndKeys.getKeyStoreFile(context, FILE_NAME);
            if (!keyStoreFile.exists()){
                return null;
            }
            inputStream = new FileInputStream(keyStoreFile);
            net.phonex.util.crypto.KeyPairGenerator kpg = new net.phonex.util.crypto.KeyPairGenerator();
            KeyStore keyStore = kpg.readKeyStore(inputStream, OBF_SALT.toCharArray());
            Certificate certificate = keyStore.getCertificate(CertificatesAndKeys.DEFAULT_KEY_ALIAS);

            if (certificate != null && certificate.getType().equals("X.509")) {
                try {
                    ((X509Certificate) certificate).checkValidity();
                } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                    MiscUtils.closeSilently(inputStream);
                    deleteKeyStore();
                    throw new PasswordPersisterException("Certificate not valid");
                }
            } else {
                throw new PasswordPersisterException("Certificate not available or incorrect certificate type");
            }
            PublicKey key = certificate.getPublicKey();
            if (!(key instanceof RSAPrivateKey)) {
                throw new PasswordPersister.InvalidKeyPairException("The saved key is not of expected type");
            }
            return key;
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException e) {
            throw new PasswordPersisterException(e);
        } finally {
            if (inputStream != null){
                MiscUtils.closeSilently(inputStream);
            }
        }
    }

    private void deleteKeyStore(){
        boolean deleted = CertificatesAndKeys.getKeyStoreFile(context, FILE_NAME).delete();
        Log.inf(TAG, "File %S deleted, success=%s", FILE_NAME, deleted);
    }

    @Override
    public boolean isKeyPairAvailable() {
        boolean exists = CertificatesAndKeys.getKeyStoreFile(context, FILE_NAME).exists();
        Log.df(TAG, "isKeyPairAvailable, result = %s", exists);
        return exists;
    }
}
