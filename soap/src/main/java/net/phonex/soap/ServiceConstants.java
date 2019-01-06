package net.phonex.soap;

import android.content.Context;

import net.phonex.common.Settings;
//import net.phonex.PhonexSettings;
//import net.phonex.core.PhonexConfig;
//import net.phonex.pub.a.PreferencesConnector;


public class ServiceConstants {
	public static final String NAMESPACE              = "http://phoenix.com/hr/schemas";
    public static final String NAMESPACE_ENVELOPE     = "http://schemas.xmlsoap.org/soap/envelope";
    
    public static final String SERVICE_NAME           = "phoenix";
    public static final String SERVICE_NAME_DEVEL     = "phoenix";
    
    public static final String SERVICE_ENDPOINT       = SERVICE_NAME       + "/phoenixService";
    public static final String SERVICE_ENDPOINT_DEVEL = SERVICE_NAME_DEVEL + "/phoenixService";

    // Do not change
    public static final String SERVICE_PORT_PRODUCTION           = "8443";
    public static final String SERVICE_PORT_NO_CERT_PRODUCTION   = "8442";

    // Do not change
    public static final String SERVICE_PORT_DEVEL           = "18443";
    public static final String SERVICE_PORT_NO_CERT_DEVEL   = "18442";

    public static final String SERVICE_PORT           = SERVICE_PORT_PRODUCTION;
    public static final String SERVICE_PORT_NO_CERT   = SERVICE_PORT_NO_CERT_PRODUCTION;

    public static final String SERVICE_SCHEME         = "https";
    
    public static final String SERVICE_WSDL           = SERVICE_NAME       + "/phoenixService/phoenix.wsdl";
    public static final String SERVICE_WSDL_DEVEL     = SERVICE_NAME_DEVEL + "/phoenixService/phoenix.wsdl";
    
    /**
     * Returns URL to the data service.
     * scheme + hostname + port + /
     * 
     * @return
     */
    public static String getServiceURL(String domain){
    	return getServiceURL(domain, true);
    }
    
    /**
     * Returns URL to the data service.
     * scheme + hostname + port + /
     * 
     * @return
     */
    public static String getServiceURL(String domain, boolean hasCert){
    	return (SERVICE_SCHEME + "://" + domain + ":" + (hasCert ? getServicePort() : getServicePortNoCert()) + "/");
    }
    
    /**
     * Determines whether user wants to use development data server.
     * @deprecated used with old devel server, now devel port is used.
     * @param ctxt
     * @return
     */
    public static boolean useDevel(Context ctxt){
    	if (ctxt==null || !Settings.debuggingRelease()){
    		return false;
    	}
        return false;
    	
//    	PreferencesConnector pWrapper = new PreferencesConnector(ctxt);
//    	return pWrapper.getBoolean(PhonexConfig.USE_DEVEL_DATA_SERVER, false);
    }
    
    /**
     * Returns default service endpoint for SOAP calls.
     * @param ctxt
     * @return
     */
    public static String getSOAPServiceEndpoint(Context ctxt){
    	if (ctxt==null || !Settings.debuggingRelease()){
    		return SERVICE_ENDPOINT;
    	}
    	
    	return useDevel(ctxt) ? SERVICE_ENDPOINT_DEVEL : SERVICE_ENDPOINT;
    }

    /**
     * Returns service port. If built in release mode, production port is used anyway.
     * @return
     */
    public static String getServicePort(){
        if (!Settings.debuggingRelease()){
            return SERVICE_PORT_PRODUCTION;
        }

        return SERVICE_PORT;
    }

    /**
     * Returns service port. If built in release mode, production port is used anyway.
     * @return
     */
    public static String getServicePortNoCert(){
        if (!Settings.debuggingRelease()){
            return SERVICE_PORT_NO_CERT_PRODUCTION;
        }

        return SERVICE_PORT_NO_CERT;
    }
    
    /**
     * Returns default URL for SOAP call.
     * 
     * @param domain
     * @return
     */
    public static String getDefaultURL(String domain){
    	return (SERVICE_SCHEME + "://" + domain + ":" + getServicePort() + "/" + SERVICE_ENDPOINT);
    }
    
    /**
     * Returns default URL for SOAP call.
     * Takes USE_DEVEL_DATA_SERVER into consideration.
     * 
     * @param domain
     * @param ctxt
     * @return
     */
    public static String getDefaultURL(String domain, Context ctxt){
    	if (ctxt==null || !Settings.debuggingRelease()){
    		return getDefaultURL(domain);
    	}
    	
    	return useDevel(ctxt) ? (SERVICE_SCHEME + "://" + domain + ":" + getServicePort() + "/" + SERVICE_ENDPOINT_DEVEL) : getDefaultURL(domain);
    }
    
    /**
     * Returns default URL for REST call.
     * 
     * @param domain
     * @return
     */
    public static String getDefaultRESTURL(String domain){
    	return (SERVICE_SCHEME + "://" + domain + ":" + getServicePort() + "/" + SERVICE_NAME);
    }

    /**
     * URL with no service name
     * @param hasClientCert
     * @return
     */
    public static String getKeyServerRESTUrl(boolean hasClientCert){
        return SERVICE_SCHEME + "://" + "phone-x.net" + ":" + (hasClientCert ? getServicePort() : getServicePortNoCert());
    }
    
    /**
     * Returns default URL for REST call.
     * Takes USE_DEVEL_DATA_SERVER into consideration.
     * 
     * @param domain
     * @param ctxt
     * @return
     */
    public static String getDefaultRESTURL(String domain, Context ctxt){
    	if (ctxt==null || !Settings.debuggingRelease()){
    		return getDefaultRESTURL(domain);
    	}

    	return useDevel(ctxt) ? (SERVICE_SCHEME + "://" + domain + ":" + getServicePort() + "/" + SERVICE_NAME_DEVEL) : getDefaultRESTURL(domain);
    }
}
