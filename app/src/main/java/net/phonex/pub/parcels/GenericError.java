package net.phonex.pub.parcels;

public enum GenericError {
    NONE,					// No error
    GENERIC_ERROR,			// Generic error, error condition not covered by other cases.
    CANCELLED,		        // Cancelled.
    TIMEOUT,				// Operation timeouted (e.g., connection).
}
