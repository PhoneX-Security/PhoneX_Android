package net.phonex.soap.entities;

public enum UserWhitelistStatus {

	IN("in"),
	NOTIN("notin"),
	DISABLED("disabled"),
	NOCLUE("noclue");
	private final String value;

	UserWhitelistStatus(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static UserWhitelistStatus fromValue(String v) { 
		for (UserWhitelistStatus c: UserWhitelistStatus.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
