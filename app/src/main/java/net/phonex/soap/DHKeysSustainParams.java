package net.phonex.soap;

import net.phonex.pub.parcels.StoredCredentials;

public class DHKeysSustainParams {
	// ID of user in database - used to link contact list entry to account
	private Integer dbId;
	
	// Number of DHkeys to keep for each user.
	private int dhkeys;
	
	// If true expiration check of the keys will be performed.
	private boolean expireKeys;
	
	// Stored credentials, needed for SSL.
	private StoredCredentials creds;

	/**
	 * @return the dbId
	 */
	public Integer getDbId() {
		return dbId;
	}

	/**
	 * @param dbId the dbId to set
	 */
	public void setDbId(Integer dbId) {
		this.dbId = dbId;
	}

	/**
	 * @return the creds
	 */
	public StoredCredentials getCreds() {
		return creds;
	}

	/**
	 * @param creds the creds to set
	 */
	public void setCreds(StoredCredentials creds) {
		this.creds = creds;
	}

	/**
	 * @return the dhkeys
	 */
	public int getDhkeys() {
		return dhkeys;
	}

	/**
	 * @param dhkeys the dhkeys to set
	 */
	public void setDhkeys(int dhkeys) {
		this.dhkeys = dhkeys;
	}

	/**
	 * @return the expireKeys
	 */
	public boolean isExpireKeys() {
		return expireKeys;
	}

	/**
	 * @param expireKeys the expireKeys to set
	 */
	public void setExpireKeys(boolean expireKeys) {
		this.expireKeys = expireKeys;
	}
	
	
}
