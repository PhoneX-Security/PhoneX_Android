package net.phonex.soap.entities;

public enum EnabledDisabled {

	ENABLED("enabled"),
	DISABLED("disabled");
	private final String value;

	EnabledDisabled(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static EnabledDisabled fromValue(String v) { 
		for (EnabledDisabled c: EnabledDisabled.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
