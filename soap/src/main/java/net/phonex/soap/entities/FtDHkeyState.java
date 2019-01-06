package net.phonex.soap.entities;

public enum FtDHkeyState {

	READY("ready"),
	USED("used"),
	EXPIRED("expired"),
	UPLOADED("uploaded");
	private final String value;

	FtDHkeyState(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static FtDHkeyState fromValue(String v) { 
		for (FtDHkeyState c: FtDHkeyState.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
