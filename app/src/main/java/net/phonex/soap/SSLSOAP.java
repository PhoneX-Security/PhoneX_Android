package net.phonex.soap;

import android.content.Context;

import net.phonex.R;
import net.phonex.pub.a.Compatibility;
import net.phonex.soap.AdditionalKeyStoresSSLSocketFactory.AdditionalKeyStoresTrustManager;
import net.phonex.util.Log;
import net.phonex.util.crypto.pki.TrustVerifier;

import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


public class SSLSOAP {
	// logging tag
	public static final String TAG = "SSLSOAP";
	private static final String KEY_STORE_PASS = "aeshizoo6Zee";
	
	/**
	 * AES TLS ciphersuites considered as secure.
	 * Only CS with perfect forward secrecy.
	 */
	private static final String[] AES_CIPHERSUITES = {
		"TLS_DH_RSA_WITH_AES_128_GCM_SHA256",
		"TLS_DH_RSA_WITH_AES_256_GCM_SHA384",
		"TLS_DH_DSS_WITH_AES_128_GCM_SHA256",
		"TLS_DH_DSS_WITH_AES_128_GCM_SHA384",
		"TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
		"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
		"TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
		"TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
		"TLS_DH_RSA_WITH_AES_256_CBC_SHA256",
		"TLS_DH_DSS_WITH_AES_256_CBC_SHA256",
		"TLS_DH_RSA_WITH_AES_128_CBC_SHA",
		"TLS_DH_DSS_WITH_AES_128_CBC_SHA",
		"TLS_DH_RSA_WITH_AES_256_CBC_SHA",
		"TLS_DH_DSS_WITH_AES_256_CBC_SHA",
		"TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
		"TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
		"TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
		"TLS_DHE_DSS_WITH_AES_256_CBC_SHA"};
	
	public static final String TLSv1 = "TLSv1";
	public static final String TLSv11 = "TLSv1.1";
	public static final String TLSv12 = "TLSv1.2";
	
	/**
	 * Secure TLS protocols.
	 * WARNING! TLSv1 is not secure with CBC ciphers... BEAST attack.
	 */
	private static final String[] SECURE_PROTOCOLS = {
		TLSv12, TLSv11, TLSv1
	};
	
	/**
	 * Apache socket factory, used for scheme register.
	 * 
	 * @param context
	 * @return
	 */
	public static org.apache.http.conn.ssl.SSLSocketFactory createAdditionalCertsSSLSocketFactory(Context context) {
	    try {
	        final KeyStore ks = loadTrustStore(context);
	        return new AdditionalKeyStoresSSLSocketFactory(ks);
	    } catch(Exception e) {
	        throw new RuntimeException(e);
	    }
	}

