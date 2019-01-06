package net.phonex.service.xmpp;

import net.phonex.service.xmpp.customIq.CListSyncPushMessage;
import net.phonex.service.xmpp.customIq.DhUsePushMessage;
import net.phonex.service.xmpp.customIq.LicenseCheckPushMessage;
import net.phonex.service.xmpp.customIq.NewCertPushMessage;
import net.phonex.service.xmpp.customIq.PairingRequestPushMessage;
import net.phonex.service.xmpp.customIq.PushIQ;
import net.phonex.util.Log;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * Created by miroc on 11.3.15.
 */
public class IQPacketListener implements PacketListener{
    private static final String TAG = "IQPacketListener";
    private WeakReference<XMPPConnection> con;
    private PushMessagesListener listener;

    public interface PushMessagesListener {
        void onCListSyncPush(CListSyncPushMessage message);
        void onNewCertificatePush(NewCertPushMessage pushMessage);
        void onDhUse(DhUsePushMessage message);
        void onLicenseCheckPush(LicenseCheckPushMessage message);
        void onPairingRequestPush(PairingRequestPushMessage message);
    }

    public IQPacketListener(XMPPConnection con) {
        this.con = new WeakReference<>(con);
    }

    public void setListener(PushMessagesListener listener) {
        this.listener = listener;
    }

    @Override
    public void processPacket(Packet packet) throws SmackException.NotConnectedException {
        if (packet == null){
            Log.wf(TAG, "processPacket; packet is null");
            return;
        }

        if(packet instanceof PushIQ) {
            PushIQ pushIQ = (PushIQ) packet;
            Log.vf(TAG, "pushIQ packet is being processed");

            IQ customJsonResultIQ = IQ.createResultIQ(pushIQ);

            XMPPConnection xmppConnection = con.get();
            if (xmppConnection!=null){
                try {
                    // TODO reconnect xmpp and send result again
                    Log.vf(TAG, "pushIQ is being sent back");
                    xmppConnection.sendPacket(customJsonResultIQ);
                } catch (SmackException.NotConnectedException exception){
                    // log error, but processing continues
                    Log.wf(TAG, exception, "response sending exception");
                }
            }

            processJsonIQ(pushIQ);
        }
    }

    private void processJsonIQ(PushIQ packet){
        try {
            JSONObject json = new JSONObject(packet.getJson());
            JSONArray msgs = json.getJSONArray("msgs");

            int msgCount = msgs.length();
            Log.vf(TAG, "processJsonIQ; number of messages [%d]", msgCount);

            for (int i = 0; i<msgCount; i++){
                JSONObject msg= (JSONObject) msgs.get(i);
                processJsonPushMsg(msg);
            }
        } catch (Exception e){
            Log.ef(TAG, e, "processJsonIQ; cannot process JsonIQ");
        }
    }

    private void processJsonPushMsg(JSONObject msg){
        Log.df(TAG, "processing JSON Push [%s]", msg);
        try {
            String pushAction = msg.getString("push");
            long timestamp = msg.getLong("tstamp");

            switch (pushAction){
                case (CListSyncPushMessage.ACTION): {
                    CListSyncPushMessage message = new CListSyncPushMessage(timestamp);

                    if (listener != null) {
                        listener.onCListSyncPush(message);
                    }
                    break;
                }

                case (NewCertPushMessage.ACTION): {
                    Log.df(TAG, "New certificate message, msg: %s", msg);
                    JSONObject data = msg.getJSONObject("data");
                    String hashPrefix = data.getString(NewCertPushMessage.FIELD_CERT_HASH_PREFIX);
                    String notBefore = data.getString(NewCertPushMessage.FIELD_NOT_BEFORE);

                    NewCertPushMessage pushMessage = new NewCertPushMessage(timestamp, Long.valueOf(notBefore), hashPrefix);

                    if (listener != null) {
                        listener.onNewCertificatePush(pushMessage);
                    }

                    break;
                }

                case (DhUsePushMessage.ACTION): {
                    Log.df(TAG, "Dh use, msg: %s", msg);
                    DhUsePushMessage message = new DhUsePushMessage(timestamp);

                    if (listener != null) {
                        listener.onDhUse(message);
                    }

                    break;
                }

                case (LicenseCheckPushMessage.ACTION):
                    Log.df(TAG, "LicenseCheck message, msg: %s", msg);
                    if (listener != null){
                        listener.onLicenseCheckPush(new LicenseCheckPushMessage(timestamp));
                    }
                    break;

                case (PairingRequestPushMessage.ACTION):
                    Log.df(TAG, "PairingRequest message, msg: %s", msg);
                    if (listener != null){
                        listener.onPairingRequestPush(new PairingRequestPushMessage(timestamp));
                    }
                    break;
            }

        } catch (JSONException e) {
            Log.ef(TAG, "processJsonPushMsg; cannot process msg [%s]", msg);
        } catch (Exception e) {
            Log.ef(TAG, "processJsonPushMsg; cannot process msg [%s]", msg);
        }

    }
}
