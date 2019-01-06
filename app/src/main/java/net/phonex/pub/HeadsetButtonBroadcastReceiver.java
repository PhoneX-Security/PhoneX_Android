package net.phonex.pub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import net.phonex.sip.PjCallback;
import net.phonex.util.Log;

public class HeadsetButtonBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "HeadsetButtonBroadcastReceiver";
    private static PjCallback uaReceiver = null;
    public static void setService(PjCallback aUAReceiver) {
        uaReceiver = aUAReceiver;
    }

    @Override
	public void onReceive(Context context, Intent intent) {
		Log.v(TAG, "onReceive()");

        // If we are not able to handle this or not interested in this, exit.
		if(uaReceiver == null || Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())==false) {
			return;
		}

        KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event != null &&
            event.getAction() == KeyEvent.ACTION_DOWN &&
            event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK &&
            uaReceiver.handleHeadsetButton()) {
                // This will abort broadcast from spreading.
                // E.g., Player wont start playing.
                abortBroadcast();
        }
	}
}
