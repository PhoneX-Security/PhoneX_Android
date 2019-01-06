package net.phonex.soap.entities;

public enum UserPresenceStatus {

	ONLINE("online"),
	OFFLINE("offline"),
	AWAY("away"),
	DND("dnd"),
	INVISIBLE("invisible");
	private final String value;

	UserPresenceStatus(String v) { 
		value=v;
	}

	public String value() {
		return value;
	}

	public static UserPresenceStatus fromValue(String v) { 
		for (UserPresenceStatus c: UserPresenceStatus.values()) { 
            	if (c.value.equals(v)) { 
                		return c; 
            } 
        } 
        throw new IllegalArgumentException(v); 
    } 
} 
