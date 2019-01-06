package net.phonex.service;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import net.phonex.ui.CallErrorActivity;

import java.lang.ref.WeakReference;

/**
 * Created by ph4r05 on 7/27/14.
 */
class ErrorMessageSvcHandler extends Handler {
    private final WeakReference<XService> s;

    public ErrorMessageSvcHandler(XService xService) {
        s = new WeakReference<XService>(xService);
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        XService xService = s.get();
        if (xService == null) {
            return;
        }

        Intent intent = new Intent(xService, CallErrorActivity.class);
        if (msg.what == XService.TOAST_MESSAGE) {
            intent.putExtra("TYPE", CallErrorActivity.GENERAL_ERROR);
            intent.putExtra("MESSAGE", (String) msg.obj);
        } else if (msg.what == XService.TOAST_MESSAGE_CALL_ENDED) {
            intent.putExtra("TYPE", CallErrorActivity.CALL_ENDED_ERROR);
            intent.putExtra("ERROR_OBJECT", (CallErrorActivity.CallErrorMessage) msg.obj);
        } else if (msg.what == XService.TOAST_MESSAGE_CUSTOM_ERROR) {
            intent.putExtra("TYPE", CallErrorActivity.CUSTOM_ERROR);
            intent.putExtra("MESSAGE", (String) msg.obj);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        xService.startActivity(intent);
    }
}
