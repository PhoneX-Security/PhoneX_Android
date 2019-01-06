package net.phonex.service.xmpp.customIq;

import net.phonex.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * XMPP packet containing GCM registration token (received during gcm registration - typically during application start)
 * Token should be stored on a server and used when calling GCM api by our server.
 * Created by miroc on 26.1.16.
 */
public class SendGcmTokenIQ extends JsonIQ {

    private final String token;
    private final String appVersion;
    private final String osVersion;
    private List<String> langs;

    /**
     * Create new GcmTokenIQ xmpp message
     * @param token GCM token
     * @param appVersion Simple app version like 1.8.2
     * @param osVersion Android Version (e.g. 5.0.1)
     * @param langs array of supported langs by current client
     */
    public SendGcmTokenIQ(String token, String appVersion, String osVersion, List<String> langs) {
        super();
        this.token = token;
        this.appVersion = appVersion;
        this.osVersion = osVersion;
        this.langs = langs;
    }

    protected String getJson() throws JSONException {
        JSONArray jsonArrayLangs = new JSONArray(langs);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("platform", "android");
        jsonObject.put("token", token);
        jsonObject.put("version", "1");
        jsonObject.put("app_version", appVersion);
        jsonObject.put("os_version", osVersion);
        jsonObject.put("langs", jsonArrayLangs);
        jsonObject.put("debug", BuildConfig.DEBUG ? 1 : 0);
        return jsonObject.toString();
    }

    @Override
    public String toString() {
        return "SendGcmTokenIQ{" +
                "token='" + token + '\'' +
                ", appVersion='" + appVersion + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", langs=" + langs +
                '}';
    }
}
