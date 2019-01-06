package net.phonex.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import net.phonex.core.Constants;
import net.phonex.core.IService;
import net.phonex.service.XService;

public class DefaultServiceConnector {
	protected static final String THIS_FILE = "SvcConnector";
	protected IService service;
	protected Context defCtxt;
	protected ServiceConnectorListener listener;


    public DefaultServiceConnector(Context defCtxt, ServiceConnectorListener listener) {
        this.defCtxt = defCtxt;
        this.listener = listener;
    }

    public DefaultServiceConnector() {

    }

    public interface ServiceConnectorListener {
		void onXServiceConnected(ComponentName arg0, IBinder arg1);
		void onXServiceDisconnected(ComponentName arg0);
	}
	
	protected ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName arg0, final IBinder arg1) {
        	try {
	            service = IService.Stub.asInterface(arg1);
	            Log.v(THIS_FILE, "service: onServiceConnected()"); 
	            
	            if (listener!=null){
	            	listener.onXServiceConnected(arg0, arg1);
	            }
        	} catch(Exception e){
        		Log.e(THIS_FILE, "service: Exception onServiceConnected", e);
        	}
        }

        @Override
        public void onServiceDisconnected(final ComponentName arg0) {
        	Log.v(THIS_FILE, "service: onServiceDisconnected()");
        	service = null;
        	
        	if (listener!=null){
            	listener.onXServiceDisconnected(arg0);
            }
        }
    };

    /**
	 * Connects to SIP service if is not already connected.
	 * Uses bindService() call, BIND_AUTO_CREATE flag.
	 * 
	 * @param ctxt Context
	 */
	public boolean connectService(Context ctxt){
		return connectService(ctxt, false);
	}
    
    /**
	 * Connects to SIP service if is not already connected.
	 * Uses bindService() call, BIND_AUTO_CREATE flag.
	 * 
	 * @param ctxt Context
	 * @param async If true bindService() will be called in a separate thread.
	 */
	public boolean connectService(Context ctxt, boolean async){
		if (service!=null){
			return true;
		}
		
		if (ctxt==null) ctxt = defCtxt;		// try default context if possible
		if (ctxt==null){
			Log.w(THIS_FILE, "NULL context in connectService()");
			return false;
		}
		
		try {
			Log.v(THIS_FILE, "Connecting to service...");
			final Intent bindIntent = XService.getStartServiceIntent(ctxt.getApplicationContext());
			final Context localCtxt = ctxt;
			if (async){
				// Asynchronous binding is in a separate thread. 
				// Main activity uses this approach.
				Thread t = new Thread("StartSip") {
		            public void run() {
		                localCtxt.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE);
		            };
		        };
		        t.start();
		        
			} else {
				// Binding in current thread
				boolean result = ctxt.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE);
				if (!result){
					Log.w(THIS_FILE, "service: service binding failed, false returned");
				}
				
			}
		} catch(Exception e){
			Log.e(THIS_FILE, "Exception during service binding", e);
			return false;
		}
		
		return true;
	}
	
	/**
	 * This call cause to disconnect from service, usually called form onDestroy()
	 * 
	 * @param ctxt
	 * @return
	 */
	public boolean disconnectService(Context ctxt){
        if (!isConnected()){
            return true;
        }

		if (ctxt==null) ctxt = defCtxt;		// try default context if possible
		if (ctxt==null){
			Log.w(THIS_FILE, "NULL context in connectService()");
			return false;
		}
		
		if (connection==null){
			Log.w(THIS_FILE, "Connection is null in disconnectService(), cannot proceed");
			return false;
		}
		
		try {
			ctxt.unbindService(connection);
			connection = null;
			
			Log.i(THIS_FILE, "Service disconnected");
		} catch(Exception e){
			Log.e(THIS_FILE, "Exception during service disconnecting", e);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Is service connected?
	 * @return
	 */
	public boolean isConnected(){
		return (this.service!=null && this.connection!=null);
	}
	
	/**
	 * Returns IService instance 
	 * 
	 * @return service
	 */
	public IService getService() {
		return service;
	}

	/**
	 * Gets default context set to this object
	 * @return
	 */
	public Context getDefCtxt() {
		return defCtxt;
	}

	/**
	 * Set default context
	 * 
	 * @param defCtxt
	 */
	public void setDefCtxt(Context defCtxt) {
		this.defCtxt = defCtxt;
	}

	public ServiceConnectorListener getListener() {
		return listener;
	}

	/**
	 * Sets listener to service events
	 * 
	 * @param listener
	 */
	public void setListener(ServiceConnectorListener listener) {
		this.listener = listener;
	}

	public ServiceConnection getConnection() {
		return connection;
	}

	public void setConnection(ServiceConnection connection) {
		this.connection = connection;
	}

	public void setService(IService service) {
		this.service = service;
	}
}
