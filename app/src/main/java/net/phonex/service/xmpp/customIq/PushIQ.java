package net.phonex.service.xmpp.customIq;

import org.jivesoftware.smack.packet.IQ;

/**
 * Created by miroc on 11.3.15.
 */
public class PushIQ extends IQ {
    public static final String ELEMENT = "push";
    public static final String NAMESPACE = "urn:xmpp:phx";

    private String json;

    public PushIQ(String json) {
        this.json = json;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    @Override
    public CharSequence getChildElementXML() {
        return "<" + ELEMENT + " xmlns=\'" + NAMESPACE + "\' />";
    }

    @Override
    public String toString() {
        return "PushIQ{" +
                "json='" + json + '\'' +
                "from='" + getFrom() + '\'' +
                "to='" + getTo() + '\'' +
                "id='" + getPacketID() + '\'' +
                '}';
    }
}
