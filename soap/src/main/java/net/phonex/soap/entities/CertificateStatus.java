package net.phonex.soap.entities;

public enum CertificateStatus {

	OK("ok"),
	INVALID("invalid"),
	REVOKED("revoked"),
	FORBIDDEN("forbidden"),
	MISSING("missing"),
	NOUSER("nouser");
	private final String value;

	CertificateStatus(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static CertificateStatus fromValue(String v) { 
		for (CertificateStatus c: CertificateStatus.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
