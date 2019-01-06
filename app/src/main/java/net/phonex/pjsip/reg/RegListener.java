package net.phonex.pjsip.reg;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.SparseIntArray;

import net.phonex.pub.a.PjManager;
import net.phonex.sip.PjUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import net.phonex.xv.MobileRegHandlerCallback;
import net.phonex.xv.Xvi;
import net.phonex.xv.pj_str_t;

public class RegListener extends MobileRegHandlerCallback {
    private static final String TAG = "RegListener";
    private static final String REG_URI_PREFIX = "reg_uri_";
    private static final String REG_EXPIRES_PREFIX = "reg_expires_";
    private final pj_str_t EMPTY_PJSTRING = Xvi.pj_str_copy("");

    private SharedPreferences prefs_db;
    private Context ctxt;
    private SparseIntArray accountCleanRegisters = new SparseIntArray();

    private HandlerThread ht;
    private RegHandler h;
    
    public RegListener(Context ctxt) {
        this.ctxt = ctxt;
        prefs_db = ctxt.getSharedPreferences("registration_prefs", Context.MODE_PRIVATE);
    }
    
    /**
     * Initializes internal handlers.
     */
    public void init(){
    	// Start handler & handler thread.
        ht = new HandlerThread("RegHandler");
        ht.start();
        h = new RegHandler(ht.getLooper());
    }
    
    /**
     * Stops running handlers.
     */
    public void deinit(){
    	h = null;
    	if (ht!=null){
    		MiscUtils.stopHandlerThread(ht, false);
    		ht = null;
    	}
    }
    
    public void set_account_cleaning_state(int acc_id, int active) {
        accountCleanRegisters.put(acc_id, active);
    }
    
    @Override
    public pj_str_t on_restore_contact(int acc_id) {
        int active = accountCleanRegisters.get(acc_id, 0);
        if(active == 0) {
            return EMPTY_PJSTRING;
        }

        long db_acc_id = PjManager.getAccountIdForPjsipId(ctxt, acc_id);
        String key_expires = REG_EXPIRES_PREFIX + Long.toString(db_acc_id);
        String key_uri = REG_URI_PREFIX + Long.toString(db_acc_id);
        int expires = prefs_db.getInt(key_expires, 0);
        int now = (int) Math.ceil(System.currentTimeMillis() / 1000);
        if(expires >= now) {
            String ret = prefs_db.getString(key_uri, "");
            Log.df(TAG, "We restore %s", ret);
            return Xvi.pj_str_copy(ret);
        }

        return EMPTY_PJSTRING;
    }
    
    @Override
    public void on_save_contact(final int acc_id, pj_str_t contact, final int expires) {
    	final String contactStr = PjUtils.pjStrToString(contact);
    	if (h==null){
    		Log.e(TAG, "Handler is null, cannot save_contact");
    		return;
    	}
    	
    	try {
	    	h.execute(new Runnable() {
				@Override
				public void run() {
					long db_acc_id = PjManager.getAccountIdForPjsipId(ctxt, acc_id);
			        String key_expires = REG_EXPIRES_PREFIX + Long.toString(db_acc_id);
			        String key_uri = REG_URI_PREFIX + Long.toString(db_acc_id);
			        
			        Log.i(TAG, String.format("acc_id=[%d], contact[%s], db_acc_id=[%d], keyExp=[%s], keyUri=[%s] ",
			        		acc_id, contactStr, db_acc_id, key_expires, key_uri));
			        
			        Editor edt = prefs_db.edit();
			        edt.putString(key_uri, contactStr);
			        int now = (int) Math.ceil(System.currentTimeMillis() / 1000);
			        edt.putInt(key_expires, now + expires);
			        edt.commit();
				}
			});   
    	} catch(Exception e){
    		Log.e(TAG, "Exception in save_contact", e);
    	}
    }
    
    /**
     * Handler for asynchronous message handling from PJSIP.
     * @author ph4r05
     */
    private static class RegHandler extends Handler {
		RegHandler(Looper l) {
            super(l);
        }

        public void execute(Runnable task) {
            Message.obtain(this, 0, task).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
	        if (msg.obj instanceof Runnable) {
                executeInternal((Runnable) msg.obj);
            } else {
                Log.wf(TAG, "can't handle msg: %s", msg);
            }
        }
        
        private void executeInternal(Runnable task) {
            try {
            	Log.v(TAG, "<RegListener>");
                task.run();
                Log.v(TAG, "</RegListener>");
            } catch (Throwable t) {
                Log.ef(TAG, t, "run task: %s", task);
            } finally {
            	
            }
        }
    }
}
