package net.phonex.util.crypto;

import android.util.Log;

import net.phonex.ft.misc.Canceller;
import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.ft.misc.TransmitProgress;
import net.phonex.ft.HybridCipher;
import net.phonex.util.MiscUtils;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoHelper {
	public static final String THIS_FILE = "CryptoHelper";
	
	public static final String BC = org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
	
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
	
	// RSA signature with SHA256
	public static final String SIGN_DESC  = "SHA256withRSA";   // SHA256withRSA
	
	// HMAC SHA 256
	public static final String HMAC = "HmacSHA256";

    // HMAC key size - same as underlying hash function
    public static final int HMAC_KEY_SIZE = 32;

	
	// SpongyCastle static initialization
    static {
    	Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
    }
    
	/**
	 * Simple PBKDF2 wrapper.
	 * 
	 * @param phrase
	 * @param salt
	 * @param iterations
	 * @param keylen
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchProviderException 
	 */
	public static byte[] pbkdf2(String phrase, byte[] salt, int iterations, int keylen) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException{
		if (phrase==null) {
			throw new IllegalArgumentException("Empty phrase");
		}		
		
		SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF);
		KeySpec keyspec = new PBEKeySpec(phrase.toCharArray(), salt, iterations, keylen);
		Key key = factory.generateSecret(keyspec);
		
		// Return string hashed interpretation.
		return key.getEncoded();
	}
	
	/**
	 * Simple PBKDF2 wrapper.
	 * Returns derived password as SHA-512(password) hexa string. 
	 * 
	 * @param phrase
	 * @param salt
	 * @param iterations
	 * @param keylen
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchProviderException 
	 */
	public static String pbkdf2String(String phrase, byte[] salt, int iterations, int keylen) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException{
		// Return string hashed interpretation.
		return MessageDigest.hashHexSha512(pbkdf2(phrase, salt, iterations, keylen));
	}
	
	/**
	 * Returns array of random size of given bytes.
	 * @param bytes
	 * @param rand
	 * @return
	 */
	public static byte[] getSalt(int bytes, Random rand){
		byte salt[] = new byte[bytes];
		rand.nextBytes(salt);
		return salt;
	}
	
	/**
	 * Returns array of random size of given bytes.
	 * Uses SecureRandom as a PRNG.
	 * @param bytes
	 * @return
	 */
	public static byte[] getSalt(int bytes){
		return getSalt(bytes, new SecureRandom());
	}
	
	/**
	 * Streamed application of the given cipher. 
	 * InputStream -> cipherOutputStream(cipher) -> OutputStream.
	 * 
	 * @param is
	 * @param os
	 * @param cip
	 * @param c
	 * @param pr
	 * @throws IOException
	 */
	public static void streamedEncryption(InputStream is, OutputStream os, Cipher cip, Canceller c, TransmitProgress pr) throws IOException{
        CipherOutputStream cos = new CipherOutputStream(new NotClosingOutputStream(os), cip);
        
        boolean cancelled=false;
        long absBytes=0;
        int numBytes;
        byte[] bytes = new byte[512];
        while ((numBytes = is.read(bytes)) != -1) {
			cos.write(bytes, 0, numBytes);
			absBytes += numBytes;
			
			// Progress ?
			if (pr!=null){
				pr.updateTxProgress(absBytes);
			}
			
			// Cancelled ?
			if (c!=null && c.isCancelled()){
				cancelled=true;
				break;
			}
		}
        cos.flush();
        cos.close();
        
        // If cancelled, throw exception to inform caller about unusual situation.
        if (cancelled){
        	throw new OperationCancelledException();
        }
	}

	/**
	 * Streamed application of the given cipher. 
	 * InputStream -> cipherOutputStream(cipher) -> OutputStream.
	 * 
	 * @param is
	 * @param os
	 * @param cip
	 * @throws IOException
	 */
	public static void streamedEncryption(InputStream is, OutputStream os, Cipher cip) throws IOException{
		streamedEncryption(is, os, cip, null, null);
	}
	
	/**
	 * Streamed application of the given cipher. 
	 * InputStream -> cipherOutputStream(cipher) -> OutputStream.
	 * 
	 * @param file
	 * @param os
	 * @param cip
	 * @throws IOException
	 */
	public static void streamedEncryption(File file, OutputStream os, Cipher cip) throws IOException{
		InputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file));
			streamedEncryption(is, os, cip);
		} finally {
			MiscUtils.closeSilently("streamedEncryption", is);
		}
	}

	/**
	 * Reads exactly n bytes from the input stream.
	 * @param is
	 * @param n
	 */
	public static void dropFirst(final InputStream is, final int n, final Canceller c) throws IOException {
		if (n <= 0){
			return;
		}

		final int buffSize = n >= 2048 ? 2048 : n;
		final byte[] bytes = new byte[buffSize];

		int numBytes;
		int offReadTotal = n;
		boolean cancelled=false;

		while (offReadTotal > 0 && (numBytes = is.read(bytes, 0, offReadTotal >= buffSize ? buffSize : offReadTotal)) != -1) {
			offReadTotal -= numBytes;

			// Cancelled ?
			if (c!=null && c.isCancelled()){
				cancelled=true;
				break;
			}
		}

		// If cancelled, throw exception to inform caller about unusual situation.
		if (cancelled){
			throw new OperationCancelledException();
		}

		if (offReadTotal != 0){
			throw new IOException("Could not read exactly " + n + " bytes");
		}
	}

	/**
	 * Streamed application of a given cipher.
	 * InputStream -> cipherInputStream(cipher) -> OutputStream.
	 * 
	 * @param is
	 * @param os
	 * @param cip
	 * @throws IOException
	 */
	public static void streamedDecryption(InputStream is, long offset, OutputStream os, Cipher cip, Canceller c, TransmitProgress pr) throws IOException {
        // Offsetting support here.
		dropFirst(is, (int)offset, c);

		CipherInputStream cis = new CipherInputStream(new NotClosingInputStream(is), cip);
        
        boolean cancelled=false;
        long absBytes=0;
        int numBytes;
        final byte[] bytes = new byte[2048];
        while ((numBytes = cis.read(bytes)) != -1) {
			os.write(bytes, 0, numBytes);
			absBytes += numBytes;
			
			// Progress ?
			if (pr!=null){
				pr.updateTxProgress(absBytes);
			}
			
			// Cancelled ?
			if (c!=null && c.isCancelled()){
				cancelled=true;
				break;
			}
		}
        
        os.flush();
        cis.close();
        
        // If cancelled, throw exception to inform caller about unusual situation.
        if (cancelled){
        	throw new OperationCancelledException();
        }
	}
	
	/**
	 * Streamed application of a given cipher.
	 * InputStream -> cipherInputStream(cipher) -> OutputStream.
	 * 
	 * @param is
	 * @param os
	 * @param cip
	 * @throws IOException
	 */
	public static void streamedDecryption(InputStream is, OutputStream os, Cipher cip) throws IOException {
		streamedDecryption(is, 0, os, cip, null, null);
	}
	
	/**
	 * Streamed application of a given cipher.
	 * InputStream -> cipherInputStream(cipher) -> OutputStream.
	 * 
	 * @param file
	 * @param offset
	 * @param os
	 * @param cip
	 * @throws IOException
	 */
	public static void streamedDecryption(File file, long offset, OutputStream os, Cipher cip, Canceller c, TransmitProgress pr) throws IOException{
		InputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file));
			streamedDecryption(is, offset, os, cip, c, pr);
		} finally {
			MiscUtils.closeSilently("streamedDecryption", is);
		}
	}
	
	/**
	 * Streamed application of a given cipher.
	 * InputStream -> cipherInputStream(cipher) -> OutputStream.
	 * 
	 * @param file
	 * @param offset
	 * @param os
	 * @param cip
	 * @throws IOException
	 */
	public static void streamedDecryption(File file, long offset, OutputStream os, Cipher cip) throws IOException{
		streamedDecryption(file, offset, os, cip, null, null);
	}
	
	/**
     * Simple routine for hybrid encryption. 
     * Works with streams, is more suitable for encryption of a large files.
     * 
     * Random AES key is encrypted with RSA_OAEP. AES-GCM-256 is used.
     * 
     * Format of the output is the following:
     * | 32bit length (uint32_t) of the RSA block | RSA BLOCK | 16B IV | AES-GCM data ... |
     * 
     * @param is
     * @param os
     * @param pubKey
     * @param rand
     * @return
     * @throws CipherException 
     */
	public static void encrypt(InputStream is, OutputStream os, PublicKey pubKey, SecureRandom rand) throws CipherException{
		try {
			// Generate random AES encryption key
			KeyGenerator keyGen = KeyGenerator.getInstance("AES", BC);
			keyGen.init(AES_KEY_SIZE*8); // for example
			SecretKey secretKey = keyGen.generateKey();
			
			// Generate initialization vector of the size of one AES block
			byte iv[] = new byte[AES_BLOCK_SIZE];
			rand.nextBytes(iv);
			IvParameterSpec ivspec = new IvParameterSpec(iv);
			
			// Encrypt the AES key with RSA
			Cipher rsa = Cipher.getInstance(RSA, BC);
			rsa.init(Cipher.ENCRYPT_MODE, pubKey, rand);
			byte[] rsaCipherText = rsa.doFinal(secretKey.getEncoded());
			
			// Encrypt given data by AES-GCM
			Cipher aes = Cipher.getInstance(AES, BC);
	        aes.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
	        
	        // Produce some output
	        DataOutputStream dos = new DataOutputStream(new NotClosingOutputStream(os));
	        dos.writeInt(rsaCipherText.length);
	        dos.write(rsaCipherText);
	        dos.write(iv);
	        
	        // Dump AES ciphertext using CipherOutpuStream.
	        streamedEncryption(is, dos, aes);
	        dos.flush();
	        dos.close();
			
		} catch(Exception e){
			throw new CipherException("Exception during encryption", e);
		}
	}

	/**
	 * Generate key + iv and encrypt plaintext
	 * @param plaintext
	 * @return
     */
	public static AesEncryptionResult encryptAesGcm(byte[] plaintext) throws CipherException {
		try {
			SecureRandom secureRandom = new SecureRandom();

			KeyGenerator keyGen = KeyGenerator.getInstance("AES", BC);
			keyGen.init(AES_KEY_SIZE * 8);
			SecretKey secretKey = keyGen.generateKey();

			byte iv[] = new byte[AES_BLOCK_SIZE];
			secureRandom.nextBytes(iv);
			IvParameterSpec ivspec = new IvParameterSpec(iv);

			Cipher aes = Cipher.getInstance(AES, BC);
			aes.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
			byte[] ciphertext = aes.doFinal(plaintext);
			return new AesEncryptionResult(ciphertext, secretKey, iv);
		} catch (Exception e){
			throw new CipherException("Exception during encryption", e);
		}
	}

	/**
	 * Opposite of encryptAesGcm
	 * @param ciphertext
	 * @param iv
	 * @param secretKey
     * @return
     */
	public static byte[] decryptAesGcm(byte[] ciphertext, byte[] iv, SecretKey secretKey) throws CipherException {
		try {
			Cipher aes = Cipher.getInstance(AES, BC);
			aes.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
			return aes.doFinal(ciphertext);
		} catch (Exception e){
			throw new CipherException("Exception during decryption", e);
		}
	}

	/**
	 * Wraps encryption result
	 */
	public static class AesEncryptionResult {
		public AesEncryptionResult(byte[] ciphertext, SecretKey secretKey, byte[] iv) {
			this.iv = iv;
			this.ciphertext = ciphertext;
			this.secretKey = secretKey;
		}

		public byte[] iv;
		public byte[] ciphertext;
		public SecretKey secretKey;
	}
	
	/**
	 * Non-stream version of the encryption.
	 * 
	 * Random AES key is encrypted with RSA_OAEP. AES-GCM-256 is used.
	 * 
	 * Format of the output is the following:
     * | 32bit length (uint32_t) of the RSA block | RSA BLOCK | 16B IV | AES-GCM data ... |
     * 
	 * @param input
	 * @param pubKey
	 * @param rand
	 * @return
	 * @throws CipherException
	 */
	public static byte[] encrypt(byte[] input, PublicKey pubKey, SecureRandom rand) throws CipherException {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(input);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			encrypt(bis, bos, pubKey, rand);
			bis.close();
			
			final byte[] ret = bos.toByteArray();
			bos.close();
			
			return ret;			
		} catch(Exception e){
			throw new CipherException("Exception during encryption", e);
		}
	}
	
	/**
	 * Simple stream routine for hybrid decryption.
	 * Format of the binary message is expeted to be the same 
	 * as in the HybridCipher.encrypt.
	 * 
	 * Random AES key is decrypted with RSA_OAEP. AES-GCM-256 is used.
	 * 
	 * Format of the output is the following:
     * | 32bit length (uint32_t) of the RSA block | RSA BLOCK | 16B IV | AES-GCM data ... |
	 * 
	 * @param is
	 * @param os
	 * @param privKey
	 * @param rand
	 * @return
	 * @throws CipherException
	 */
	public static void decrypt(InputStream is, OutputStream os, PrivateKey privKey, SecureRandom rand) throws CipherException{
		try {
			DataInputStream dis = new DataInputStream(new NotClosingInputStream(is));
			
			int rsaLen = dis.readInt();
			byte rsaCipherText[] = new byte[rsaLen];
			byte iv[] = new byte[AES_BLOCK_SIZE];
			
			// Read RSA ciphertext
			int rsaRead = dis.read(rsaCipherText, 0, rsaLen);
			if (rsaRead!=rsaLen){
				MiscUtils.closeSilently("decrypt, dis", dis);
				throw new RuntimeException("Stream ended prematurely");
			}
			
			// Read IV
			int ivRead = dis.read(iv, 0, AES_BLOCK_SIZE);
			if (ivRead!=AES_BLOCK_SIZE){
				MiscUtils.closeSilently("decrypt, dis", dis);
				throw new RuntimeException("Stream ended prematurely");
			}
			
			// Decrypt symmetric encryption key.
			Cipher rsa = Cipher.getInstance(RSA, BC);
			rsa.init(Cipher.DECRYPT_MODE, privKey, rand);
			byte[] aesKey = rsa.doFinal(rsaCipherText);
			
			// Reconstruct IV
			IvParameterSpec ivspec = new IvParameterSpec(iv);
			
			// Reconstruct AES key
			SecretKey secret = new SecretKeySpec(aesKey, "AES");
			
			// AES decryption instance
			Cipher aes = Cipher.getInstance(AES, new BouncyCastleProvider());
	        aes.init(Cipher.DECRYPT_MODE, secret, ivspec);
	        
	        // Decrypt the cipher stream
	        streamedDecryption(dis, os, aes);
	        os.flush();
	        
	        MiscUtils.closeSilently("decrypt, dis", dis);
		} catch(Exception e){
			throw new CipherException("Exception during encryption", e);
		}
	}
	
	/**
	 * Simple non-stream routine for hybrid decryption.
	 * Format of the binary message is expeted to be the same 
	 * as in the HybridCipher.encrypt.
	 * 
	 * Random AES key is decrypted with RSA_OAEP. AES-GCM-256 is used.
	 * 
	 * Format of the output is the following:
     * | 32bit length (uint32_t) of the RSA block | RSA BLOCK | 16B IV | AES-GCM data ... |
	 * 
	 * @param input
	 * @param privKey
	 * @param rand
	 * @return
	 * @throws CipherException
	 */
	public static byte[] decrypt(byte[] input, PrivateKey privKey, SecureRandom rand) throws CipherException {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(input);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			decrypt(bis, bos, privKey, rand);
			bis.close();
			
			final byte[] ret = bos.toByteArray();
			bos.close();
			
			return ret;			
		} catch(Exception e){
			throw new CipherException("Exception during encryption", e);
		}
	}
	
	/**
	 * Simple routine for creating digital signatures.
	 * RSA with SHA256 is used.
	 * 
	 * @param what
	 * @param privKey
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws java.security.SignatureException
	 * @throws NoSuchProviderException 
	 */
	public static byte[] sign(byte[] what, PrivateKey privKey, SecureRandom rand) throws NoSuchAlgorithmException, InvalidKeyException, java.security.SignatureException, NoSuchProviderException{
    	Signature mySign = Signature.getInstance(SIGN_DESC, BC);
    	mySign.initSign(privKey, rand);
		mySign.update(what);
		return mySign.sign();
    }
	
	/**
	 * Simple routine for creating digital signatures. Stream version.
	 * RSA with SHA256 is used.
	 * 
	 * @param is
	 * @param privKey
	 * @param rand
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws java.security.SignatureException
	 * @throws IOException
	 * @throws NoSuchProviderException 
	 */
	public static byte[] sign(InputStream is, PrivateKey privKey, SecureRandom rand) throws NoSuchAlgorithmException, InvalidKeyException, java.security.SignatureException, IOException, NoSuchProviderException{
    	Signature mySign = Signature.getInstance(SIGN_DESC, BC);
    	mySign.initSign(privKey, rand);
    	
    	int numBytes;
        byte[] bytes = new byte[512];
        while ((numBytes = is.read(bytes)) != -1) {
        	mySign.update(bytes, 0, numBytes);
		}
        
		return mySign.sign();
    }
    
	/**
	 * Simple routine for verification of digital signatures.
	 * RSA with SHA256 is used.
	 * 
	 * @param what
	 * @param signature
	 * @param pubkey
	 * @return
	 * @throws InvalidKeyException
	 * @throws java.security.SignatureException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException 
	 */
    public static boolean verify(byte[] what, byte[] signature, PublicKey pubkey) throws InvalidKeyException, java.security.SignatureException, NoSuchAlgorithmException, NoSuchProviderException{
	    Signature myVerifySign = Signature.getInstance(SIGN_DESC, BC);
	    myVerifySign.initVerify(pubkey);
	    myVerifySign.update(what);
	    return myVerifySign.verify(signature);
    }
    
    /**
     * Simple routine for verification of digital signatures. Stream version.
	 * RSA with SHA256 is used.
	 * 
     * @param is
     * @param signature
     * @param pubkey
     * @return
     * @throws InvalidKeyException
     * @throws java.security.SignatureException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static boolean verify(InputStream is, byte[] signature, PublicKey pubkey) throws InvalidKeyException, java.security.SignatureException, NoSuchAlgorithmException, IOException{
	    Signature myVerifySign = Signature.getInstance(SIGN_DESC);
	    myVerifySign.initVerify(pubkey);
	    
	    int numBytes;
        byte[] bytes = new byte[512];
        while ((numBytes = is.read(bytes)) != -1) {
        	myVerifySign.update(bytes, 0, numBytes);
		}
        
	    return myVerifySign.verify(signature);
    }
    
    /**
     * HMAC with SHA256.
     * 
     * @param input
     * @param key
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws InvalidKeyException
     */
    public static byte[] hmac(byte[] input, byte[] key) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException{
    	 Mac sha256_HMAC = Mac.getInstance(HMAC, BC);
    	 SecretKeySpec secret_key = new SecretKeySpec(key, HMAC);
    	 sha256_HMAC.init(secret_key);
    	 return sha256_HMAC.doFinal(input);
    }
    
    /**
     * HMAC with SHA256. Streamed version.
     * 
     * @param is
     * @param key
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws InvalidKeyException
     * @throws IOException 
     * @throws IllegalStateException 
     */
    public static byte[] hmac(InputStream is, byte[] key) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, IllegalStateException, IOException{
    	 Mac sha256_HMAC = Mac.getInstance(HMAC, BC);
    	 SecretKeySpec secret_key = new SecretKeySpec(key, HMAC);
    	 sha256_HMAC.init(secret_key);
    	 
    	 int numBytes;
         byte[] bytes = new byte[512];
         while ((numBytes = is.read(bytes)) != -1) {
        	 sha256_HMAC.update(bytes, 0, numBytes);
 		 }
         
    	 return sha256_HMAC.doFinal();
    }
    
    /**
     * Simple helper method for concatenation of X byte arrays.
     * 
     * @param arrays
     * @return
     */
    public static byte[] concat(byte[]...arrays){
        // Determine the length of the result array
        int totalLength = 0;
        for (int i = 0; i < arrays.length; i++){
            totalLength += arrays[i].length;
        }

        // create the result array
        byte[] result = new byte[totalLength];

        // copy the source arrays into the result array
        int currentIndex = 0;
        for (int i = 0; i < arrays.length; i++){
            System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
            currentIndex += arrays[i].length;
        }

        return result;
    }
	
    /**
     * Computes hash of the input stream with given hash algorithm.
     * @param is
     * @param hashAlg
     * @return
     * @throws NoSuchAlgorithmException 
     * @throws IOException 
     */
    public static byte[] hash(InputStream is, String hashAlg) throws NoSuchAlgorithmException, IOException{
    	java.security.MessageDigest hasher = java.security.MessageDigest.getInstance(hashAlg);
    	
    	int numBytes;
        byte[] bytes = new byte[512];
        while ((numBytes = is.read(bytes)) != -1) {
        	hasher.update(bytes, 0, numBytes);
		}
        
		return hasher.digest();
    }
    
    /**
     * Computes hash of the input stream with given hash algorithm.
     * @param file
     * @param hashAlg
     * @return
     * @throws NoSuchAlgorithmException 
     * @throws IOException 
     */
    public static byte[] hash(File file, String hashAlg) throws NoSuchAlgorithmException, IOException{
    	InputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file));
			return hash(is, hashAlg);
		} finally {
			try {
				if (is!=null){
					is.close();
				}
			} catch(Exception e){
				Log.e(THIS_FILE, "Cannot close input stream in generateFTFileMac.");
			}
		}
    }
    
    /**
     * Stream that delegates output stream write operations 
     * to the given output stream but does not closes itself 
     * upon close() invocation.
     * 
     * This is needed for some streams since e.g., 
     * CipherOutputStream needs close() to be called (in order 
     * to finalize padding), but 
     * 
     * @author ph4r05
     *
     */
    public static class NotClosingOutputStream extends OutputStream {
    	private final OutputStream os;

		public NotClosingOutputStream(OutputStream os) {
			this.os = os;
		}

		@Override
		public void write(int b) throws IOException {
			os.write(b);
		}

		@Override
		public void close() throws IOException {
			// do not close.
		}
		
		@Override
		public void flush() throws IOException {
			os.flush();
		}

		@Override
		public void write(byte[] buffer, int offset, int count) throws IOException {
			os.write(buffer, offset, count);
		}

		@Override
		public void write(byte[] buffer) throws IOException {
			os.write(buffer);
		}
    }
    
    /**
     * Stream that delegates input stream read operations 
     * to the given input stream but does not closes itself 
     * upon close() invocation. 
     * 
     * @author ph4r05
     *
     */
    public static class NotClosingInputStream extends InputStream {
    	private final InputStream is;

    	public NotClosingInputStream(InputStream is) {
			this.is = is;
		}
    	
		@Override
		public int read() throws IOException {
			return is.read();
		}

		@Override
		public int available() throws IOException {
			return is.available();
		}

		@Override
		public void close() throws IOException {
			// do not close.
		}

		@Override
		public void mark(int readlimit) {
			is.mark(readlimit);
		}

		@Override
		public boolean markSupported() {
			return is.markSupported();
		}

		@Override
		public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
			return is.read(buffer, byteOffset, byteCount);
		}

		@Override
		public int read(byte[] buffer) throws IOException {
			return is.read(buffer);
		}

		@Override
		public synchronized void reset() throws IOException {
			is.reset();
		}

		@Override
		public long skip(long byteCount) throws IOException {
			return is.skip(byteCount);
		}
    }
    
	public static class CipherException extends Exception {
		private static final long serialVersionUID = 1L;

		public CipherException() {
			super();
		}

		public CipherException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public CipherException(String detailMessage) {
			super(detailMessage);
		}

		public CipherException(Throwable throwable) {
			super(throwable);
		}
	}
	
	public static class SignatureException extends Exception {
		private static final long serialVersionUID = 1L;

		public SignatureException() {
			super();
		}

		public SignatureException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public SignatureException(String detailMessage) {
			super(detailMessage);
		}

		public SignatureException(Throwable throwable) {
			super(throwable);
		}
	}
	
	public static class MACVerificationException extends Exception {
		private static final long serialVersionUID = 1L;

		public MACVerificationException() {
			super();
		}

		public MACVerificationException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public MACVerificationException(String detailMessage) {
			super(detailMessage);
		}

		public MACVerificationException(Throwable throwable) {
			super(throwable);
		}
	}
}
