package net.phonex.util.crypto;

import org.spongycastle.util.encoders.Base64;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Random;

/**
 * Generates hash for particular messages
 * @author ph4r05
 */
public class MessageDigest {
	 /**
     * Generates randomized hash on base64 encoding from given seed
     * @param seed
     * @param randomized    if true, random number and current time in ms are appended to seed.
     * @return 
     */
    public static String generateHash(String seed, boolean randomized) throws NoSuchAlgorithmException{
    	return generateHash(seed, randomized, 1);
    }
	 /**
     * Generates randomized hash on base64 encoding from given seed
     * @param seed
     * @param randomized    if true, random number and current time in ms are appended to seed.
     * @return 
     */
    public static String generateHash(String seed, boolean randomized, int iterations) throws NoSuchAlgorithmException{
    	java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-512");
    	return generateHash(seed, randomized, iterations, sha);
    }
    
    /**
     * Generates randomized hash on base64 encoding from given seed, using SHA 256
     * @param seed
     * @param randomized
     * @param iterations
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String generateHashSha256(String seed, boolean randomized, int iterations) throws NoSuchAlgorithmException{
    	java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
    	return generateHash(seed, randomized, iterations, sha);
    }
    	
    /**
     * Applies given hash function iteratively.
     * 
     * @param input
     * @param iterations
     * @param hash
     * @return
     */
    public static byte[] iterativeHash(byte[] input, int iterations, java.security.MessageDigest hash) {
        byte[] digest = null;
        for(int i=0; i<iterations; i++){
        	digest = hash.digest(input);
        	input = digest;
        }
        
        return digest;
    }
    
    /**
     * Generates randomized hash on base64 encoding from given seed, using given hash function
     * @param seed
     * @param randomized
     * @param iterations
     * @param hash
     * @return
     */
    public static String generateHash(String seed, boolean randomized, int iterations, java.security.MessageDigest hash) {
        String sseed = seed;
        
        if (randomized) {
            Random rand = new SecureRandom();
            StringBuilder sb = new StringBuilder(seed)
                        .append(":").append(System.currentTimeMillis())
                        .append(":").append(rand.nextLong());
            sseed = sb.toString();
        }
        
        return new String(Base64.encode(iterativeHash(sseed.getBytes(), iterations, hash)));
    }
    
    /**
     * Generates randomized hash on base64 encoding from given seed
     * @param seed
     * @param randomized    if true, random number and current time in ms are appended to seed.
     * @return 
     */
    public static String generateMD5Hash(String seed, boolean randomized) throws NoSuchAlgorithmException{
        String sseed = seed;
        if (randomized) {
            Random rand = new SecureRandom();
            StringBuilder sb = new StringBuilder(seed)
                        .append(":").append(System.currentTimeMillis())
                        .append(":").append(rand.nextLong());
            sseed = sb.toString();
        }
        return generateMD5Hash(sseed.getBytes());
    }

    public static String generateMD5Hash(byte[] data) throws NoSuchAlgorithmException{
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("MD5");

        byte[] digest = sha.digest(data);
        return encodeHex(digest);
    }
    
    public static byte[] hash(String data2hash, String hashAlg) throws UnsupportedEncodingException, NoSuchAlgorithmException{
    	java.security.MessageDigest hasher = java.security.MessageDigest.getInstance(hashAlg);
        return hasher.digest(data2hash.getBytes("UTF-8"));
    }
    
    public static byte[] hash(byte[] data2hash, String hashAlg) throws UnsupportedEncodingException, NoSuchAlgorithmException{
    	java.security.MessageDigest hasher = java.security.MessageDigest.getInstance(hashAlg);
        return hasher.digest(data2hash);
    }
    
    public static byte[] hashSha256(String data2hash) throws UnsupportedEncodingException, NoSuchAlgorithmException{
    	return hash(data2hash, "SHA-256");
    }
    
    public static byte[] hashSha256(byte[] data2hash) throws UnsupportedEncodingException, NoSuchAlgorithmException{
    	return hash(data2hash, "SHA-256");
    }
    
    public static byte[] hashSha512(String data2hash) throws UnsupportedEncodingException, NoSuchAlgorithmException{
    	return hash(data2hash, "SHA-512");
    }
    
