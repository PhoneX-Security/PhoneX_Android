package net.phonex.soap;

/**
 * Parameters for ContactlistFetchAsync task
 * @author ph4r05
 *
 */
public class ClistFetchParams {
	private String sip;
	private String serviceURL;
	
	// ID of user in database - used to link contact list entry to account
	private Integer dbId;
	
	// update contact list table from data fetched from 
	private UpdateStrategy clistTableUpdateStrategy = UpdateStrategy.DO_NOT_UPDATE;
	
	private String storagePass;
	
	
	public String getStoragePass() {
		return storagePass;
	}

	public void setStoragePass(String storagePass) {
		this.storagePass = storagePass;
	}

	public ClistFetchParams() {
		super();
	}
	
	public ClistFetchParams(String sip, Integer dbId) {
		super();
		this.sip = sip;
		this.dbId = dbId;
	}
	
	public String getSip() {
		return sip;
	}
	public void setSip(String sip) {
		this.sip = sip;
	}
	public Integer getDbId() {
		return dbId;
	}
	public void setDbId(Integer dbId) {
		this.dbId = dbId;
	}
	
	public String getServiceURL() {
		return serviceURL;
	}

	public void setServiceURL(String serviceURL) {
		this.serviceURL = serviceURL;
	}

    public UpdateStrategy getClistTableUpdateStrategy() {
        return clistTableUpdateStrategy;
    }

    public void setClistTableUpdateStrategy(UpdateStrategy clistTableUpdateStrategy) {
        this.clistTableUpdateStrategy = clistTableUpdateStrategy;
    }

    @Override
	public String toString() {
		return "ClistFetchParams [sip=" + sip + ", serviceURL=" + serviceURL
				+ ", dbId=" + dbId //+ ", updateClistTable=" + updateClistTable
				+ "]";
	}

    public enum UpdateStrategy{
        DROP_AND_UPDATE, UPDATE, DO_NOT_UPDATE    }

}
