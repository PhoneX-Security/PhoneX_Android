/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.phonex.gcm;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.android.gms.gcm.GcmListenerService;
import com.google.gson.Gson;

import net.phonex.autologin.AutoLoginManager;
import net.phonex.core.Intents;
import net.phonex.db.entity.SipProfile;
import net.phonex.gcm.entities.GcmJson;
import net.phonex.gcm.entities.GcmMessage;
import net.phonex.gcm.entities.Phx;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.ArrayList;
import java.util.List;

public class MyGcmListenerService extends GcmListenerService {

    private static final String TAG = "MyGcmListenerService";

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    @Override
    public void onMessageReceived(String from, Bundle data) {
        Log.df(TAG, "From: %s", from);
        Log.df(TAG, "Data: %s", data);

        String json = data.getString("phxroot");
        Log.df(TAG, "json=%s", json);

        List<GcmMessage> msgs = parseMessageListFromJson(json);
        if (msgs == null || msgs.size() == 0){
            Log.wf(TAG, "GcmMessages null or size() == 0");
            return;
        }

        // check if user is logged in by loading current sip account
        SipProfile currentProfile = SipProfile.getCurrentProfile(this);
        if (currentProfile == null){
            Log.df(TAG, "User not logged in, save messages and initiate login");
            saveMessages(json);
            AutoLoginManager.triggerLoginFromSavedState(this);
        } else {
            Log.df(TAG, "User logged in, send messages via intent for processing");
            processMessages(msgs);
        }
    }

    private void saveMessages(String gcmMessagesInJson) {
        // Store messages in json and retrieve later
        PreferencesConnector preferencesConnector = new PreferencesConnector(this);
        preferencesConnector.setString(PhonexConfig.GCM_MESSAGES_JSON, gcmMessagesInJson);
    }

    private void processMessages(@NonNull List<GcmMessage> msgs) {
        ArrayList<GcmMessage> msgsArrayList = new ArrayList<>(msgs);

        // restart of password may require sip stack to be restarted
        Intent intent = new Intent(Intents.ACTION_GCM_MESSAGES_RECEIVED);
        intent.putParcelableArrayListExtra(Intents.EXTRA_GCM_MESSAGES, msgsArrayList);
        MiscUtils.sendBroadcast(this, intent);
    }

    public static List<GcmMessage> parseMessageListFromJson(String json){
        if (TextUtils.isEmpty(json)){
            Log.wf(TAG, "Null json.");
            return null;
        }

        Gson gson = new Gson();
        GcmJson gcmJson = gson.fromJson(json, GcmJson.class);
        if (gcmJson == null){
            Log.wf(TAG, "Null GcmJson object");
            return null;
        }

        Phx phx = gcmJson.getPhx();
        if (phx == null){
            Log.wf(TAG, "Null Phx object");
            return null;
        }

        return phx.getMsg();
    }

}
