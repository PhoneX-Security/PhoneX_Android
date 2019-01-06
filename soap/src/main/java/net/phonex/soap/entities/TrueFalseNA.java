package net.phonex.soap.entities;

public enum TrueFalseNA {

	TRUE("true"),
	FALSE("false"),
	NA("na");
	private final String value;

	TrueFalseNA(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static TrueFalseNA fromValue(String v) { 
		for (TrueFalseNA c: TrueFalseNA.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
