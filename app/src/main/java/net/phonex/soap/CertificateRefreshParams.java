package net.phonex.soap;

public class CertificateRefreshParams {
	private String storagePass;
	private String sip;
	private boolean forceRecheck;
	private String existingCertHash2recheck;
	private boolean allowDhKeyRefreshOnCertChange=true;
	
	public String getStoragePass() {
		return storagePass;
	}
	public void setStoragePass(String storagePass) {
		this.storagePass = storagePass;
	}
	public String getSip() {
		return sip;
	}
	public void setSip(String sip) {
		this.sip = sip;
	}
	public boolean isForceRecheck() {
		return forceRecheck;
	}
	public void setForceRecheck(boolean forceRecheck) {
		this.forceRecheck = forceRecheck;
	}
	public String getExistingCertHash2recheck() {
		return existingCertHash2recheck;
	}
	public void setExistingCertHash2recheck(String existingCertHash2recheck) {
		this.existingCertHash2recheck = existingCertHash2recheck;
	}
	public boolean isAllowDhKeyRefreshOnCertChange() {
		return allowDhKeyRefreshOnCertChange;
	}
	public void setAllowDhKeyRefreshOnCertChange(
			boolean allowDhKeyRefreshOnCertChange) {
		this.allowDhKeyRefreshOnCertChange = allowDhKeyRefreshOnCertChange;
	}
}
