package net.phonex.service;

import android.telephony.PhoneStateListener;

import net.phonex.util.Log;

import java.lang.ref.WeakReference;

/**
 * Simple listener for Telephony changes.
 * Created by dusanklinec on 11.11.15.
 */
class ServicePhoneStateReceiver extends PhoneStateListener {
    private static final String TAG = "ServicePhoneStateReceiver";

    private WeakReference<XService> xService;
    private boolean ignoreFirstCallState = true;

    public ServicePhoneStateReceiver(XService xService) {
        this.xService = new WeakReference<>(xService);
    }

    @Override
    public void onCallStateChanged(final int state, final String incomingNumber) {
        XService svc = xService.get();
        if (svc == null){
            Log.e(TAG, "Service is null");
            return;
        }

        if (!ignoreFirstCallState) {
            Log.df(TAG, "Call state has changed !%s: %s", state, incomingNumber);
            svc.getHandler().execute(new SvcRunnable("onCallStateChanged") {

                @Override
                protected void doRun() throws XService.SameThreadException {
                    svc.onGSMStateChanged(state, incomingNumber);

                    if (svc.getPjManager() != null) {
                        svc.getPjManager().onGSMStateChanged(state, incomingNumber);
                    }
                }
            });
        } else {
            ignoreFirstCallState = false;
        }

        super.onCallStateChanged(state, incomingNumber);
    }
}
