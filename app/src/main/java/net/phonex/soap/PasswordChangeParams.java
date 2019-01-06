package net.phonex.soap;

import java.io.Serializable;

/**
 * Parameters for Certificate signing async task
 * 
 * @author ph4r05
 */
public class PasswordChangeParams implements Serializable {
	private String userSIP;
	private String targetUserSIP;
	private String serviceURL;
	private String storagePass;
	private String xmppPass;
	private String userOldPass;
	private String userNewPass;
	private String pemPass;
	private boolean derivePasswords=false;
	private boolean rekeyKeyStore=false;
	private boolean rekeyDB=false;

	public String getUserSIP() {
		return userSIP;
	}

	public void setUserSIP(String userSIP) {
		this.userSIP = userSIP;
	}

	public String getTargetUserSIP() {
		return targetUserSIP;
	}

	public void setTargetUserSIP(String targetUserSIP) {
		this.targetUserSIP = targetUserSIP;
	}

	public String getServiceURL() {
		return serviceURL;
	}

	public void setServiceURL(String serviceURL) {
		this.serviceURL = serviceURL;
	}

	public String getStoragePass() {
		return storagePass;
	}

	public void setStoragePass(String storagePass) {
		this.storagePass = storagePass;
	}

	public String getUserOldPass() {
		return userOldPass;
	}

	public void setUserOldPass(String userOldPass) {
		this.userOldPass = userOldPass;
	}

	public String getUserNewPass() {
		return userNewPass;
	}

	public void setUserNewPass(String userNewPass) {
		this.userNewPass = userNewPass;
	}

	public String getPemPass() {
		return pemPass;
	}

	public void setPemPass(String pemPass) {
		this.pemPass = pemPass;
	}

	public boolean isDerivePasswords() {
		return derivePasswords;
	}

	public void setDerivePasswords(boolean derivePasswords) {
		this.derivePasswords = derivePasswords;
	}

	public boolean isRekeyKeyStore() {
		return rekeyKeyStore;
	}

	public void setRekeyKeyStore(boolean rekeyKeyStore) {
		this.rekeyKeyStore = rekeyKeyStore;
		if (rekeyKeyStore){
			setDerivePasswords(true);
		}
	}

	public boolean isRekeyDB() {
		return rekeyDB;
	}

	public void setRekeyDB(boolean rekeyDB) {
		this.rekeyDB = rekeyDB;
	}

	public String getXmppPass() {
		return xmppPass;
	}

	public void setXmppPass(String xmppPass) {
		this.xmppPass = xmppPass;
	}
	
}
