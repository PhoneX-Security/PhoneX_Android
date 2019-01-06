package net.phonex.sip;

/**
 * ZRTP constants - info messages, state names.
 * Has to be consistent with ZrtpCWrapper.h
 * 
 * Later it may be transformed to resources - using internationalized strings.
 * 
 * @author ph4r05
 *
 */
public class ZrtpConstants {
	
	/* The ZRTP protocol states */
	public static final String[] zrtpStatesName = 
	{
	    "Initial",            /*!< Initial state after starting the state engine */
	    "Detect",             /*!< State sending Hello, try to detect answer message */
	    "AckDetected",        /*!< HelloAck received */
	    "AckSent",            /*!< HelloAck sent after Hello received */
	    "WaitCommit",         /*!< Wait for a Commit message */
	    "CommitSent",         /*!< Commit message sent */
	    "WaitDHPart2",        /*!< Wait for a DHPart2 message */
	    "WaitConfirm1",       /*!< Wait for a Confirm1 message */
	    "WaitConfirm2",       /*!< Wait for a confirm2 message */
	    "WaitConfAck",        /*!< Wait for Conf2Ack */
	    "WaitClearAck",       /*!< Wait for clearAck - not used */
	    "SecureState",        /*!< This is the secure state - SRTP active */
	    "WaitErrorAck",       /*!< Wait for ErrorAck message */
	    "numberOfStates"      /*!< Gives total number of protocol states */
	};

	public static final String[] infoCodes =
	{
	    "EMPTY",
	    "Hello received, preparing a Commit",
	    "Commit: Generated a public DH key",
	    "Responder: Commit received, preparing DHPart1",
	    "DH1Part: Generated a public DH key",
	    "Initiator: DHPart1 received, preparing DHPart2",
	    "Responder: DHPart2 received, preparing Confirm1",
	    "Initiator: Confirm1 received, preparing Confirm2",
	    "Responder: Confirm2 received, preparing Conf2Ack",
	    "At least one retained secrets matches - security OK",
	    "Entered secure state",
	    "No more security for this session"
	};

	/**
	 * Sub-codes for Warning
	 */
	public static final String[] warningCodes =
	{
	    "EMPTY",
	    "Commit contains an AES256 cipher but does not offer a Diffie-Helman 4096",
	    "Received a GoClear message",
	    "Hello offers an AES256 cipher but does not offer a Diffie-Helman 4096",
	    "No retained shared secrets available - must verify SAS",
	    "Internal ZRTP packet checksum mismatch - packet dropped",
	    "Dropping packet because SRTP authentication failed!",
	    "Dropping packet because SRTP replay check failed!",
	    "Valid retained shared secrets available but no matches found - must verify SAS"
	};

	/**
	 * Sub-codes for Severe
	 */
	public static final String[] severeCodes =
	{
	    "EMPTY",
	    "Hash HMAC check of Hello failed!",
	    "Hash HMAC check of Commit failed!",
	    "Hash HMAC check of DHPart1 failed!",
	    "Hash HMAC check of DHPart2 failed!",
	    "Cannot send data - connection or peer down?",
	    "Internal protocol error occurred!",
	    "Cannot start a timer - internal resources exhausted?",
	    "Too much retries during ZRTP negotiation - connection or peer down?"
	};
	
	public static final String[] severity = 
	{
		"Unknown",
	    "Info",                      /*!< Just an info message */
	    "Warning",                   /*!< A Warning message - security can be established */
	    "Severe",                    /*!< Severe error, security will not be established */
	    "Error"                      /*!< ZRTP error, security will not be established  */
	};
	
	/**
	 * Returns name of ZRTP message severity
	 */
	public static String getSeverity(int i){
		if (i<0 || i>=severity.length) return "N/A";
		return severity[i];
	}
	
	public static String getStateName(int i){
		if (i<0 || i>=zrtpStatesName.length) return "N/A";
		return zrtpStatesName[i];
	}
	
	public static String getInfoCode(int i){
		if (i<0 || i>=infoCodes.length) return "N/A";
		return infoCodes[i];
	}
	
