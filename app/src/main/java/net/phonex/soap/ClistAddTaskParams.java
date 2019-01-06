package net.phonex.soap;

import net.phonex.db.entity.SipClist;

/**
 * Parameters for add to contact list task
 * 
 * @author ph4r05
 */
public class ClistAddTaskParams {
	protected String userName;
	protected String diplayName;
    protected String storagePass;
    protected boolean loadIdentity;
    protected GenKeyParams genKeyParams;

    public ClistAddTaskParams() {
    }

    public ClistAddTaskParams(SipClist profile) {
        setUserName(profile.getSip());
        setDiplayName(profile.getDisplayName());
    }

    public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getDiplayName() {
		return diplayName;
	}
	public void setDiplayName(String diplayName) {
		this.diplayName = diplayName;
	}
	public String getStoragePass() {
		return storagePass;
	}
	public void setStoragePass(String storagePass) {
		this.storagePass = storagePass;
	}
	public GenKeyParams getGenKeyParams() {
		return genKeyParams;
	}
	public void setGenKeyParams(GenKeyParams genKeyParams) {
		this.genKeyParams = genKeyParams;
	}
	public boolean isLoadIdentity() {
		return loadIdentity;
	}
	public void setLoadIdentity(boolean loadIdentity) {
		this.loadIdentity = loadIdentity;
	}
}
