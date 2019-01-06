package net.phonex.ui.inapp;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by miroc on 11.11.15.
 */
public class DeveloperPayload {
    public interface Items{
        String TYPE = "type";
        String SKU = "sku";
        String SIP = "sip";
    }

    private String type;
    private String sku;
    private String sip;

    public static DeveloperPayload fromJson(String payloadJson) throws JSONException {
        JSONObject jsonObject = new JSONObject(payloadJson);
       return new DeveloperPayload(
               jsonObject.getString(Items.TYPE),
               jsonObject.getString(Items.SKU),
               jsonObject.getString(Items.SIP)
       );
    }

    public DeveloperPayload(String type, String sku, String sip) {
        this.type = type;
        this.sku = sku;
        this.sip = sip;
    }

    public String toJson() throws JSONException {
        JSONObject payloadJson = new JSONObject();
        payloadJson.put(Items.SIP, sip);
        payloadJson.put(Items.SKU, sku);
        payloadJson.put(Items.TYPE, type);
        return payloadJson.toString();
    }

    public String getType() {
        return type;
    }

    public String getSku() {
        return sku;
    }

    public String getSip() {
        return sip;
    }
}
