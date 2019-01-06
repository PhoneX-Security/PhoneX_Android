package net.phonex.soap;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * Private credentials such as private key, keystore and so on...
 * @author ph4r05
 */
public class UserPrivateCredentials {
	// stores certificate information if there is any
	public PrivateKey pk=null;
	public KeyStore ks=null;
	public char[] pkKey=null;
	public Certificate[] certChain=null;
	public X509Certificate cert=null;
	
	@Override
	public String toString() {
		return "UserPrivateCredentials [pk=" + pk + ", ks=" + ks + ", pkKey="
				+ Arrays.toString(pkKey) + ", certChain="
				+ Arrays.toString(certChain) + ", cert=" + cert + "]";
	}
	public PrivateKey getPk() {
		return pk;
	}
	public void setPk(PrivateKey pk) {
		this.pk = pk;
	}
	public KeyStore getKs() {
		return ks;
	}
	public void setKs(KeyStore ks) {
		this.ks = ks;
	}
	public char[] getPkKey() {
		return pkKey;
	}
	public void setPkKey(char[] pkKey) {
		this.pkKey = pkKey;
	}
	public Certificate[] getCertChain() {
		return certChain;
	}
	public void setCertChain(Certificate[] certChain) {
		this.certChain = certChain;
	}
	public X509Certificate getCert() {
		return cert;
	}
	public void setCert(X509Certificate cert) {
		this.cert = cert;
	}	
}
