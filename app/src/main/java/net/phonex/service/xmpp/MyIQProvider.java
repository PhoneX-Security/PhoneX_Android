package net.phonex.service.xmpp;

import net.phonex.service.xmpp.customIq.PushIQ;
import net.phonex.util.Log;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * Parser for IQ packets, containing XMPP push messages from the server.
 * Created by miroc on 11.3.15.
 */
public class MyIQProvider implements IQProvider{
    private static final String TAG = "MyIQProvider";

    public IQ parseIQ(XmlPullParser parser) throws Exception {
        Log.vf(TAG, "SMACK parseIQ");
        String json =null;
        String from = null;
        String to = null;
        String id =  null;

        boolean done = false;
        while (!done) {
            int eventType = parser.next();


            switch (eventType){
                case XmlPullParser.START_TAG:

                    switch (parser.getName()){
                        case "iq":
                            from = parser.getAttributeValue("", "from");
                            to = parser.getAttributeValue("", "to");
                            id = parser.getAttributeValue("", "id");

                            break;
                        case "json":
                            int token = parser.nextToken();
                            while(token!=XmlPullParser.CDSECT){
                                token = parser.nextToken();
                            }
                            String cdata = parser.getText();
                            Log.inf(TAG, "Smack parsed json data [%s]", cdata);
                            json = cdata;

                            break;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    // push is the end tag, we can finish parsing
                    if ("push".equals(parser.getName())){
                        done = true;
                    }
                    break;
            }
        }

        PushIQ pushIQ = new PushIQ(json);
        pushIQ.setTo(to);
        pushIQ.setFrom(from);
        pushIQ.setPacketID(id);
        return pushIQ;
    }
}


