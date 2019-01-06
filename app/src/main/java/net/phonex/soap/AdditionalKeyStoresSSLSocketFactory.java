package net.phonex.soap;

import net.phonex.util.Log;
import net.phonex.util.crypto.pki.TrustVerifier;

import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


/**
 * Allows you to trust certificates from additional KeyStores in addition to the
 * default KeyStore.
 */
public class AdditionalKeyStoresSSLSocketFactory extends SSLSocketFactory {
	protected SSLContext sslContext = SSLContext.getInstance("TLS");

	public AdditionalKeyStoresSSLSocketFactory(KeyStore keyStore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
		super(null, null, null, null, null, null);
		sslContext.init(null, new TrustManager[] { new AdditionalKeyStoresTrustManager(keyStore) }, null);
	}
	
	@Override
	public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
		return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
	}

	@Override
	public Socket createSocket() throws IOException {
		return sslContext.getSocketFactory().createSocket();
	}

	/**
	 * Based on
	 * http://download.oracle.com/javase/1.5.0/docs/guide/security/jsse/
	 * JSSERefGuide.html#X509TrustManager.
	 * 
	 * Uses multiple KeyStores passed in constructor.
	 * Then it construct multiple X509TrustManager-s for certificate checking.
	 * 
	 * Passed certificate is valid if the whole chain is date-valid and each 
	 * certificate in the chain is valid in at least one X509TrustManager.
	 * 
	 * In reality it uses <tt>TrustVerifier</tt> for each KeyStore as X509TrustManager.
	 * 
	 * @see TrustVerifier
	 */
	public static class AdditionalKeyStoresTrustManager implements X509TrustManager {
		// list of loaded trust managers 
		protected ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<X509TrustManager>();
		// log tag
		public static final String TAG="AddTrustManager";

        /**
         * Construct X509TrustManager from passed trust stores.
         * @param additionaltrustStores
         */
		public AdditionalKeyStoresTrustManager(KeyStore... additionaltrustStores) {
			Log.vf(TAG, "Initializing trust managers, size=%d", additionaltrustStores!=null ? additionaltrustStores.length : 0);
			
			// list of trusted manager factories
			final ArrayList<TrustManagerFactory> factories = new ArrayList<TrustManagerFactory>();

			try {
				// The default Trustmanager with default keystore.
				for (KeyStore keyStore : additionaltrustStores) {
					//
					// Do not use TrustManagerFactory at the moment, use specialized TrustVerifier
					// for this purpose...
					//
					
					TrustVerifier tv = new TrustVerifier();
					tv.init(keyStore);
					
					x509TrustManagers.add((X509TrustManager) tv);
					Log.vf(TAG, "Trust manager added; %s", tv);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			/*
			 * Iterate over the returned trust managers, and hold on to any that
			 * are X509TrustManagers.
			 */
			for (TrustManagerFactory tmf : factories) {
				for (TrustManager tm : tmf.getTrustManagers()) {
					if (tm instanceof X509TrustManager) {
						x509TrustManagers.add((X509TrustManager) tm);
					}
				}
			}

			if (x509TrustManagers.size() == 0){
				throw new RuntimeException("Couldn't find any X509TrustManagers");
			}
		}

		/*
		 * Delegate to the default trust manager.
		 */
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			this.checkTrusted(chain, authType);
		}

		/*
		 * Loop over the trust managers until we find one that accepts our server
		 */
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			this.checkTrusted(chain, authType);
		}

		public X509Certificate[] getAcceptedIssuers() {
			final ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
			for (X509TrustManager tm : x509TrustManagers){
				list.addAll(Arrays.asList(tm.getAcceptedIssuers()));
			}
			return list.toArray(new X509Certificate[list.size()]);
		}
		
		/**
	     * Checks if all certificates in cert chain are valid and there is trusted element in chain.
	     * @param certs
	     * @param authType
	     * @throws CertificateException 
	     */
	    public void checkTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
	        if (certs == null || certs.length == 0) {
	            throw new IllegalArgumentException("null or zero-length certificate chain");
	        }
	        
	        // Check if every certificate in chain is still valid (meaning time). Every has to be valid!
	        // TODO: check Certificate Revocation List!
	        try {
	            for (X509Certificate cert : certs) {
	                cert.checkValidity();
	            }
	        } catch (Exception e) {
	            throw new CertificateException("Certificate not trusted. It has expired", e);
	        }
	        	        
	        // Use sub-ordinate trust managers to verify trust, at least one is enough.
	        for (X509TrustManager tm : x509TrustManagers) {
				try {
                    Log.vf(TAG, "testing against trust manager: %s authtype %s", tm, authType);
					tm.checkServerTrusted(certs, authType);
					return;
				} catch (CertificateException e) {
					// ignore
				}
			}

            Log.v(TAG, "No other trustmangers found, certificate is invalid");
	        // no valid certificate found -> throw exception
			throw new CertificateException();
	    }

        /**
         * Adds a new trust manager to the collection.
         * @param tm
         */
        public void addTrustManager(X509TrustManager tm){
            this.x509TrustManagers.add(tm);
        }
	}
}
