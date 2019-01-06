package net.phonex.util.crypto;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES cipher in CBC mode, special cipher text format. 
 * AES works in 265bit mode, PKCS5 padding.
 * 
 * 
 * @author ph4r05
 *
 */
public class AESCipher {
	// which cipher to use?
	public static final String AES="AES/CBC/PKCS5Padding";
	
	// salt size in bytes
	public static final int SALT_SIZE = 12;
	
	// AES key size - fix to 256
	public static final int AES_KEY_SIZE = 32;
	
	// AES key size - fix to 256
	public static final int AES_BLOCK_SIZE = 16;
	
	// how many iterations should key derivation perform?
	public static final int KEY_GEN_ITERATIONS = 1024;
	
	/**
	 * Function returns JCA AES algorithm description.
	 * 
	 * @return
	 */
	public static String getAESDescriptor(){
		return AES;
	}
	
    /**
     * Encrypt plaintext with given key
     * 
     * @param plaintext
     * @param key
     * @return
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidKeySpecException 
     * @throws InvalidParameterSpecException 
     */
	public static byte[] encrypt(byte[] plaintext, char[] password) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException{
		return encrypt(plaintext, password, new SecureRandom());
	}
	
	/**
	 * Encrypt plaintext with given key
	 * 
	 * @param plaintext
	 * @param password
	 * @param rand
	 * @return
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws NoSuchPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeySpecException
	 * @throws InvalidParameterSpecException
	 */
	public static byte[] encrypt(byte[] plaintext, char[] password, Random rand) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException{		
		// generate salt
		byte[] salt = new byte[SALT_SIZE];
		rand.nextBytes(salt);
		
		// derive AES encryption key using password and salt
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		KeySpec spec = new PBEKeySpec(password, salt, KEY_GEN_ITERATIONS, AES_KEY_SIZE * 8);
		SecretKey tmp = factory.generateSecret(spec);
		SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
		
		// generate initialization vector of the size of one AES block
		byte iv[] = new byte[AES_BLOCK_SIZE];
		rand.nextBytes(iv);
		IvParameterSpec ivspec = new IvParameterSpec(iv);
		
		// do encryption
		Cipher cipher = Cipher.getInstance(getAESDescriptor());
		cipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
		byte[] ciphertext = cipher.doFinal(plaintext);
		
		byte[] result = new byte[salt.length + iv.length + ciphertext.length];
		System.arraycopy(salt, 		 0, result, 0, 							salt.length);
		System.arraycopy(iv, 		 0, result, salt.length, 				iv.length);
		System.arraycopy(ciphertext, 0, result, salt.length + iv.length, 	ciphertext.length);
		return result;
	}
	
	/**
	 * Encrypt with given AES-256 key.
	 * No PBKDF2 is used. Key has to have exact length.
	 * Format of the cipher block is preserved as with PBKDF2 (salt).
	 * 
	 * @param plaintext
	 * @param key
	 * @param rand
	 * @return
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws NoSuchPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeySpecException
	 * @throws InvalidParameterSpecException
	 */
	public static byte[] encrypt(byte[] plaintext, byte[] key, Random rand) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException{
		// generate salt
		byte[] salt = new byte[SALT_SIZE];
				
		// derive AES encryption key using password and salt
		SecretKey secret = new SecretKeySpec(key, "AES");
		
		// generate initialization vector of the size of one AES block
		byte iv[] = new byte[AES_BLOCK_SIZE];
		rand.nextBytes(iv);
		IvParameterSpec ivspec = new IvParameterSpec(iv);
		
		// do encryption
		Cipher cipher = Cipher.getInstance(getAESDescriptor());
		cipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
		byte[] ciphertext = cipher.doFinal(plaintext);
		
		byte[] result = new byte[salt.length + iv.length + ciphertext.length];
		System.arraycopy(salt, 		 0, result, 0, 							salt.length);
		System.arraycopy(iv, 		 0, result, salt.length, 				iv.length);
		System.arraycopy(ciphertext, 0, result, salt.length + iv.length, 	ciphertext.length);
		return result;
	}
	
	/**
	 * Streamed encryption.
	 * No PBKDF2 is used. Key has to have exact length.
	 * Format of the cipher block is preserved as with PBKDF2 (salt).
	 * 
	 * @param is
	 * @param os
	 * @param key
	 * @param rand
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws NoSuchPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeySpecException
	 * @throws InvalidParameterSpecException
	 * @throws IOException 
	 */
	public static void encrypt(InputStream is, OutputStream os, byte[] key, Random rand) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException, IOException{
		// generate salt
		byte[] salt = new byte[SALT_SIZE];
				
		// derive AES encryption key using password and salt
		SecretKey secret = new SecretKeySpec(key, "AES");
		
		// generate initialization vector of the size of one AES block
		byte iv[] = new byte[AES_BLOCK_SIZE];
		rand.nextBytes(iv);
		IvParameterSpec ivspec = new IvParameterSpec(iv);
		
		// do encryption
		Cipher cipher = Cipher.getInstance(getAESDescriptor());
		cipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
		
		DataOutputStream dos = new DataOutputStream(os); 
		dos.write(salt);
		dos.write(iv);
		
		@SuppressWarnings("resource")
		CipherOutputStream cos = new CipherOutputStream(dos, cipher);
		int numBytes;
        byte[] bytes = new byte[512];
        while ((numBytes = is.read(bytes)) != -1) {
			cos.write(bytes, 0, numBytes);
		}
		
        // Do not call close since it closes underlying stream and this method
        // is not supposed to do that.
		cos.flush();
		dos.flush();
	}
	
