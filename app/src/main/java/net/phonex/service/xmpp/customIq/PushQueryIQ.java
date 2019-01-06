package net.phonex.service.xmpp.customIq;

import org.jivesoftware.smack.packet.IQ;

/**
 * Created by dusanklinec on 13.03.15.
 */
public class PushQueryIQ extends IQ {
    public static final String ELEMENT = "pushQuery";
    public static final String NAMESPACE = "urn:xmpp:phx";

    @Override
    public CharSequence getChildElementXML() {
        return "<" + ELEMENT + " xmlns=\'" + NAMESPACE + "\' />";
    }
}
