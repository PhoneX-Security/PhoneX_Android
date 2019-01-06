package net.phonex.login;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import net.phonex.util.Log;
import net.phonex.util.ProfileUtils;

/**
 *
 * Created by miroc on 17.2.16.
 */
public class LogoutIntentService extends IntentService {
    private static final String TAG = "LogoutIntentService";
    private static final String CANNOT_BE_WAKED_BY_GCM = "cannot_be_waked_by_gcm";

    public static Intent createIntent(Context context, boolean cannotBeWakedByGcm){
        Intent intent = new Intent(context, LogoutIntentService.class);
        intent.putExtra(CANNOT_BE_WAKED_BY_GCM, cannotBeWakedByGcm);
        return intent;
    }

    public LogoutIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.inf(TAG, "onHandleIntent; intent=%s", intent);
        boolean cannotBeWakedByGcm = intent.getBooleanExtra(CANNOT_BE_WAKED_BY_GCM, false);
        ProfileUtils.secureQuit(this, cannotBeWakedByGcm);
    }
}

