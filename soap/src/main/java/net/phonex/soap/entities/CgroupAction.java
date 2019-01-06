package net.phonex.soap.entities;

public enum CgroupAction {

	ADD("add"),
	UPDATE("update"),
	REMOVE("remove");
	private final String value;

	CgroupAction(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static CgroupAction fromValue(String v) { 
		for (CgroupAction c: CgroupAction.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
