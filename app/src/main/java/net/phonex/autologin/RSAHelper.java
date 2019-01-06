package net.phonex.autologin;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.Cipher;

public class RSAHelper {
    private static final String RSA_SPEC = "RSA/ECB/PKCS1Padding";
    // Note: Do not specify a provider, this can cause problems for Android Marhsmallow:
    // http://stackoverflow.com/questions/32400689/crash-casting-to-rsaprivatekey
//    private static final String ANDROID_CRYPTO_PROVIDER = "AndroidOpenSSL";

    public static byte[] encrypt(PublicKey publicKey, final byte[] cleartext) throws GeneralSecurityException {
//        Cipher rsaCipher = Cipher.getInstance(RSA_SPEC, ANDROID_CRYPTO_PROVIDER);
        Cipher rsaCipher = Cipher.getInstance(RSA_SPEC);
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return rsaCipher.doFinal(cleartext);
    }

    public static byte[] decrypt(PrivateKey privateKey, final byte[] ciphertext) throws GeneralSecurityException {
//        Cipher rsaCipher = Cipher.getInstance(RSA_SPEC, ANDROID_CRYPTO_PROVIDER);
        Cipher rsaCipher = Cipher.getInstance(RSA_SPEC);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return rsaCipher.doFinal(ciphertext);
    }

    public static int getBlockSize(int keySize) {
        return keySize / 8;
    }

    public static int getClearBlockSize(int keySize) {
        return keySize / 8 - 11;
    }
}
