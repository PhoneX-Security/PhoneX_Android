package net.phonex.ui.call;

import android.app.Activity;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import net.phonex.R;
import net.phonex.util.Log;

import java.lang.reflect.Method;

/**
 * Created by miroc on 13.1.15.
 */
public class MyPhoneStateListener extends PhoneStateListener {
    private static final String TAG = "MyPhoneStateListener";
    private TelephonyManager telephonyManager;
    private Context context;

    public MyPhoneStateListener(TelephonyManager telephonyManager, Activity activity) {
        this.telephonyManager = telephonyManager;
        context = activity;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        super.onCallStateChanged(state, incomingNumber);
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                //when Idle i.e no call
                //Toast.makeText(CallActivity.this, "Phone state Idle", Toast.LENGTH_LONG).show();
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                //when Off hook i.e in call
                //Toast.makeText(CallActivity.this, "Phone state Off hook", Toast.LENGTH_LONG).show();
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                //when Ringing
                Log.v(TAG, "Receiving GSM call....");
                //Toast.makeText(CallActivity.this, "Phone state ringing...", Toast.LENGTH_LONG).show();
                try
                {
                    if (telephonyManager == null){
                        Log.wf(TAG, "onCallStateChanged; telephonyManager is null");
                        return;
                    }

                    // Get the getITelephony() method
                    Class<?> classTelephony = Class.forName(telephonyManager.getClass().getName());

                    Method methodGetITelephony = classTelephony.getDeclaredMethod("getITelephony");
                    methodGetITelephony.setAccessible(true);

                    // 	Invoke getITelephony() to get the ITelephony interface
                    Object telephonyInterface = methodGetITelephony.invoke(telephonyManager);

                    // Get the endCall method from ITelephony
                    Class<?> telephonyInterfaceClass = Class.forName(telephonyInterface.getClass().getName());
                    Method methodEndCall = telephonyInterfaceClass.getDeclaredMethod("endCall");

                    // 	Invoke endCall()/
                    methodEndCall.invoke(telephonyInterface);

                    Toast tag = Toast.makeText(context, context.getString(R.string.incall_incoming_gsm_call) + incomingNumber, Toast.LENGTH_LONG);
                    tag.show();
                }
                catch (Exception e){
                    Log.ef(TAG, e, "Error intercepting call: %s", e.toString());
                }
                break;
            default:
                break;
        }
    }
}
