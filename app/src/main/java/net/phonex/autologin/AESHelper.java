package net.phonex.autologin;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class AESHelper {
    private static final String AES = "AES";
    private static final String AES_SPEC = "AES/CBC/PKCS5Padding";
    private static final int AES_KEY_SIZE_BIT = 128;

    private static byte[] doAES(final SecretKey secretKey, int opmode, final byte[] cleartext, final byte[] iv)
            throws GeneralSecurityException {
        Cipher aesCipher = Cipher.getInstance(AES_SPEC);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
        aesCipher.init(opmode, secretKey, ivParameterSpec);
        return aesCipher.doFinal(cleartext);
    }

    public static byte[] encrypt(final SecretKey secretKey, final byte[] cleartext, final byte[] iv)
            throws GeneralSecurityException {
        return doAES(secretKey, Cipher.ENCRYPT_MODE, cleartext, iv);
    }

    public static byte[] decrypt(final SecretKey secretKey, final byte[] ciphertext, final byte[] iv)
            throws GeneralSecurityException {
        return doAES(secretKey, Cipher.DECRYPT_MODE, ciphertext, iv);
    }

    public static SecretKey generateSecretKey(final SecureRandom random) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(AES);
        keyGenerator.init(AES_KEY_SIZE_BIT, random);
        return keyGenerator.generateKey();
    }
}
