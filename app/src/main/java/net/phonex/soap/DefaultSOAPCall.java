package net.phonex.soap;

import android.content.Context;

import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Default SOAP client.
 * 
 * @author ph4r05
 */
public abstract class DefaultSOAPCall implements Runnable{

    public interface ProgressEventListener{
        public void publishProgress(String... values);
    }

	/**
     * SOAP client.
     */
    protected SOAPHelper soap;
    protected Context ctxt;


    protected ProgressEventListener progressEventListener;
    protected Exception thrownException;



    public DefaultSOAPCall() {
    	soap=null;
    	ctxt=null;
    }
    
	public DefaultSOAPCall(Context ctxt) {
		this.ctxt = ctxt;
		soap = new SOAPHelper(ctxt);
	}

    public void setProgressEventListener(ProgressEventListener listener) {
        this.progressEventListener = listener;
    }

    public Exception getThrownException() {
        return thrownException;
    }

    public void setThrownException(Exception thrownException) {
        this.thrownException = thrownException;
    }

    @Override
    public abstract void run();
	
	/**
	 * Initializes SOAP with default KeyStore and TrustVerifier.
	 * @param storagePass
	 * @throws IOException 
	 * @throws CertificateException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyStoreException 
	 * @throws UnrecoverableKeyException 
	 */
	public void initSoap(String storagePass) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		soap.initSoap(storagePass);
	}
	
	/**
	 * Simple debug call to SOAP service
	 * static because it can be called from other tasks not extending BaseAsyncTask
	 * @param soapEnvelope
	 * @param destination
	 * @param action
	 * @param returnBody
	 * @return
	 */
	public static Object simpleSOAPRequest(SoapSerializationEnvelope soapEnvelope, String destination, String action, boolean returnBody) throws Exception{
		return SOAPHelper.simpleSOAPRequest(soapEnvelope, destination, action, returnBody);
	}

	/**
	 * Returns internal SOAP helper.
	 * @return
	 */
	public SOAPHelper getSoap(){
		return soap;
	}
	
	/**
	 * Returns context stored in private attribute.
	 * @return
	 */
	public Context getContext() {
		return ctxt;
	}
	
	/**
	 * Returns SecureRandom initialized by init() call.
	 * @return
	 */
	public SecureRandom getRand(){
		return soap.getRand();
	}

	/**
	 * Sets new context to the internal attribute. 
	 * Initializes underlying SOAP internal helper with this context
	 * if does not exist, otherwise just updates.
	 * @param ctxt
	 */
	public void setContext(Context ctxt) {
		this.ctxt = ctxt;
		if (soap==null){
			soap = new SOAPHelper(ctxt);
		} else {
			soap.setCtxt(ctxt);
		}
	}
}