	public static String getWarningCode(int i){
		if (i<0 || i>=warningCodes.length) return "N/A";
		return warningCodes[i];
	}
	
	public static String getSevereCode(int i){
		if (i<0 || i>=severeCodes.length) return "N/A";
		return severeCodes[i];
	}
	
	public static String getErrorCode(int i){
		if (i<0) return "N/A";
		
		// REGEX converting ZrtpCWrap to switch:
		// ([a-zA-Z_0-9]+)\s*=\s*([0-9xa-fA-f]+)\s*,?\s*/\*!<\s*(.*)\s*\*/$
		// Replace: case $2: return "$1 [$3]"; /*!< $3 */ 
		switch(i){
			default:   return "N/A";
			case 0x10: return "zrtp_MalformedPacket [Malformed packet (CRC OK, but wrong structure)]"; 	/*!< Malformed packet (CRC OK, but wrong structure)  */
		    case 0x20: return "zrtp_CriticalSWError [Critical software error]";							/*!< Critical software error  */
		    case 0x30: return "zrtp_UnsuppZRTPVersion [Unsupported ZRTP version]"; 						/*!< Unsupported ZRTP version  */
		    case 0x40: return "zrtp_HelloCompMismatch [Hello components mismatch]"; 					/*!< Hello components mismatch  */
		    case 0x51: return "zrtp_UnsuppHashType [Hash type not supported]";							/*!< Hash type not supported  */
		    case 0x52: return "zrtp_UnsuppCiphertype [Cipher type not supported]"; 						/*!< Cipher type not supported  */
		    case 0x53: return "zrtp_UnsuppPKExchange [Public key exchange not supported]"; 				/*!< Public key exchange not supported  */
		    case 0x54: return "zrtp_UnsuppSRTPAuthTag [SRTP auth. tag not supported]"; 					/*!< SRTP auth. tag not supported  */
		    case 0x55: return "zrtp_UnsuppSASScheme [SAS scheme not supported]"; 						/*!< SAS scheme not supported  */
		    case 0x56: return "zrtp_NoSharedSecret [No shared secret available, DH mode required]"; 	/*!< No shared secret available, DH mode required  */
		    case 0x61: return "zrtp_DHErrorWrongPV [DH Error: bad pvi or pvr ( == 1, 0, or p-1)]"; 		/*!< DH Error: bad pvi or pvr ( == 1, 0, or p-1)  */
		    case 0x62: return "zrtp_DHErrorWrongHVI [DH Error: hvi != hashed data]"; 					/*!< DH Error: hvi != hashed data  */
		    case 0x63: return "zrtp_SASuntrustedMiTM [Received relayed SAS from untrusted MiTM]"; 		/*!< Received relayed SAS from untrusted MiTM  */
		    case 0x70: return "zrtp_ConfirmHMACWrong [Auth. Error: Bad Confirm pkt HMAC]"; 				/*!< Auth. Error: Bad Confirm pkt HMAC  */
		    case 0x80: return "zrtp_NonceReused [Nonce reuse]"; 										/*!< Nonce reuse  */
		    case 0x90: return "zrtp_EqualZIDHello [Equal ZIDs in Hello]"; 								/*!< Equal ZIDs in Hello  */
		    case 0x100: return "zrtp_GoCleatNotAllowed [GoClear packet received, but not allowed]"; 	/*!< GoClear packet received, but not allowed  */
		    case 0x7fffffff: return "zrtp_IgnorePacket [Internal state, not reported]"; 				/*!< Internal state, not reported  */
		}	
	}
	
	/**
	 * Returns string for given ZRTP message.
	 * @param severity
	 * @param subcode
	 * @return
	 */
	public static String getReportCode(int severity, int subcode){
		switch (severity){
			default: return "N/A";
			case 1:  return getInfoCode(subcode);
			case 2:  return getWarningCode(subcode);
			case 3:  return getSevereCode(subcode);
			case 4:  return getErrorCode(subcode);
		}
	}
}
