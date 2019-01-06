
package net.phonex.ui.inapp;

import android.content.Context;

import net.phonex.db.entity.SipProfile;
import net.phonex.inapp.Base64;
import net.phonex.inapp.Base64DecoderException;
import net.phonex.inapp.Purchase;
import net.phonex.inapp.PurchaseSettings;
import net.phonex.util.Log;

import org.json.JSONException;

/**
 * Created by miroc on 11.11.15.
 */
public class PurchaseUtils {
    private static final String TAG = "PurchaseUtils";

    /** Verifies the developer payload of a purchase. */
    static boolean verifyDeveloperPayload(Context context, Purchase purchase) {

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        DeveloperPayload developerPayload;
        try {
            developerPayload = DeveloperPayload.fromJson(purchase.getDeveloperPayload());
        } catch (JSONException e) {
            Log.ef(TAG, e, "cannot parse developer payload");
            return false;
        }

        SipProfile profile = SipProfile.getCurrentProfile(context);
        if (profile == null){
            Log.wf(TAG, "null profile, cannot verify payload");
            return false;
        }

        // Currently only verify that purchase was done by the same user
        return developerPayload.getSip().equals(profile.getSip(false));
    }

    private static String xorEncrypt(String input, String key) {
        byte[] inputBytes = input.getBytes();
        int inputSize = inputBytes.length;

        byte[] keyBytes = key.getBytes();
        int keySize = keyBytes.length - 1;

        byte[] outBytes = new byte[inputSize];
        for (int i=0; i<inputSize; i++) {
            outBytes[i] = (byte) (inputBytes[i] ^ keyBytes[i % keySize]);
        }

        return Base64.encode(outBytes);
    }

    private static String xorDecrypt(String input, String key) throws Base64DecoderException {
//        byte[] inputBytes = Base64.decode(input, Base64.DEFAULT);
        byte[] inputBytes = Base64.decode(input);
        int inputSize = inputBytes.length;

        byte[] keyBytes = key.getBytes();
        int keySize = keyBytes.length - 1;

        byte[] outBytes = new byte[inputSize];
        for (int i=0; i<inputSize; i++) {
            outBytes[i] = (byte) (inputBytes[i] ^ keyBytes[i % keySize]);
        }

        return new String(outBytes);
    }

    public static String retrievePublicKey() throws Base64DecoderException {
        return xorDecrypt(PurchaseSettings.ENCRYPTED_PUBLIC_KEY, PurchaseSettings.ENCRYPTION_KEY);
    }
}
