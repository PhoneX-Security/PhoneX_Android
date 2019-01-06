package net.phonex.soap.accountSettings;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Soap call update recovery email
 * Created by miroc on 8.10.15.
 */
public class SetRecoveryEmailCall extends AccountSettingsUpdateCall {
    private String recoveryEmail;

    public SetRecoveryEmailCall(Context context, String recoveryEmail) {
        super(context);
        this.recoveryEmail = recoveryEmail;
    }

    @Override
    protected JSONObject getSettingsUpdateJson() throws JSONException {
        JSONObject areq = new JSONObject();
        areq.put("recoveryEmail", recoveryEmail);
        return areq;
    }
}
