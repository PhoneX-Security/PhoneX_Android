package net.phonex.soap.entities;

public enum WhitelistAction {

	ADD("add"),
	REMOVE("remove"),
	ENABLE("enable"),
	DISABLE("disable"),
	NOTHING("nothing");
	private final String value;

	WhitelistAction(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static WhitelistAction fromValue(String v) { 
		for (WhitelistAction c: WhitelistAction.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
