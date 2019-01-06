package net.phonex.soap.entities;

public enum ContactlistAction {

	ADD("add"),
	UPDATE("update"),
	REMOVE("remove"),
	ENABLE("enable"),
	DISABLE("disable"),
	NOTHING("nothing");
	private final String value;

	ContactlistAction(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static ContactlistAction fromValue(String v) { 
		for (ContactlistAction c: ContactlistAction.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
