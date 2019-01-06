/**
 * This file contains relicensed code from Apache copyright of 
 * Copyright (C) 2011 The Android Open Source Project
 */

package net.phonex.util.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;

import net.phonex.core.Constants;


/**
 * Utility methods for dealing with URIs.
 */
public class UriUtils {
    private final static String SCHEME_IMTO = "imto";
    private final static String SCHEME_TEL = "tel";
    private final static String SCHEME_SMSTO = "smsto";
    private final static String AUTHORITY_CSIP = Constants.PROTOCOL_CSIP;
    private final static String AUTHORITY_SIP = Constants.PROTOCOL_SIP;
    private final static String AUTHORITY_SKYPE = "skype";

    /** Static helper, not instantiable. */
    private UriUtils() {}

    /** Checks whether two URI are equal, taking care of the case where either is null. */
    public static boolean areEqual(Uri uri1, Uri uri2) {
        if (uri1 == null && uri2 == null) {
            return true;
        }
        if (uri1 == null || uri2 == null) {
            return false;
        }
        return uri1.equals(uri2);
    }

    /** Parses a string into a URI and returns null if the given string is null. */
    public static Uri parseUriOrNull(String uriString) {
        if (uriString == null) {
            return null;
        }
        return Uri.parse(uriString);
    }

    /** Converts a URI into a string, returns null if the given URI is null. */
    public static String uriToString(Uri uri) {
        return uri == null ? null : uri.toString();
    }
    
    /**
     * Detect if phone number is a uri
     * @param number The number to detect
     * @return true if look like a URI instead of a phone number 
     */
    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped. (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    /**
     * Returns number/sip address to call.
     * @param it
     * @param ctxt
     * @return
     */
    public static String extractContactFromIntent(Intent it, Context ctxt) {
        if(it == null) {
            return null;
        }

        String phoneNumber = null;
        String scheme = null;
        final String action = it.getAction();
        final Uri data = it.getData();

        // If data is not null, try to extract scheme (protocol).
        if (data!=null){
            scheme = data.getScheme();
            if (scheme!=null){
                scheme = scheme.toLowerCase();
            }
        }

        if(data != null && action != null) {
            phoneNumber = PhoneNumberUtils.getNumberFromIntent(it, ctxt);
        }

        // If null then nothing to do more.
        if (action==null || data==null){
            return phoneNumber;
        }

        if (phoneNumber != null){
            if(SCHEME_SMSTO.equalsIgnoreCase(scheme) || SCHEME_TEL.equalsIgnoreCase(scheme)) {
                phoneNumber = PhoneNumberUtils.stripSeparators(phoneNumber);
            }
        } else if (Intent.ACTION_SENDTO.equalsIgnoreCase(action)) {
            if (SCHEME_IMTO.equals(scheme)) {
                String auth = data.getAuthority();
                if (AUTHORITY_CSIP.equals(auth) ||
                        AUTHORITY_SIP.equals(auth) ||
                        AUTHORITY_SKYPE.equals(auth) ) {
                    phoneNumber = data.getLastPathSegment();
                }
            }else if (SCHEME_SMSTO.equals(scheme)) {
                phoneNumber = PhoneNumberUtils.stripSeparators(data.getSchemeSpecificPart());
            }
        } else {
           phoneNumber = data.getSchemeSpecificPart();
        }
        
        return phoneNumber;
    }

}
