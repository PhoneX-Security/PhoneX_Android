package net.phonex.soap;

import net.phonex.util.crypto.MessageDigest;

import java.security.NoSuchAlgorithmException;


public class AuthHashGenerator {
	
	/**
	 * Generates HA1 password field from user SIP and password
	 * @param sip
	 * @param password
	 * @return
	 * @throws NoSuchAlgorithmException 
	 */
	public static String getHA1(String sip, String password) throws NoSuchAlgorithmException{
		// split sip by @
		String arr[] = sip.split("@", 2);
		if (arr==null || arr.length!=2) {
			throw new IllegalArgumentException("Invalid SIP format");
		}
		
		return getHA1(arr[0], arr[1], password);
	}
	
	public static String getHA1(String username, String domain, String password) throws NoSuchAlgorithmException{
		StringBuilder sb = new StringBuilder();
		sb.append(username).append(":")
			.append(domain).append(":")
			.append(password);
		
		return MessageDigest.generateMD5Hash(sb.toString(), false);
	}
	
	/**
     * Generates string base for encryption and auth token
     * 
     * @param sip
     * @param ha1
     * @param usrToken
     * @param serverToken
     * @param milliWindow
     * @param offset
     * @return
     * @throws NoSuchAlgorithmException 
     */
    public static String generateUserTokenBase(String sip, String ha1, String usrToken, String serverToken, long milliWindow, int offset) {
        // determine current time window
        long curTime = System.currentTimeMillis();
        long curTimeSlot = ((long) Math.floor(curTime / (double)milliWindow)) + offset;
        StringBuilder sb = new StringBuilder()
                .append(sip).append(':')
                .append(ha1).append(':')
                .append(usrToken).append(':')
                .append(serverToken).append(':')
                .append(curTimeSlot).append(':');
        return sb.toString();
    }
    
    /**
     * Generates user auth token for defined set of parameters.
     * Method does not use database.
     * @param sip               user sip
     * @param ha1               ha1 field from database
     * @param usrToken  
     * @param serverToken       
     * @param milliWindow       length of one time slot
     * @param offset            time window offset from NOW
     * @return 
     */
    public static String generateUserAuthToken(String sip, String ha1, String usrToken, String serverToken, long milliWindow, int offset) throws NoSuchAlgorithmException{
        // determine current time window
        String base = generateUserTokenBase(sip, ha1, usrToken, serverToken, milliWindow, offset);
        return MessageDigest.generateHash(base+"PHOENIX_AUTH", false, 3779);
    }
    
      /**
     * Generates user encryption token for defined set of parameters.
     * Method does not use database.
     * @param sip               user sip
     * @param ha1               ha1 field from database
     * @param usrToken  
     * @param serverToken       
     * @param milliWindow       length of one time slot
     * @param offset            time window offset from NOW
     * @return 
     */
    public static String generateUserEncToken(String sip, String ha1, String usrToken, String serverToken, long milliWindow, int offset) throws NoSuchAlgorithmException{
        // determine current time window
        String base = generateUserTokenBase(sip, ha1, usrToken, serverToken, milliWindow, offset);
        return MessageDigest.generateHash(base+"PHOENIX_ENC", false, 11);
    }
}
