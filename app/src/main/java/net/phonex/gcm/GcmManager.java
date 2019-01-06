package net.phonex.gcm;

import net.phonex.db.entity.SipProfile;
import net.phonex.gcm.entities.GcmMessage;
import net.phonex.pref.PhonexConfig;
import net.phonex.pref.PreferencesConnector;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Process gcm messages - send acknowledgment to push server
 * There are two distinctive cases:
 * 1. user is online when receiving gcms - in this case, messages are recevied via onMessagesReceived()
 * 2. user is offline - messages are stored in SharedPreferences, login is initiated. After login is finished,
 * this object loads them from the preferences, and sends acknowledgements.
 *
 * Created by miroc on 11.2.16.
 */
public class GcmManager {
    private static final String TAG = "GcmManager";
    private final XService xService;
    private ExecutorService executor;

    private Map<String, Long> typeTimestampMap;

    public GcmManager(XService xService) {
        this.xService = xService;
        typeTimestampMap = new HashMap<>();
        executor = Executors.newSingleThreadExecutor();
    }

    public void onMessagesReceived(final List<GcmMessage> messages){
        Log.df(TAG, "onMessagesReceived");
        if (messages == null){
            Log.wf(TAG, "onMessagesReceived; no messages");
            return;
        }
        executor.submit(() -> {
            List<GcmMessage> unacknowledgedMessages = mergeWithCurrent(messages);
            SipProfile currentProfile = SipProfile.getCurrentProfile(xService);
            if (currentProfile == null){
                Log.ef(TAG, "onMessagesReceived; null current profile");
                return;
            }

            xService.getXmppManager().acknowledgeGcmMessages(currentProfile.getSip(), unacknowledgedMessages);
        });
    }

    public List<GcmMessage> retrieveMessagesToAcknowledge() {
        Log.df(TAG, "retrieveMessagesToAcknowledge;");

        List<GcmMessage> msgs = popStoredMessages();
        if (msgs == null) {
            return null;
        }
        return mergeWithCurrent(msgs);
    }

    private List<GcmMessage> popStoredMessages(){
        PreferencesConnector prefs = xService.getPrefs();
        String json = prefs.getString(PhonexConfig.GCM_MESSAGES_JSON);
        prefs.setString(PhonexConfig.GCM_MESSAGES_JSON, null);
        return MyGcmListenerService.parseMessageListFromJson(json);
    }

    /**
     * Save un-acknowledged messages into the internal map and return them in a list
     * @param messages all new messages
     * @return list of currently saved messages
     */
    private List<GcmMessage> mergeWithCurrent(final List<GcmMessage> messages){
        Map<String, GcmMessage> messagesToAcknowledge = new HashMap<>();

        for (GcmMessage message : messages){
            Long lastSavedTimestamp = typeTimestampMap.get(message.getPush());
            if (lastSavedTimestamp == null || lastSavedTimestamp < message.getTimestamp()){
                typeTimestampMap.put(message.getPush(), message.getTimestamp());
                messagesToAcknowledge.put(message.getPush(), message);
            }
        }
        return Lists.newArrayList(messagesToAcknowledge.values());
    }
}