    /**
     * Apache socket factory, used for scheme register.
     *
     * @param context
     * @return
     */
    public static org.apache.http.conn.ssl.SSLSocketFactory createAdditionalWebCertsSSLSocketFactory(Context context) {
        try {
            final KeyStore ks = loadWebTrustStore(context);
            return new AdditionalKeyStoresSSLSocketFactory(ks);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
	
	/**
	 * Generates Java SSLSocketFactory.
	 * 
	 * @deprecated
	 * @return
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 * @throws IOException 
	 * @throws CertificateException 
	 */
	protected static SSLSocketFactory getSSLSocketFactory(Context context) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, CertificateException, IOException {
		final KeyStore ks = loadTrustStore(context);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext sslcontext = SSLContext.getInstance("SSL");
        sslcontext.init(null, tmf.getTrustManagers(), null);
        return sslcontext.getSocketFactory();
	}
	
	/**
	 * Loads trust store (list of trusted CAs) stored as resource in the application.
	 * 
	 * @param context
	 * @return
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public static KeyStore loadTrustStore(Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		final KeyStore ks = KeyStore.getInstance("BKS");
        
        // the bks file we generated above
        final InputStream in = context.getResources().openRawResource(R.raw.truststore);  
        try {
            ks.load(in, KEY_STORE_PASS.toCharArray());
        } catch(Exception e){
        	Log.e(TAG, "Cannot load TrustStore", e);
        }	
        finally {
            in.close();
        }
        
        return ks;
	}

    /**
     * Loads trust store (list of trusted CAs) stored as resource in the application for web page.
     *
     * @param context
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     */
    public static KeyStore loadWebTrustStore(Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
        final KeyStore ks = KeyStore.getInstance("BKS");

        // the bks file we generated above
        final InputStream in = context.getResources().openRawResource(R.raw.truststore_web);
        try {
            ks.load(in, KEY_STORE_PASS.toCharArray());
        } catch(Exception e){
            Log.e(TAG, "Cannot load TrustStore", e);
        }
        finally {
            in.close();
        }

        return ks;
    }

	/**
	 * Generates X509TrustManager based on our trust store stored in the application.
	 * 
	 * @param context
	 * @return
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 */
	public static X509TrustManager getTrustManager(Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		final KeyStore ks = loadTrustStore(context);
		return new AdditionalKeyStoresTrustManager(ks);
	}

    /**
     * Generates X509TrustManager based on our trust store stored in the application.
     * Used for web.
     *
     * @param context
     * @return
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public static AdditionalKeyStoresTrustManager getWebTrustManager(Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
        final KeyStore ks = loadWebTrustStore(context);
        return new AdditionalKeyStoresTrustManager(ks);
    }

    /**
     * Tries to load default system trust manager with system CA list.
     *
     * @param context
     * @return
     */
    public static X509TrustManager getSystemTrustManager(Context context) {
        try {

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            // Use default platform set of trusted CAs
            tmf.init((KeyStore) null);
            TrustManager[] tms = tmf.getTrustManagers();
            if (tms == null || tms.length == 0) {
                return null;
            }

            for (TrustManager ts : tms) {
                if (ts != null && (ts instanceof X509TrustManager)) {
                    return (X509TrustManager) ts;
                }
            }

        } catch(Exception ex){
            ;
        }

        return null;
    }


    /**
	 * Generates custom TrustVerifier based on our trust store
	 * 
	 * @param context
	 * @return
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 */
	public static TrustVerifier getDefaultTrustManager(Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		final KeyStore ks = loadTrustStore(context);
		return new TrustVerifier(ks);
	}

    /**
     * Generates custom TrustVerifier based on our trust store
     *
     * @param context
     * @return
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public static TrustVerifier getDefaultWebTrustManager(Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
        final KeyStore ks = loadWebTrustStore(context);
        return new TrustVerifier(ks);
    }
	
	/**
	 * Installs trust manager for HTTPS connections
	 *  + keystore must contain client certificate + private key in order to connect to mutual auth server
	 *  + truststore is loaded from internal memory - contains ROOT CA
	 *  
	 *  keyStore can be null - to support SSL connections when user does not have client certificate yet.
     *
     * @return Returns SSLContextHolder with used SSLContext, socket factory and hostname verifier.
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 */
	public static SSLContextHolder installTrustManager4HTTPS(KeyStore keyStore, char[] password, Context context)
			throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		return installTrustManager4HTTPS(keyStore, password, context, new SecureRandom());
	}
	
	/**
	 * Installs trust manager for HTTPS connections
	 *  + keystore must contain client certificate + private key in order to connect to mutual auth server
	 *  + truststore is loaded from internal memory - contains ROOT CA
	 *  
	 *  keyStore can be null - to support SSL connections when user does not have client certificate yet.
     *
     * @return Returns SSLContextHolder with used SSLContext, socket factory and hostname verifier.
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 */
	public static SSLContextHolder installTrustManager4HTTPS(KeyStore keyStore, char[] password, Context context, SecureRandom rand)
			throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		// register my scheme for SSL - additional fix, independent of others
        SSLContextHolder holder = new SSLContextHolder();

		final SchemeRegistry schemeRegistry = new SchemeRegistry();
		org.apache.http.conn.ssl.SSLSocketFactory factory = SSLSOAP.createAdditionalCertsSSLSocketFactory(context);
		schemeRegistry.register(new Scheme("https", factory, 18443));
		schemeRegistry.register(new Scheme("https", factory,  8443));
		schemeRegistry.register(new Scheme("https", factory,   443));

		// Host name verifier
		holder.hostVerifier = setHostnameVerifier();

		// Build custom SSL context that should be used for HTTPS.
		SSLContext sslcontext = getSSLContext(keyStore, password, context, rand);
		
		// Use our custom SSL factory - in order to provide stronger encryption.
		CustomSSLSocketFactory csf = getSSLSocketFactory(sslcontext);
		
		// Set this factory as default for HTTPS connections.
		HttpsURLConnection.setDefaultSSLSocketFactory(csf);
		Log.i(TAG, "Initialized default ssl socket factory");

        holder.sslcontext = sslcontext;
        holder.sslSocketFactory = csf;
        return holder;
	}
	
