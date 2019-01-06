package net.phonex.core;

import android.net.Uri;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for Sip uri manipulation in java space.
 * Allows to parse sip uris and check it.
 */
public final class SipUri {

    private final static String DIGIT_NBR_RULE = "^[0-9\\-#\\+\\*\\(\\)]+$";
    private final static String SIP_SCHEME_RULE = "sip(?:s)?|tel";
    private final static Pattern SIP_CONTACT_PATTERN = Pattern
            .compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?(" + SIP_SCHEME_RULE + "):([^@]+)@([^>]+)(?:>)?$");
    private final static Pattern SIP_HOST_PATTERN = Pattern
            .compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?(" + SIP_SCHEME_RULE + "):([^@>]+)(?:>)?$");
    private final static Pattern SIP_CONTACT_ADDRESS_PATTERN = Pattern
            .compile("^([^@:]+)@([^@]+)$");
    private final static Pattern SIP_CONTACT_EASY_PATTERN = Pattern
            .compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?sip(?:s)?:([^@]*@[^>]*)(?:>)?", Pattern.CASE_INSENSITIVE);
    private final static Pattern SIP_URI_PATTERN = Pattern
            .compile("^(sip(?:s)?):(?:[^:]*(?::[^@]*)?@)?([^:@]*)(?::([0-9]*))?$", Pattern.CASE_INSENSITIVE);

    // Contact related
    private SipUri() {
        // Singleton
    }

    /**
     * Parse a sip contact
     *
     * @param sipUri string sip contact
     * @return a ParsedSipContactInfos which contains uri parts. If not match
     * return the object with blank fields
     */
    public static ParsedSipContactInfos parseSipContact(String sipUri) {
        ParsedSipContactInfos parsedInfos = new ParsedSipContactInfos();

        if (!TextUtils.isEmpty(sipUri)) {
            Matcher m = SIP_CONTACT_PATTERN.matcher(sipUri);
            if (m.matches()) {
                parsedInfos.displayName = Uri.decode(m.group(1).trim());
                parsedInfos.domain = m.group(4);
                parsedInfos.userName = Uri.decode(m.group(3));
                parsedInfos.scheme = m.group(2);
            } else {
                // Try to consider that as host
                m = SIP_HOST_PATTERN.matcher(sipUri);
                if (m.matches()) {
                    parsedInfos.displayName = Uri.decode(m.group(1).trim());
                    parsedInfos.domain = m.group(3);
                    parsedInfos.scheme = m.group(2);
                } else {
                    m = SIP_CONTACT_ADDRESS_PATTERN.matcher(sipUri);
                    if (m.matches()) {
                        parsedInfos.userName = Uri.decode(m.group(1));
                        parsedInfos.domain = m.group(2);
                    } else {
                        // Final fallback, we have only a username given
                        parsedInfos.userName = sipUri;
                    }
                }
            }
        }

        return parsedInfos;
    }

    /**
     * Return what should be display as caller id for this sip uri This is the
     * merged and fancy way fallback to uri or user name if needed
     *
     * @param uri the uri to display
     * @return the simple display
     */
    public static String getDisplayedSimpleContact(CharSequence uri) {
        // Reformat number
        if (uri != null) {
            String remoteContact = uri.toString();
            ParsedSipContactInfos parsedInfos = parseSipContact(remoteContact);

            if (!TextUtils.isEmpty(parsedInfos.displayName)) {
                // If available prefer the display name
                remoteContact = parsedInfos.displayName;
            } else if (!TextUtils.isEmpty(parsedInfos.userName)) {
                // Else, if available choose the username
                remoteContact = parsedInfos.userName;
            }
            return remoteContact;
        }
        return "";
    }

    /**
     * Check if username is an phone tel
     *
     * @param phone username to check
     * @return true if look like a phone number
     */
    public static boolean isPhoneNumber(String phone) {
        return (!TextUtils.isEmpty(phone) && Pattern.matches(DIGIT_NBR_RULE, phone));
    }

    /**
     * Get extract a phone number from sip uri if any available
     *
     * @param uriInfos the parsed information of the uri obtained with {@link #parseSipContact(String)}
     * @return null if no phone number detected. The phone number else.
     */
    public static String getPhoneNumber(ParsedSipContactInfos uriInfos) {
        if (uriInfos == null) {
            return null;
        }
        if (isPhoneNumber(uriInfos.userName)) {
            return uriInfos.userName;
        } else if (isPhoneNumber(uriInfos.displayName)) {
            return uriInfos.displayName;
        }
        return null;
    }

    /**
     * Transform sip uri into something that doesn't depend on remote display
     * name
     * For example, if you give "Display Name" <sip:user@domain.com>
     * It will return sip:user@domain.com
     *
     * @param sipContact full sip uri
     * @return simplified sip uri
     */
    public static String getCanonicalSipContact(String sipContact) {
        return getCanonicalSipContact(sipContact, true);
    }

