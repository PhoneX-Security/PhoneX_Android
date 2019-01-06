package net.phonex.util.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.telephony.TelephonyManager;

import net.phonex.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides static functions to quickly test the capabilities of this device. The static
 * members are not safe for threading
 * Original from  com.android.contacts.util.PhoneCapabilityTester
 */
public final class PhoneCapabilityTester {
    private static boolean sIsInitialized;
    private static boolean sIsPhone;

    /**
     * Tests whether the Intent has a receiver registered. This can be used to show/hide
     * functionality (like Phone, SMS)
     */
    public static boolean isIntentRegistered(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> receiverList = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receiverList.size() > 0;
    }
    
    /**
     * Resolve the intent as a activity receiver and return all activities related
     * @param ctxt The application content
     * @param i the intent to resolve
     * @return List of resolved info
     */
    public static List<ResolveInfo> getPossibleActivities(Context ctxt, Intent i) {
        PackageManager pm = ctxt.getPackageManager();
        try {
            return pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER);
        } catch (NullPointerException e) {
            return new ArrayList<ResolveInfo>();
        }
    }

    /**
     * Returns true if this device can be used to make phone calls
     */
    public static boolean isPhone(Context context) {
        if (!sIsInitialized) {
            initialize(context);
        }
        // Is the device physically capabable of making phone calls?
        return sIsPhone;
    }

    private static void initialize(Context context) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        sIsPhone = (telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE);
        sIsInitialized = true;
    }
    
    public static void deinit() {
        sIsInitialized = false;
    }

}
