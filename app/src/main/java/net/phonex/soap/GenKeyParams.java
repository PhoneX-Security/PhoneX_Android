package net.phonex.soap;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Class for passing parameters to this task.
 * @author ph4r05
 */
public class GenKeyParams{
	
	/**
	 * My SIP.
	 */
	private String mySip;
	
	/**
	 * Storage password for unlocking priv key for HTTPS.
	 */
	private String storagePass;
	
	/**
	 * Private key to generate signature with
	 */
	private PrivateKey privKey;
	
	/**
	 * My certificate (included in signatures).
	 */
	private X509Certificate myCert;
	
	/**
	 * List of users to generate keys for.
	 */
	private List<GenKeyForUser> userList;
	
	/**
	 * List of nonce2s to delete from the server.
	 */
	private List<String> deleteNonce2List;

	/**
	 * Default number of keys to add.
	 */
	private int numKeys;
	
	/**
	 * @return the storagePass
	 */
	public String getStoragePass() {
		return storagePass;
	}

	/**
	 * @param storagePass the storagePass to set
	 */
	public void setStoragePass(String storagePass) {
		this.storagePass = storagePass;
	}

	/**
	 * @return the privKey
	 */
	public PrivateKey getPrivKey() {
		return privKey;
	}

	/**
	 * @param privKey the privKey to set
	 */
	public void setPrivKey(PrivateKey privKey) {
		this.privKey = privKey;
	}

	/**
	 * @return the myCert
	 */
	public X509Certificate getMyCert() {
		return myCert;
	}

	/**
	 * @param myCert the myCert to set
	 */
	public void setMyCert(X509Certificate myCert) {
		this.myCert = myCert;
	}
	
	/**
	 * @return the mySip
	 */
	public String getMySip() {
		return mySip;
	}

	/**
	 * @param mySip the mySip to set
	 */
	public void setMySip(String mySip) {
		this.mySip = mySip;
	}
	
	/**
	 * @return the numKeys
	 */
	public int getNumKeys() {
		return numKeys;
	}

	/**
	 * @param numKeys the numKeys to set
	 */
	public void setNumKeys(int numKeys) {
		this.numKeys = numKeys;
	}

	/**
	 * @return the userList
	 */
	public List<GenKeyForUser> getUserList() {
		return userList;
	}

	/**
	 * @param userList the userList to set
	 */
	public void setUserList(List<GenKeyForUser> userList) {
		this.userList = userList;
	}
	
	/**
	 * @return the deleteNonce2List
	 */
	public List<String> getDeleteNonce2List() {
		return deleteNonce2List;
	}

	/**
	 * @param deleteNonce2List the deleteNonce2List to set
	 */
	public void setDeleteNonce2List(List<String> deleteNonce2List) {
		this.deleteNonce2List = deleteNonce2List;
	}



	/**
	 * Contains information for one user to generate keys for.
	 * 
	 * @author ph4r05
	 */
	public static class GenKeyForUser{
		/**
		 * User for which to generate DHkeys.
		 */
		private String userSip;
		
		/**
		 * User certificate (included in signatures).
		 */
		private X509Certificate userCert;
		
		/**
		 * Number of DH keys to generate for particular user.
		 */
		private int numKeys;
		
		/**
		 * @return the userSip
		 */
		public String getUserSip() {
			return userSip;
		}
		/**
		 * @param userSip the userSip to set
		 */
		public void setUserSip(String userSip) {
			this.userSip = userSip;
		}
		/**
		 * @return the userCert
		 */
		public X509Certificate getUserCert() {
			return userCert;
		}
		/**
		 * @param userCert the userCert to set
		 */
		public void setUserCert(X509Certificate userCert) {
			this.userCert = userCert;
		}
		/**
		 * @return the numKeys
		 */
		public int getNumKeys() {
			return numKeys;
		}
		/**
		 * @param numKeys the numKeys to set
		 */
		public void setNumKeys(int numKeys) {
			this.numKeys = numKeys;
		}
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + numKeys;
			result = prime * result
					+ ((userCert == null) ? 0 : userCert.hashCode());
			result = prime * result
					+ ((userSip == null) ? 0 : userSip.hashCode());
			return result;
		}
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			GenKeyForUser other = (GenKeyForUser) obj;
			if (numKeys != other.numKeys)
				return false;
			if (userCert == null) {
				if (other.userCert != null)
					return false;
			} else if (!userCert.equals(other.userCert))
				return false;
			if (userSip == null) {
				if (other.userSip != null)
					return false;
			} else if (!userSip.equals(other.userSip))
				return false;
			return true;
		}
	}
}