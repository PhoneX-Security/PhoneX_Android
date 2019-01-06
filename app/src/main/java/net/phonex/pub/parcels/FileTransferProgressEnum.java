package net.phonex.pub.parcels;

/**
 * Progress states for file transfer.
 */
public enum FileTransferProgressEnum {
    CANCELLED,            // Operation was cancelled.
    ERROR,                  // Operation finished with error.
    DONE,                   // Process finished.
    IN_QUEUE,               // Request is enqueued and waiting to be processed.
    INITIALIZATION,         // Process has started.
    COMPUTING_ENC_KEYS,     // Computing crypto keys, DH exchange.

    // Download specific.
    RETRIEVING_FILE,        // Download has been started.
    DELETING_FROM_SERVER,   // Remote cleanup.
    DELETED_FROM_SERVER,    // Remote cleanup finished.
    LOADING_INFORMATION,    // Loading information from server.
    DOWNLOADING,            // Download process.
    LOADING_PRIVATE_FILES,  // Loading private files.
    CONNECTING_TO_SERVER,   // Connecting to the server.
    FILE_EXTRACTION,        // ZIP extraction started.
    DECRYPTING_FILES,       // Decryption process has started.

    // Upload specific.
    DOWNLOADING_KEYS,       // DH key download.
    KEY_VERIFICATION,       // DH key verification.
    UPLOADING,              // Uploading.
    ENCRYPTING_FILES,       // DH key verification.
    SENDING_NOTIFICATION,   // Sending upload notification.
}
