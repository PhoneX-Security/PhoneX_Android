package net.phonex.service.xmpp.customIq;

import net.phonex.gcm.entities.GcmMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Acknowledgment for Gcm push
 * Created by miroc on 26.1.16.
 */
public class GcmMessagesAcksIQ extends JsonIQ {

    private final List<GcmMessage> messages;

    public GcmMessagesAcksIQ(List<GcmMessage> messages) {
        super();
        this.messages = messages;
    }

    protected String getJson() throws JSONException {
        JSONArray acks = new JSONArray();
        for (GcmMessage message : messages){
            JSONObject ack = new JSONObject();
            ack.put("t", message.getTimestamp());
            ack.put("p", message.getPush());
            acks.put(ack);
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("acks", acks);
        jsonObject.put("timestamp", System.currentTimeMillis());
        return jsonObject.toString();
    }
}