    /**
     * Transform sip uri into something that doesn't depend on remote display
     * name
     *
     * @param sipContact    full sip uri
     * @param includeScheme whether to include scheme in case of username
     * @return the canonical sip contact <br/>
     * Example sip:user@domain.com <br/>
     * or user@domain.com (if include scheme is false)
     */
    public static String getCanonicalSipContact(String sipContact, boolean includeScheme) {
        StringBuilder sb = new StringBuilder();
        if (TextUtils.isEmpty(sipContact)) {
            return sb.toString();
        }

        Matcher m = SIP_CONTACT_PATTERN.matcher(sipContact);
        boolean hasUsername = false;
        boolean isHost = false;

        if (m.matches()) {
            hasUsername = true;
        } else {
            m = SIP_HOST_PATTERN.matcher(sipContact);
            isHost = true;
        }

        if (m.matches()) {
            if (includeScheme || isHost) {
                sb.append(m.group(2));
                sb.append(":");
            }
            sb.append(m.group(3));
            if (hasUsername) {
                sb.append("@");
                sb.append(m.group(4));
            }
        } else {
            m = SIP_CONTACT_ADDRESS_PATTERN.matcher(sipContact);
            if (m.matches()) {
                if (includeScheme) {
                    sb.append("sip:");
                }
                sb.append(sipContact);
            } else {
                sb.append(sipContact);
            }
        }

        return sb.toString();
    }

    /**
     * Removes SIP scheme from the username if there is some.
     * @param userName
     * @return
     */
    public static String stripSipScheme(String userName){
        if (userName==null){
            return null;
        }

        userName = userName.replace("csips:", "");
        userName = userName.replace("sips:", "");
        userName = userName.replace("cip:", "");
        userName = userName.replace("sip:", "");
        return userName;
    }

    // Uri related

    /**
     * Parse an uri
     *
     * @param sipUri the uri to parse
     * @return parsed object
     */
    public static ParsedSipUriInfos parseSipUri(String sipUri) {
        ParsedSipUriInfos parsedInfos = new ParsedSipUriInfos();
        if (TextUtils.isEmpty(sipUri)) {
            return parsedInfos;
        }

        Matcher m = SIP_URI_PATTERN.matcher(sipUri);
        if (m.matches()) {
            parsedInfos.scheme = m.group(1);
            parsedInfos.domain = m.group(2);
            if (m.group(3) != null) {
                try {
                    parsedInfos.port = Integer.parseInt(m.group(3));
                } catch (NumberFormatException e) {
                    // Log.e(THIS_FILE, "Unable to parse port number");
                }
            }
        }

        return parsedInfos;
    }

    public static Uri forgeSipUri(String scheme, String contact) {
        return Uri.fromParts(scheme, contact, null);
    }

    public static String encodeUser(String user) {
        //user             =  1*( unreserved / escaped / user-unreserved )
        //user-unreserved  =  "&" / "=" / "+" / "$" / "," / ";" / "?" / "/"
        //unreserved  =  alphanum / mark
        //mark        =  "-" / "_" / "." / "!" / "~" / "*" / "'" / "(" / ")"
        return Uri.encode(user, "&=+$,;?/-_.!~*'()");
    }

    /**
     * Extracts SIP from contact, returns null if the contacts if not properly formatted.
     *
     * @param contact
     * @return
     */
    public static String getSipFromContact(String contact) {
        Matcher m = SIP_CONTACT_EASY_PATTERN.matcher(contact);
        String number = contact;
        if (m.matches()) {
            number = m.group(2);
            return number;
        }

        return null;
    }

    /**
     * Holder for parsed sip contact information.<br/>
     * Basically wrap AoR.
     * We should have something like "{@link ParsedSipContactInfos#displayName} <{@link ParsedSipContactInfos#scheme}:{@link ParsedSipContactInfos#userName}@{@link ParsedSipContactInfos#domain}>
     */
    public static class ParsedSipContactInfos {
        /**
         * Contact display name.
         */
        public String displayName = "";
        /**
         * User name of AoR
         */
        public String userName = "";
        /**
         * Domaine name
         */
        public String domain = "";
        /**
         * Scheme of the protocol
         */
        public String scheme = "";


        @Override
        public String toString() {
            return toString(true);
        }

        public String toString(boolean includeDisplayName) {
            StringBuffer buildString = new StringBuffer();
            if (TextUtils.isEmpty(scheme)) {
                buildString.append("<sip:");
            } else {
                buildString.append("<" + scheme + ":");
            }
            if (!TextUtils.isEmpty(userName)) {
                buildString.append(encodeUser(userName) + "@");
            }
            buildString.append(domain + ">");

            // Append display name at beggining if necessary
            if (includeDisplayName && !TextUtils.isEmpty(displayName)) {
                // Prepend with space
                buildString.insert(0, " ");
                // Start with display name
                // qdtext         =  LWS / %x21 / %x23-5B / %x5D-7E  / UTF8-NONASCII
                String encodedName = displayName.replace("\"", "%22");
                encodedName = encodedName.replace("\\", "%5C");
                buildString.insert(0, "\"" + encodedName + "\" ");
            }
            return buildString.toString();
        }

        public String getContactAddress() {
            StringBuffer buildString = new StringBuffer();

            if (!TextUtils.isEmpty(userName)) {
                buildString.append(encodeUser(userName) + "@");
            }
            buildString.append(domain);
            return buildString.toString();
        }
    }

    /**
     * Holder for parsed sip uri information.<br/>
     * We should have something like "{@link ParsedSipUriInfos#scheme}:{@link ParsedSipUriInfos#domain}:{@link ParsedSipUriInfos#port}"
     */
    public static class ParsedSipUriInfos {
        /**
         * Domain name/ip
         */
        public String domain = "";
        /**
         * Scheme of the protocol
         */
        public String scheme = Constants.PROTOCOL_SIP;
        /**
         * Port number
         */
        public int port = 5060;
    }

}
