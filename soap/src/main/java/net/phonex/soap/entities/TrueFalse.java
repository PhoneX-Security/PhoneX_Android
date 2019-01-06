package net.phonex.soap.entities;

public enum TrueFalse {

	TRUE("true"),
	FALSE("false");
	private final String value;

	TrueFalse(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static TrueFalse fromValue(String v) { 
		for (TrueFalse c: TrueFalse.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
