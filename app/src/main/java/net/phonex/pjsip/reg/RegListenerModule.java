package net.phonex.pjsip.reg;

import android.content.Context;

import net.phonex.db.entity.SipProfile;
import net.phonex.sip.IPjPlugin;
import net.phonex.util.Log;

import net.phonex.xv.Xvi;

public class RegListenerModule implements IPjPlugin {
    private static final String TAG = "RegListenerModule";
    private RegListener regListener;

    public RegListenerModule() {
    }

    @Override
    public void setContext(Context ctxt) {
        regListener = new RegListener(ctxt);
    }

    @Override
    public void onBeforeStartPjsip() {
    	if (regListener ==null){
    		throw new RuntimeException("RegListener is null");
    	}
    	
    	// Initialize callback.
    	regListener.init();
    	
        int status = Xvi.mobile_reg_handler_init();
        Xvi.mobile_reg_handler_set_callback(regListener);
        Log.vf(TAG, "RegListener added, status=%s", status);
    }

    @Override
    public void onBeforeAccountStartRegistration(int pjId, SipProfile acc) {
        regListener.set_account_cleaning_state(pjId, acc.getTry_clean_registers());
    }

	@Override
	public void onBeforeStopPjsip() {
		// Destroy reg handler.
		if (regListener !=null){
			regListener.deinit();
		}
	}
}
