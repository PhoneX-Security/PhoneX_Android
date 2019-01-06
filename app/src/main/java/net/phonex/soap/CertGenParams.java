package net.phonex.soap;

/**
 * Parameters for Certificate signing async task
 * 
 * @author ph4r05
 */
public class CertGenParams {
	private String userSIP;
	private String serviceURL;
	private String storagePass;
	private String pemPass;
	private String userPass;
	private String userDomain;
	private String xmppPass;
	private boolean certificateJustCreated=false;
	private boolean removeDHKeys=false;
	
	// store certificate to certificate holder if everything is OK? (for testing)
	private boolean storeResult=true;
	
	// user credentials - for SOAP connection for example
	private UserPrivateCredentials privateCredentials=null;
	
	public String getUserSIP() {
		return userSIP;
	}
	public void setUserSIP(String userSIP) {
		this.userSIP = userSIP;
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
	public String getUserPass() {
		return userPass;
	}
	public void setUserPass(String userPass) {
		this.userPass = userPass;
	}
	public boolean isStoreResult() {
		return storeResult;
	}

	public UserPrivateCredentials getPrivateCredentials() {
		return privateCredentials;
	}
	public void setPrivateCredentials(UserPrivateCredentials privateCredentials) {
		this.privateCredentials = privateCredentials;
	}
	public String getUserDomain() {
		return userDomain;
	}
	public void setUserDomain(String userDomain) {
		this.userDomain = userDomain;
	}
	public boolean isCertificateJustCreated() {
		return certificateJustCreated;
	}
	public void setCertificateJustCreated(boolean certificateJustCreated) {
		this.certificateJustCreated = certificateJustCreated;
	}
	public String getPemPass() {
		return pemPass;
	}
	public void setPemPass(String pemPass) {
		this.pemPass = pemPass;
	}
	public boolean isRemoveDHKeys() {
		return removeDHKeys;
	}
	public void setRemoveDHKeys(boolean removeDHKeys) {
		this.removeDHKeys = removeDHKeys;
	}
	public String getXmppPass() {
		return xmppPass;
	}
	public void setXmppPass(String xmppPass) {
		this.xmppPass = xmppPass;
	}
}
