package net.phonex.sip;

import android.os.Bundle;
import android.text.TextUtils;

import net.phonex.util.Log;

import net.phonex.xv.Xvi;
import net.phonex.xv.pj_pool_t;
import net.phonex.xv.pj_str_t;
import net.phonex.xv.XviConstants;
import net.phonex.xv.pjsua_msg_data;

/**
 * Created by ph4r05 on 7/28/14.
 */
public class PjUtils {
    private static final String TAG="PjUtils";

    /**
     * Conterts Java boolean to Pj boolean
     * @param b integer representing boolean.
     * @return
     */
    static int toPjBool(boolean b){
        return b ? Xvi.PJ_TRUE : Xvi.PJ_FALSE;
    }

    /**
     * Converts Pj string to a Java string.
     * @param pjStr
     * @return
     */
    public static String pjStrToString(pj_str_t pjStr) {
        // Sometimes String allocated in JNI is garbage collected later - race condition that happens very few times, but has deadly consequences
        // This happens on Android 5+ devices, for unknown reasons, probably error in JNI code. Therefore be defensive and copy string to avoid its garbage collection.
        // Quite hacky indedd, TODO: find better solution
        String s = Xvi.conv_pj_str_t_to_jstring(pjStr);
        if (s != null){
            return new String(s);
        } else {
            return s;
        }
    }

    /**
     * Adds extra headers to the message.
     *
     * @param msgData   Message to add the extra headers to.
     * @param pool      Memory pool for extra headers.
     * @param extraHeaders  Bundle containing extra headers.
     */
    public static void addExtraHeaders(pjsua_msg_data msgData, pj_pool_t pool, Bundle extraHeaders){
        if (extraHeaders == null) {
            return;
        }

        for (String key : extraHeaders.keySet()) {
            try {
                String value = extraHeaders.getString(key);
                if (TextUtils.isEmpty(value)) {
                    continue;
                }

                int res = Xvi.sipstack_msg_data_add_string_hdr(pool, msgData, Xvi.pj_str_copy(key), Xvi.pj_str_copy(value));
                if (res == XviConstants.PJ_SUCCESS) {
                    Log.ef(TAG, "Failed to add header [%s: %s]", key, value);
                }
            } catch (Exception e) {
                Log.ef(TAG, e, "Exception adding header [%s]", key);
            }
        }
    }
}
