package net.phonex.soap.entities;

public enum PairingRequestResolutionEnum {

	NONE("none"),
	ACCEPTED("accepted"),
	DENIED("denied"),
	BLOCKED("blocked"),
	REVERTED("reverted");
	private final String value;

	PairingRequestResolutionEnum(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static PairingRequestResolutionEnum fromValue(String v) { 
		for (PairingRequestResolutionEnum c: PairingRequestResolutionEnum.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
