/*
 * Copyright 2012 The Spring Web Services Framework.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.phonex.util.crypto.pki;

import net.phonex.soap.AdditionalKeyStoresSSLSocketFactory;
import net.phonex.util.Log;

import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorResult;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.X509TrustManager;


/**
 * X509TrustVerifier used to verify certificate validity. 
 * 
 * Uses <tt>CertPathValidator</tt> to validate the certificate chain/path.
 * Uses provided <tt>KeyStore</tt> to load trust anchors for certificate path validation. 
 * 
 * @author ph4r05
 * @see AdditionalKeyStoresSSLSocketFactory
 */
public class TrustVerifier implements X509TrustManager {	
	private static final String TAG="TrustVerifier";
    
    /**
     * Trust store 
     */
    private KeyStore trustStoreCA = null;
    private PKIXParameters params = null;   // certification parameters
    private X509Certificate[] trustedCerts = null;
        
    private CertificateFactory cf = null;
    
    // SpongyCastle static initialization
    static {
        Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
    }
    
    public TrustVerifier() {

    }
    
    public TrustVerifier(KeyStore ks) {
    	this.init(ks);
    }
    
    /**
     * Loads all certificates from resources to memory.
     */
    final public void init(KeyStore ks){
        try {      
        	// certificate factory according to X.509 std
            cf = CertificateFactory.getInstance("X.509");
            
            // keystore
        	trustStoreCA = ks;
            Log.vf(TAG, "Trust store loaded; key store=%s; size=%s", trustStoreCA.toString(), trustStoreCA.size());
            
            // Set validation parameters
            params = new PKIXParameters(trustStoreCA);
            params.setRevocationEnabled(false); // to avoid exception on empty CRL
            Log.i(TAG, "Verification params loaded; ");
            
            // Load all trusted certificates
            ArrayList<X509Certificate> certs = new ArrayList<X509Certificate>();
            Enumeration<String> aliases = trustStoreCA.aliases();
            while(aliases.hasMoreElements()){
                final String alias = aliases.nextElement();
                X509Certificate certificate = (X509Certificate) trustStoreCA.getCertificate(alias);
                certs.add(certificate);
            }
            
            trustedCerts = new X509Certificate[certs.size()];
            certs.toArray(trustedCerts);
            Log.inf(TAG, "Trust verifier initialized; len(certs)=%s", trustedCerts.length);
            
        } catch (Exception ex) {
        	Log.e(TAG, "Problem with loading trust store", ex);
        }
    }
    
    @Override
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return trustedCerts;
    }

    @Override
    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
        this.checkTrusted(certs, authType);
    }

    @Override
    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
        this.checkTrusted(certs, authType);
    }
    
    /**
     * Checks if all certificates in cert chain are valid and there is trusted element in chain.
     * @param certs
     * @param authType
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
    	checkTrusted(certs, authType, null);
    }
    
    /**
     * Checks if all certificates in cert chain are valid and there is trusted element in chain.
     * @param certs
     * @param authType
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate[] certs, String authType, Date date) throws CertificateException {
        if (certs == null || certs.length == 0) {
            throw new IllegalArgumentException("null or zero-length certificate chain");
        }
        
        // Check if every certificate in chain is still valid. Every has to be valid!
        // TODO: check Certificate Revocation List!
        X509Certificate lastCert = null;
        try {
            for (X509Certificate cert : certs) {
            	lastCert = cert;
            	
            	if(date==null) {
            		lastCert.checkValidity();
            	}
            	else {
            		lastCert.checkValidity(date);
            	}
            }
        } catch (Exception e) {
            throw new CertificateException("Certificate not trusted. It has expired; cert=" + lastCert + "; date=" + date, e);
        }
        
        // Validate certificate path
        CertPath certPath = null;
        try {
            // Build certificate path
            List<X509Certificate> certList = new ArrayList<X509Certificate>();
            certList.addAll(Arrays.asList(certs));
            
            // Create cert path for further verification
            certPath = cf.generateCertPath(certList);
            
            // Validate cert path against trusted store
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            
            // set validation date for this test
            params.setDate(date);
             
            @SuppressWarnings("unused")
			CertPathValidatorResult result = validator.validate(certPath, params);
            
            return;	// if here, chain is valid
        } catch(Exception e){
            throw new CertificateException("Certificate not validated; certPath="+certPath, e);
        } finally {
        	// reset verification date
        	params.setDate(null);
        }
    }

    /**
     * Check trust for cert chain
     * 
     * @param certs
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate[] certs) throws CertificateException{
        checkClientTrusted(certs, "default");
    }
    
    /**
     * Check trust for one cert
     * 
     * @param cert
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate cert) throws CertificateException{
        java.security.cert.X509Certificate[] arr = new X509Certificate[] {cert};
        checkTrusted(arr, "default");
    }
    
    /**
     * Check trust for one cert
     * 
     * @param cert
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate cert, Date date) throws CertificateException{
        java.security.cert.X509Certificate[] arr = new X509Certificate[] {cert};
        checkTrusted(arr, "default", date);
    }
    
    public KeyStore getTrustStoreCA() {
        return trustStoreCA;
    }

    public PKIXParameters getParams() {
        return params;
    }

    public X509Certificate[] getTrustedCerts() {
        return trustedCerts;
    }

    public CertificateFactory getCf() {
        return cf;
    }
}