    public static byte[] hashSha512(byte[] data2hash) throws UnsupportedEncodingException, NoSuchAlgorithmException{
    	return hash(data2hash, "SHA-512");
    }
    
    /**
     * Hashes array of bytes by a given hash algorithm. Hash is in hexa string format.
     * 
     * @param data2hash
     * @param hashAlg
     * @return
     * @throws NoSuchAlgorithmException 
     */
    public static String hashHex(byte[] data2hash, String hashAlg) throws NoSuchAlgorithmException{
    	java.security.MessageDigest hasher = java.security.MessageDigest.getInstance(hashAlg);
        byte[] digest = hasher.digest(data2hash);
        return encodeHex(digest);
    }

    public static String hashHexSha1(String string2hash) throws NoSuchAlgorithmException{
        return hashHex(string2hash.getBytes(), "SHA-1");
    }

    /**
     * Hashes array of bytes by SHA-256. Hash is in hexa string format.
     * 
     * @param data2hash
     * @param hashAlg
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String hashHexSha256(byte[] data2hash) throws NoSuchAlgorithmException{
    	return hashHex(data2hash, "SHA-256");
    }
    
    /**
     * Hashes array of bytes by SHA-256. Hash is in hexa string format.
     * 
     * @param data2hash
     * @param hashAlg
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String hashHexSha512(byte[] data2hash) throws NoSuchAlgorithmException{
    	return hashHex(data2hash, "SHA-512");
    }
    
    /**
     * Returns simple SHA512 certificate digest
     * @param cert
     * @return 
     */
    public static String getCertificateDigest(byte[] encoded) throws NoSuchAlgorithmException, IOException{
    	return hashHexSha512(encoded);
    }
    
    /**
     * Returns simple SHA512 certificate digest
     * @param cert
     * @return 
     */
    public static String getCertificateDigest(X509Certificate cert) throws NoSuchAlgorithmException, IOException, CertificateEncodingException{
    	return getCertificateDigest(cert.getEncoded());
    }
    
    /**
     * Turns an array of bytes into a String representing each byte as an
     * unsigned hex number.
	 * 
     * @param bytes an array of bytes to convert to a hex-string
     * @return generated hex string
     */
    public static String encodeHex(byte[] bytes) {
        StringBuilder buf = new StringBuilder(bytes.length * 2);
        int i;

        for (i = 0; i < bytes.length; i++) {
            if (((int)bytes[i] & 0xff) < 0x10) {
                buf.append("0");
            }
            buf.append(Long.toString((int)bytes[i] & 0xff, 16));
        }
        return buf.toString();
    }

    /**
     * Turns a hex encoded string into a byte array. It is specifically meant
     * to "reverse" the toHex(byte[]) method.
     *
     * @param hex a hex encoded String to transform into a byte array.
     * @return a byte array representing the hex String[
     */
    public static byte[] decodeHex(String hex) {
        char[] chars = hex.toCharArray();
        byte[] bytes = new byte[chars.length / 2];
        int byteCount = 0;
        for (int i = 0; i < chars.length; i += 2) {
            int newByte = 0x00;
            newByte |= hexCharToByte(chars[i]);
            newByte <<= 4;
            newByte |= hexCharToByte(chars[i + 1]);
            bytes[byteCount] = (byte)newByte;
            byteCount++;
        }
        return bytes;
    }

    /**
     * Returns the the byte value of a hexadecmical char (0-f). It's assumed
     * that the hexidecimal chars are lower case as appropriate.
     *
     * @param ch a hexedicmal character (0-f)
     * @return the byte value of the character (0x00-0x0F)
     */
    private static byte hexCharToByte(char ch) {
        switch (ch) {
            case '0':
                return 0x00;
            case '1':
                return 0x01;
            case '2':
                return 0x02;
            case '3':
                return 0x03;
            case '4':
                return 0x04;
            case '5':
                return 0x05;
            case '6':
                return 0x06;
            case '7':
                return 0x07;
            case '8':
                return 0x08;
            case '9':
                return 0x09;
            case 'a':
                return 0x0A;
            case 'b':
                return 0x0B;
            case 'c':
                return 0x0C;
            case 'd':
                return 0x0D;
            case 'e':
                return 0x0E;
            case 'f':
                return 0x0F;
        }
        return 0x00;
    }
}
