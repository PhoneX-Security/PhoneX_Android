package net.phonex.pub.parcels;

/**
 * Possible error states that can occur in a file-transfer process.
 * @author ph4r05
 */
public enum FileTransferError {
    NONE,					// No error
    GENERIC_ERROR,			// Generic error, error condition not covered by other cases.
    BAD_RESPONSE, 			// Bad response from SOAP server.
    CERTIFICATE_MISSING, 	// A needed certificate is missing / was not found.
    SECURITY_ERROR, 		// Critical security error in a protocol (i.e., signature mismatch).
    DHKEY_MISSING,			// A specified DH key was not found (i.e., in database, on server).
    DOWN_NO_SUCH_FILE_FOR_NONCE, // A file for a given nonce does not exist.
    DOWN_DOWNLOAD_ERROR, 	// Unspecified error in downloading process (i.e., connection was lost).
    DOWN_DECRYPTION_ERROR,	// Unspecified error during decryption, file may be malformed.
    UPD_UPLOAD_ERROR, 		// Unspecified error in uploading process (i.e., connection was lost).
    UPD_ENCRYPTION_ERROR,	// Unspecified error during encryption.
    UPD_NO_AVAILABLE_DHKEYS,// Destination user has no available DH keys.
    UPD_QUOTA_EXCEEDED,		// Destination user has full mailbox.
    UPD_FILE_TOO_BIG,		// Uploaded file is too big to be uploaded.
    CANCELLED,		        // Cancelled.
    TIMEOUT,				// Operation timeouted (e.g., connection).
}
