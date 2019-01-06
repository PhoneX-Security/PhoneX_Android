package net.phonex.soap;

import net.phonex.pub.parcels.StoredCredentials;

public interface IUserIdentityHolder {
	public UserPrivateCredentials getUserPrivateCredentials();
	public void setUserPrivateCredentials(UserPrivateCredentials userPrivateCredentials);

	public StoredCredentials getStoredCredentials();
	public void setStoredCredentials(StoredCredentials storedCredentials);
}