	/**
     * Decrypt ciphertext, assume structure salt:iv:ciphertext. 4:16:x bytes
     * 
     * @param plaintext
     * @param key
     * @return
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidKeySpecException 
     * @throws InvalidParameterSpecException 
     */
	public static byte[] decrypt(byte[] cipherblock, char[] password) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException{
		// split passed cipherblock to parts
		if (cipherblock.length <= (SALT_SIZE+AES_BLOCK_SIZE)){
			throw new IllegalArgumentException("cipher block is too small");
		}
		
		int cipherLen = cipherblock.length - SALT_SIZE - AES_BLOCK_SIZE;
		byte[] salt = new byte[SALT_SIZE];
		byte[] iv = new byte[16];
		byte[] ciphertext = new byte[cipherLen];
		System.arraycopy(cipherblock, 0, 							salt, 		0, 	SALT_SIZE);
		System.arraycopy(cipherblock, SALT_SIZE,  					iv, 		0, 	AES_BLOCK_SIZE);
		System.arraycopy(cipherblock, SALT_SIZE + AES_BLOCK_SIZE, 	ciphertext, 0, 	ciphertext.length);
		
		// derive AES encryption key using password and salt
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		KeySpec spec = new PBEKeySpec(password, salt, KEY_GEN_ITERATIONS, AES_KEY_SIZE * 8);
		SecretKey tmp = factory.generateSecret(spec);
		SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
		
		Cipher cipher = Cipher.getInstance(getAESDescriptor());
		cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
		return cipher.doFinal(ciphertext);
	}
	
	/**
	 * Decrypt with given key.
	 * No PBKDF2 is used. Key has to have exact length.
	 * Format of the cipher block is preserved as with PBKDF2 (salt).
	 * 
	 * @param cipherblock
	 * @param key
	 * @return
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws NoSuchPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeySpecException
	 * @throws InvalidParameterSpecException
	 */
	public static byte[] decrypt(byte[] cipherblock, byte[] key) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException{
		// split passed cipherblock to parts
		if (cipherblock.length <= (SALT_SIZE+AES_BLOCK_SIZE)){
			throw new IllegalArgumentException("cipher block is too small");
		}
		
		int cipherLen = cipherblock.length - SALT_SIZE - AES_BLOCK_SIZE;
		byte[] salt = new byte[SALT_SIZE];
		byte[] iv = new byte[16];
		byte[] ciphertext = new byte[cipherLen];
		System.arraycopy(cipherblock, 0, 							salt, 		0, 	SALT_SIZE);
		System.arraycopy(cipherblock, SALT_SIZE,  					iv, 		0, 	AES_BLOCK_SIZE);
		System.arraycopy(cipherblock, SALT_SIZE + AES_BLOCK_SIZE, 	ciphertext, 0, 	ciphertext.length);

		SecretKey secret = new SecretKeySpec(key, "AES");		
		Cipher cipher = Cipher.getInstance(getAESDescriptor());
		cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
		return cipher.doFinal(ciphertext);
	}
	
	/**
	 * Streamed decryption.
	 * 
	 * No PBKDF2 is used. Key has to have exact length.
	 * Format of the cipher block is preserved as with PBKDF2 (salt).
	 * 
	 * @param is
	 * @param os
	 * @param key
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws NoSuchPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeySpecException
	 * @throws InvalidParameterSpecException
	 * @throws IOException 
	 */
	public static void decrypt(InputStream is, OutputStream os, byte[] key) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException, IOException{
		BufferedInputStream bif = new BufferedInputStream(is);
		
		byte[] salt = new byte[SALT_SIZE];
		byte[] iv = new byte[AES_BLOCK_SIZE];

		int saltLen = bif.read(salt);
		if (saltLen != SALT_SIZE){
			throw new IllegalArgumentException("cipher block is too small");
		}
		
		int ivLen = bif.read(iv);
		if (ivLen != AES_BLOCK_SIZE){
			throw new IllegalArgumentException("cipher block is too small");
		}

		SecretKey secret = new SecretKeySpec(key, "AES");		
		Cipher cipher = Cipher.getInstance(getAESDescriptor());
		cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
		
		@SuppressWarnings("resource")
		CipherInputStream cis = new CipherInputStream(bif, cipher);
		
		int numBytes;
        byte[] bytes = new byte[512];
        while ((numBytes = cis.read(bytes)) != -1) {
			os.write(bytes, 0, numBytes);
		}
		
        // Do not call close on streams since it closes underlying stream and this method
        // is not supposed to do that.
		os.flush();
	}
}