	/**
	 * Creates SSLContext with our trust anchors (stored in the application).
	 * SSL certificates used for SSL/TLS connections has to be stored in the provided key store.
	 * Returns our SSLContext that should be used in the application for secure connections.
	 * 
	 * @param keyStore
	 * @param password
	 * @param context
	 * @param rand
	 * @return
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 * @throws UnrecoverableKeyException
	 */
	public static SSLContext getSSLContext(KeyStore keyStore, char[] password, Context context, SecureRandom rand) 
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException
	{
		// Trust manager array - add my own trust manager trusting only my own CA.
		TrustManager[] trustManagers = new TrustManager[] { SSLSOAP.getTrustManager(context) };

		SSLContext sslcontext = null;
		try {
			KeyManager[] keyManagers = null;
			
			// initialize key manager factory with the client keys.
			if(keyStore!=null){
				KeyManagerFactory keyManagerFactory = null;
				keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				keyManagerFactory.init(keyStore, password);
				keyManagers = keyManagerFactory.getKeyManagers();
			} else {
				Log.i(TAG, "KeyStore is null");
			}

			sslcontext = SSLContext.getInstance("TLS");
			sslcontext.init(keyManagers, trustManagers, rand);
			Log.d(TAG, "initialized TLS context");
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "Exception", e);
		} catch (KeyManagementException e) {
			Log.e(TAG, "Exception", e);
		}
		
