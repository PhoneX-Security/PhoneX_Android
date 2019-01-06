package net.phonex.soap.accountSettings;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Should be called when user is logged off
 * Created by miroc on 8.10.15.
 */
public class SetUserAsLoggedOffCall extends AccountSettingsUpdateCall {

    public SetUserAsLoggedOffCall(Context context) {
        super(context);
    }

    @Override
    protected JSONObject getSettingsUpdateJson() throws JSONException {
        JSONObject areq = new JSONObject();
        areq.put("loggedIn", false);
        return areq;
    }
}
