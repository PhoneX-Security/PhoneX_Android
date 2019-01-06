package net.phonex.service.xmpp.customIq;

import net.phonex.util.Log;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.json.JSONException;

/**
 * Created by miroc on 26.1.16.
 */
public abstract class JsonIQ extends IQ {

    private static final String TAG = "JsonIQ";

    public JsonIQ() {
        setType(Type.SET);
    }

    protected abstract String getJson() throws JSONException;

    @Override
    public CharSequence getChildElementXML() {
        XmlStringBuilder var1 = new XmlStringBuilder();
        var1.halfOpenElement("config")
                .xmlnsAttribute("urn:xmpp:ppush")
                .rightAngelBracket();


        var1.openElement("version")
                .append("1")
                .closeElement("version");

        var1.openElement("json");

        try {
            var1.append(getJson());
        } catch (JSONException e) {
            Log.ef(TAG, e, "Error while getting Json content.");
        }

        var1.closeElement("json");
        var1.closeElement("config");
        return var1;
    }
}
