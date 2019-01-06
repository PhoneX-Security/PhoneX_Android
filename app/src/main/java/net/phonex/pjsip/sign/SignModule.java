package net.phonex.pjsip.sign;

import android.content.Context;

import net.phonex.db.entity.SipProfile;
import net.phonex.sip.IPjPlugin;
import net.phonex.util.Log;

import net.phonex.xv.Xvi;


public class SignModule implements IPjPlugin {

    private static final String THIS_FILE = "SignModule";
    private SignModCallback sigCB = null;

    public SignModule() {
    }

    @Override
    public void setContext(Context ctxt) {
    	if (sigCB==null){
    		sigCB = new SignModCallback(ctxt);
    	} else {
    		sigCB.setContext(ctxt);
    	}
    	
    	Log.d(THIS_FILE, "context set");
    }

    @Override
    public void onBeforeStartPjsip() {
        int status = Xvi.mod_sign_init();
        Log.df(THIS_FILE, "sigCB class prior setting callback: %s", sigCB);
        
        Xvi.mod_sign_set_callback(sigCB);
        Log.df(THIS_FILE, "SIGN module added with status %s; sigCB class: %s", status, sigCB);
    }
    
    @Override
    public void onBeforeStopPjsip(){
    	Log.df(THIS_FILE, "de-initializing mod-sign: %s", sigCB);
    	sigCB.deinit();
    }

    @Override
    public void onBeforeAccountStartRegistration(int pjId, SipProfile acc) {
    	Log.d(THIS_FILE, "OnBeforeAccountStartRegistration");
        //regHandlerReceiver.set_account_cleaning_state(pjId, acc.try_clean_registers);
    }

	public SignModCallback getSigCB() {
		return sigCB;
	}
	
	public int initSignatures(String keyStore, String password) throws Exception{
		Log.df(THIS_FILE, "sigCB class prior initSignatures: %s", sigCB);
		return sigCB.initSignatures(keyStore, password);
	}
	
	public void flushCache(){
		sigCB.flushCache();
	}
}