		return sslcontext;
	}

    /**
     * Creates SSLContext with our trust anchors (stored in the application).
     * SSL certificates used for SSL/TLS connections has to be stored in the provided key store.
     * Returns our SSLContext that should be used in the application for secure connections.
     *
     * Used for WEB browsing, has system trust anchors + our trust anchors.
     *
     * @param keyStore
     * @param password
     * @param context
     * @param rand
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     * @throws UnrecoverableKeyException
     */
    public static SSLContext getWebSSLContext(KeyStore keyStore, char[] password, Context context, SecureRandom rand)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException
    {
        return getWebSSLContext(keyStore, password, context, rand, false);
    }

    /**
     * Creates SSLContext with our trust anchors (stored in the application).
     * SSL certificates used for SSL/TLS connections has to be stored in the provided key store.
     * Returns our SSLContext that should be used in the application for secure connections.
     *
     * Used for WEB browsing, has system trust anchors + our trust anchors.
     *
     * @param keyStore
     * @param password
     * @param context
     * @param rand
     * @param acceptSystemWideCertificates if True, System wide trust manager is added to the set thus accepts also system wide certificates.
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     * @throws UnrecoverableKeyException
     */
    public static SSLContext getWebSSLContext(KeyStore keyStore, char[] password, Context context, SecureRandom rand, boolean acceptSystemWideCertificates)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException
    {
        // Trust manager array - add my own trust manager trusting only my own CA.
        List<TrustManager> tManagers = new ArrayList<TrustManager>(2);
        final AdditionalKeyStoresTrustManager webTrustManager = SSLSOAP.getWebTrustManager(context);
        tManagers.add(webTrustManager);

        // Add system trust manager?
        if (acceptSystemWideCertificates){
            X509TrustManager systemTrustManager = getSystemTrustManager(context);
            if (systemTrustManager!=null){
                tManagers.add(systemTrustManager);
                webTrustManager.addTrustManager(systemTrustManager);

                Log.vf(TAG, "System trust manager added: %s", systemTrustManager);
            }
        }

        SSLContext sslcontext = null;
        TrustManager[] trustManagers = tManagers.toArray(new TrustManager[tManagers.size()]);
        try {
            KeyManager[] keyManagers = null;

            // initialize key manager factory with the client keys.
            if(keyStore!=null){
                KeyManagerFactory keyManagerFactory = null;
                keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, password);
                keyManagers = keyManagerFactory.getKeyManagers();
            } else {
                Log.i(TAG, "KeyStore is null");
            }

            sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(keyManagers, trustManagers, rand);
            Log.df(TAG, "initialized TLS context; |trustManagers|=%d", trustManagers.length);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Exception", e);
        } catch (KeyManagementException e) {
            Log.e(TAG, "Exception", e);
        }

        return sslcontext;
    }
	
	/**
	 * Creates custom SSL Socket factory using default SSL Socket factory 
	 * from provided SSL Context.
	 * 
	 * @param sslcontext
	 * @return
	 */
	public static CustomSSLSocketFactory getSSLSocketFactory(SSLContext sslcontext){
		SSLSocketFactory sf = sslcontext.getSocketFactory();
		CustomSSLSocketFactory csf = new CustomSSLSocketFactory(sf);
		return csf;
	}
	
	/**
	 * Sets custom HostnameVerifier.
	 */
	public static CustomHostnameVerifier setHostnameVerifier(){
		// Host name verifier
        final CustomHostnameVerifier v = new CustomHostnameVerifier();
		HttpsURLConnection.setDefaultHostnameVerifier(v);

        return v;
	}
	
	/**
	 * Creates scheme registry for 80 and 443, 8443 ports using trust own trust verifier.
	 * @return
	 */
	public static SchemeRegistry createPlainSchemeRegistry(Context ctxt, boolean registerHttp){
	    org.apache.http.conn.ssl.SSLSocketFactory factory = SSLSOAP.createAdditionalCertsSSLSocketFactory(ctxt);
	    SchemeRegistry schemeRegistry = new SchemeRegistry();
	    if (registerHttp){
	    	schemeRegistry.register(new Scheme("http",  PlainSocketFactory.getSocketFactory(), 80));
	    }
	    
	    schemeRegistry.register(new Scheme("https", factory,   443));
	    schemeRegistry.register(new Scheme("https", factory,  8443));
	    schemeRegistry.register(new Scheme("https", factory, 18443));
	    return schemeRegistry;
	}

    /**
     * Check certificate validity without throwing an exception.
     * @param cert
     * @param tv
     * @return
     */
    public static boolean isCertificateValid(java.security.cert.X509Certificate cert, X509TrustManager tv){
        if (cert==null || tv==null){
            return false;
        }

        try {
            tv.checkServerTrusted(new X509Certificate[] {cert}, "default");
            return true;
        } catch (CertificateException e) {

        }

        return false;
    }

	/**
	 * Custom HostnameVerifier encapsulating another StrictHostnameVerifier.
	 * It checks the first CN present in the server certificate against
	 * the hostname specified in URL and also checks all of the subject-alt
	 * entries in the certificate for a match. Wildcards are allowed, but only
	 * up to one level. 
	 * 
	 * @author ph4r05
	 *
	 */
	private static class CustomHostnameVerifier implements HostnameVerifier {
		private final HostnameVerifier hv = new StrictHostnameVerifier();
		
		@Override
		public boolean verify(String hostname, SSLSession session) {
			Log.inf(TAG, "Hostname verifier asked for verification: [%s]", hostname);
			return hv.verify(hostname, session);
		}
	}
	
	/**
	 * AES TLS ciphersuites considered as secure.
	 * Only CS with perfect forward secrecy.
	 * Warning: use TLS 1.1 and above only!
	 * @return
	 */
	public static String[] getAESCiphersuites() {
		return AES_CIPHERSUITES;
	}
	
	/**
	 * Returns secure TLS protocols.
	 * @return
	 */
	public static String[] getSecureProtocols() {
		return SECURE_PROTOCOLS;
	}

	/**
	 * SSL socket factory using delegation to default socket factory.
	 * Allows only some cipher suites - secure ones.
	 * 
	 * @author ph4r05
	 *
	 */
	public static class CustomSSLSocketFactory extends SSLSocketFactory{
		private final SSLSocketFactory factory;
		private boolean allowedFallbackTLSv1 = true;
		private boolean allowedTLSv1 = false;
		
		/**
		 * If set to true, then sslSocketFactory will be allowed to use TLSv1
		 * if there is none support for TLSv1.1 or TLSv1.2.
		 * 
		 * @param allowedFallbackTLSv1
		 */
		public void setAllowedFallbackTLSv1(boolean allowedFallbackTLSv1) {
			this.allowedFallbackTLSv1 = allowedFallbackTLSv1;
		}

		/**
		 * If true, TLSv1 is normally allowed as an enabled protocol.
		 * @param allowedTLSv1
		 */
		public void setAllowedTLSv1(boolean allowedTLSv1) {
			this.allowedTLSv1 = allowedTLSv1;
		}
		
		public CustomSSLSocketFactory(SSLSocketFactory factory) {
			super();
			this.factory = factory;
		}
		
		/**
		 * Processing socket.
		 * Enables only secure cipher suites, black lists RC4, DES, anon, ...
		 * 
		 * @param s
		 * @return
		 */
		private Socket processSocket(Socket s){
			if (s==null) return null;
			if (SSLSocket.class.isAssignableFrom(s.getClass())){
				final SSLSocket ssl = (SSLSocket) s;
				Log.vf(TAG, "SSLSocket created: %s", ssl.toString());
				
				//
				// Enabled security protocols.
				//
				String[] protocols = ssl.getEnabledProtocols();
				ArrayList<String> protocolsEnabled = new ArrayList<String>();
				Set<String> protocolsSet = new HashSet<String>(protocols.length);
				for(String proto : protocols){
					protocolsSet.add(proto);
				}
				
				// Add TLSv1.2 if enabled.
				if (protocolsSet.contains(TLSv12)){
					protocolsEnabled.add(TLSv12);
				}
				
				// Add TLSv1.1 if enabled
				if (protocolsSet.contains(TLSv11)){
					protocolsEnabled.add(TLSv11);
				}
				
				// If no other protocol is supported, add TLSv1 if enabled
				if ((isAllowedTLSv1() && protocolsSet.contains(TLSv1)) || 
				    (isAllowedFallbackTLSv1() && protocolsEnabled.isEmpty() && protocolsSet.contains(TLSv1))){
					protocolsEnabled.add(TLSv1);
				}
				ssl.setEnabledProtocols(protocolsEnabled.toArray(new String[protocolsEnabled.size()]));
				
				//
				// Enabled cipher suites.
				//
				String[] cipher = ssl.getEnabledCipherSuites();
				if (cipher==null || cipher.length==0){
					throw new SecurityException("Cipher suite is null or empty");
				}

				ArrayList<String> cipherEnabled = new ArrayList<String>();
				for(String cs : cipher){
					// Cipher suite should be upper case, but anyway...
					final String csUp = cs.toUpperCase();
					boolean enabled = true;

					// Blacklist RC4, DES
					if (csUp.contains("RC4") || csUp.contains("DES")){
						enabled = false;
					}

					// Blacklist MD5
					if (csUp.contains("MD5")){
						enabled = false;
					}

					// Blacklist anonymous auth!
					if (csUp.contains("ANON")){
						enabled = false;
					}

					// Blacklist NULL entries!
					if (csUp.contains("NULL")){
						enabled = false;
					}

					// Use just AES anyway
					if (!csUp.contains("AES")){
						enabled = false;
					}

                    // 23 API - Android M
                    if (Compatibility.isApiGreaterOrEquals(23)){
                        /*
                        Android 6.0 fix - of any of these ciphers is enabled, BoringSSL on Android 6.0 returns INVALID GROUP SIZE
                        Therefore avoid because there are other ciphers available.

                        TLS_DHE_RSA_WITH_AES_128_CBC_SHA
                        TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
                        TLS_DHE_RSA_WITH_AES_256_CBC_SHA
                        TLS_DHE_RSA_WITH_AES_256_GCM_SHA384

                        Also disable ciphers like TLS_RSA_WITH_AES_128_CBC_SHA ("prefix TLS_RSA") with no PFC

                        Basically only ciphers containing TLS_ECDHE are only allowed
                         */
                        if (csUp.startsWith("TLS_DHE") || csUp.startsWith("TLS_RSA")){
                            enabled = false;
                        }
                    }

					if (enabled){
						cipherEnabled.add(cs);
					}
				}
				
				// Sort cipher suites, prefer those having TLS as a prefix, then DHE.
				Collections.sort(cipherEnabled, new CipherSuiteComparator());
                
				// Print sorted:
				for(String cs : cipherEnabled){
					Log.vf(TAG, "CipherSuiteEnabled: %s", cs);
				}
				
				// Set enabled cipher suites to the socket.
				ssl.setEnabledCipherSuites(cipherEnabled.toArray(new String[cipherEnabled.size()]));
				
			} else {
				Log.e(TAG, "Returned socket is not SSL socket");
				throw new SecurityException("Not using SSL Socket");
			}
			
			return s;
		}
		
		/**
		 * @return
		 * @throws IOException
		 * @see javax.net.SocketFactory#createSocket()
		 */
		public Socket createSocket() throws IOException {
			return processSocket(factory.createSocket());
		}
		
		/**
		 * @param address
		 * @param port
		 * @param localAddress
		 * @param localPort
		 * @return
		 * @throws IOException
		 * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int, java.net.InetAddress, int)
		 */
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
			return processSocket(factory.createSocket(address, port, localAddress, localPort));
		}
		
		/**
		 * @param host
		 * @param port
		 * @return
		 * @throws IOException
		 * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int)
		 */
		public Socket createSocket(InetAddress host, int port) throws IOException {
			return processSocket(factory.createSocket(host, port));
		}
		
		/**
		 * @param s
		 * @param host
		 * @param port
		 * @param autoClose
		 * @return
		 * @throws IOException
		 * @see javax.net.ssl.SSLSocketFactory#createSocket(java.net.Socket, java.lang.String, int, boolean)
		 */
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
			return processSocket(factory.createSocket(s, host, port, autoClose));
		}
		
		/**
		 * @param host
		 * @param port
		 * @param localHost
		 * @param localPort
		 * @return
		 * @throws IOException
		 * @throws UnknownHostException
		 * @see javax.net.SocketFactory#createSocket(java.lang.String, int, java.net.InetAddress, int)
		 */
		public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
			return processSocket(factory.createSocket(host, port, localHost, localPort));
		}
		
		/**
		 * @param host
		 * @param port
		 * @return
		 * @throws IOException
		 * @throws UnknownHostException
		 * @see javax.net.SocketFactory#createSocket(java.lang.String, int)
		 */
		public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
			return processSocket(factory.createSocket(host, port));
		}
		
		/**
		 * @param o
		 * @return
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		public boolean equals(Object o) {
			return factory.equals(o);
		}
		
		/**
		 * @return
		 * @see javax.net.ssl.SSLSocketFactory#getDefaultCipherSuites()
		 */
		public String[] getDefaultCipherSuites() {
			return factory.getDefaultCipherSuites();
		}
		
		/**
		 * @return
		 * @see javax.net.ssl.SSLSocketFactory#getSupportedCipherSuites()
		 */
		public String[] getSupportedCipherSuites() {
			return factory.getSupportedCipherSuites();
		}
		
		/**
		 * @return
		 * @see java.lang.Object#hashCode()
		 */
		public int hashCode() {
			return factory.hashCode();
		}
		
		/**
		 * @return
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return "CustomFactory:" + factory.toString();
		}
		
		public boolean isAllowedFallbackTLSv1() {
			return allowedFallbackTLSv1;
		}
		
		public boolean isAllowedTLSv1() {
			return allowedTLSv1;
		}
	}
	
	/**
	 * Cipher suite comparator, preferring TLS, DHE, RSA, AES cipher suites.
	 * @author ph4r05
	 *
	 */
	private static class CipherSuiteComparator implements Comparator<String>{
		@Override
		public int compare(String lhs, String rhs) {
			
			// Prefer those using TLS.
			boolean lTLS = lhs.startsWith("TLS_");
			boolean rTLS = rhs.startsWith("TLS_");
			if ( lTLS && !rTLS) return -1;
			if (!lTLS &&  rTLS) return  1;
			
			// Both are TLSs or none of them.
			// Prefer those using Diffie-Hellman key exchange (PFC)
			boolean lDH = lhs.contains("_DH_");
			boolean rDH = rhs.contains("_DH_");
			if ( lDH && !rDH) return -1;
			if (!lDH &&  rDH) return  1;
			
			// Prefer those using Diffie-Hellman elliptic key exchange (PFC)
			boolean lDHE = lhs.contains("_DHE_");
			boolean rDHE = rhs.contains("_DHE_");
			if ( lDHE && !rDHE) return -1;
			if (!lDHE &&  rDHE) return  1;
			
			// Both has DHE or none of them.
			// Prefer those using RSA.
			boolean lRSA = lhs.contains("_RSA_");
			boolean rRSA = rhs.contains("_RSA_");
			if ( lRSA && !rRSA) return -1;
			if (!lRSA &&  rRSA) return  1;
			
			// Prefer AES 256 GCM
			boolean lAESGCM1 = lhs.contains("_AES_256_GCM_");
			boolean rAESGCM1 = lhs.contains("_AES_256_GCM_");
			if ( lAESGCM1 && !rAESGCM1) return -1;
			if (!lAESGCM1 &&  rAESGCM1) return  1;
			
			// Prefer AES 128 GCM
			boolean lAESGCM2 = lhs.contains("_AES_128_GCM_");
			boolean rAESGCM2 = lhs.contains("_AES_128_GCM_");
			if ( lAESGCM2 && !rAESGCM2) return -1;
			if (!lAESGCM2 &&  rAESGCM2) return  1;
			
			// Both has RSA or none of them.
			// Prefer AES
			boolean lAES = lhs.contains("_AES_");
			boolean rAES = rhs.contains("_AES_");
			if ( lAES && !rAES) return -1;
			if (!lAES &&  rAES) return  1;
			
			// Both has AES or none of them.
			// Now just compare strings.
			return lhs.compareTo(rhs);
		}
	} // end of CipherSuiteComparator.

    /**
     * Holder of SSL Context & socket factory
     */
    public static class SSLContextHolder {
        public SSLContext sslcontext;
        public CustomSSLSocketFactory sslSocketFactory;
        public CustomHostnameVerifier hostVerifier;
    }
}
