package net.phonex.ft;

import com.google.protobuf.ByteString;

import net.phonex.pub.proto.FileTransfer.HybridEncryption;
import net.phonex.util.crypto.CryptoHelper.CipherException;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HybridCipher {
	// which cipher to use?
	// AES in Galois Counter Mode (Authenticated encryption)
	public static final String AES="AES/GCM/NoPadding";
	
	// AES block size, same holds for IV length.
	public static final int AES_BLOCK_SIZE = 16;
	
	// AES key size - fix to 256b.
	public static final int AES_KEY_SIZE = 32;
	
	// RSA with OAEP padding.
	public static final String RSA="RSA/ECB/OAEPWithSHA1AndMGF1Padding";
	
	// Key derivation function.
	public static final String PBKDF="PBKDF2WithHmacSHA1";
	
	// SpongyCastle static initialization
    static {
    	Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
    }
	
    /**
     * Simple routine for hybrid encryption. 
     * Should be only used if the source is small in length. Otherwise streaming
     * approach might be more suitable.
     * 
     * Plaintext to encrypt is byte array. Returned structure is hybridEncryption, which contain
     * IV, symmetrically encrypted input byte array with randomly generated AES encryption key K
     * and asymmetrically encrypted K.
     * 
     * @param src
     * @param pubKey
     * @param rand
     * @return
     * @throws CipherException 
     */
	public static HybridEncryption encrypt(byte[] src, PublicKey pubKey, SecureRandom rand) throws CipherException{
		try {
			// Generate random AES encryption key
			KeyGenerator keyGen = KeyGenerator.getInstance("AES", new BouncyCastleProvider());
			keyGen.init(AES_KEY_SIZE*8, rand); // for example
			SecretKey secretKey = keyGen.generateKey();
			
			// Generate initialization vector of the size of one AES block
			byte iv[] = new byte[AES_BLOCK_SIZE];
			rand.nextBytes(iv);
			IvParameterSpec ivspec = new IvParameterSpec(iv);
			
			// Encrypt the AES key with RSA
			Cipher rsa = Cipher.getInstance(RSA, new BouncyCastleProvider());
			rsa.init(Cipher.ENCRYPT_MODE, pubKey, rand);
			byte[] rsaCipherText = rsa.doFinal(secretKey.getEncoded());
			
			// Encrypt given data by AES-GCM
			Cipher aes = Cipher.getInstance(AES, new BouncyCastleProvider());
	        aes.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
			byte[] aesCipherText = aes.doFinal(src);
			
			HybridEncryption.Builder he = HybridEncryption.newBuilder();
			he.setIv(ByteString.copyFrom(iv));
			he.setACiphertext(ByteString.copyFrom(rsaCipherText));
			he.setSCiphertext(ByteString.copyFrom(aesCipherText));
			return he.build();
		} catch(Exception e){
			throw new CipherException("Exception during encryption", e);
		}
	}
	
	/**
	 * Simple routine for hybrid decryption.
	 * 
	 * @param he
	 * @param privKey
	 * @param rand
	 * @return
	 * @throws CipherException
	 */
	public static byte[] decrypt(HybridEncryption he, PrivateKey privKey, SecureRandom rand) throws CipherException{
		try {
			ByteString aCiphertextBS = he.getACiphertext();
			ByteString sCiphertextBS = he.getSCiphertext();
			ByteString ivBS = he.getIv();
			
			// Decrypt symmetric encryption key.
			Cipher rsa = Cipher.getInstance(RSA, new BouncyCastleProvider());
			rsa.init(Cipher.DECRYPT_MODE, privKey, rand);
			byte[] aesKey = rsa.doFinal(aCiphertextBS.toByteArray());
			
			// Reconstruct IV
			IvParameterSpec ivspec = new IvParameterSpec(ivBS.toByteArray());
			
			// Reconstruct AES key
			SecretKey secret = new SecretKeySpec(aesKey, "AES");
			
			// Decrypt
			Cipher aes = Cipher.getInstance(AES, new BouncyCastleProvider());
	        aes.init(Cipher.DECRYPT_MODE, secret, ivspec);
	        return aes.doFinal(sCiphertextBS.toByteArray());
		} catch(Exception e){
			throw new CipherException("Exception during encryption", e);
		}
	}
	
	
}
