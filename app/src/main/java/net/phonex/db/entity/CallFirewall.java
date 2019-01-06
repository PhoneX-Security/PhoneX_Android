package net.phonex.db.entity;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;

import net.phonex.db.scheme.CallFirewallScheme;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CallFirewall {
    private static final String TAG = "CallFirewall";

    public static final int ACTION_CAN_CALL = 0;
    public static final int ACTION_CANT_CALL = 1;
    public static final int ACTION_CAN_ANSWER = 2;

    public static final int CRITERIA_STARTS = 0;
    public static final int CRITERIA_EXACT_MATCH = 1;
    public static final int CRITERIA_REGEXP = 2;
    public static final int CRITERIA_ENDS = 3;
    public static final int CRITERIA_ALL = 4;
    public static final int CRITERIA_CONTAINS = 5;

    private static final Map<Long, List<CallFirewall>> rulesMap = new ConcurrentHashMap<Long, List<CallFirewall>>();

    public Integer id;
    public Integer priority;
    public Integer account;
    public String matchPattern;
    public Integer criteria;
    public Integer action;

    public CallFirewall() {
        // Nothing to do
    }

    public CallFirewall(Cursor c) {
        super();
        createFromDb(c);
    }

    public static boolean isCallable(Context ctxt, long accountId, String number) {
        boolean canCall = true;
        List<CallFirewall> callFirewallList = getRulesForAccount(ctxt, accountId);
        for (CallFirewall f : callFirewallList) {
            canCall &= f.canCall(ctxt, number);

            // Stop processing & rewrite
            if (f.stopProcessing(ctxt, number)) {
                return canCall;
            }
        }
        return canCall;
    }

    public static int isAnswerable(Context ctxt, long accountId, String number, Bundle extraHdr) {
        List<CallFirewall> callFirewallList = getRulesForAccount(ctxt, accountId);
        for (CallFirewall f : callFirewallList) {
            if (f.canAnswer(ctxt, number, extraHdr)) {
                return 200;
            }

            // Stop processing & rewrite
            if (f.stopProcessing(ctxt, number)) {
                return 0;
            }
        }
        return 0;
    }

    ;

    // Helpers static factory
    public static CallFirewall getRuleFromDbId(Context ctxt, long filterId, String[] projection) {
        CallFirewall callFirewall = new CallFirewall();
        if (filterId >= 0) {
            Cursor c = ctxt.getContentResolver().query(ContentUris.withAppendedId(CallFirewallScheme.ID_URI_BASE, filterId),
                    projection, null, null, null);

            if (c != null) {
                try {
                    if (c.getCount() > 0) {
                        c.moveToFirst();
                        callFirewall = new CallFirewall(c);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Something went wrong while retrieving the account", e);
                } finally {
                    c.close();
                }
            }
        }
        return callFirewall;
    }

    /**
     * Load firewall rules for a given account.
     * @param ctxt
     * @param accountId
     * @return
     */
    private static List<CallFirewall> getRulesForAccount(Context ctxt, long accountId) {
        if (rulesMap.containsKey(accountId)) {
            return rulesMap.get(accountId);
        }

        ArrayList<CallFirewall> aList = new ArrayList<CallFirewall>();
        Cursor c = getRulesCursorForAccount(ctxt, accountId);
        if (c != null) {
            try {
                for(; c.moveToNext(); ){
                    aList.add(new CallFirewall(c));
                }
            } catch (Exception e) {
                Log.e(TAG, "Cannot load firewall rules", e);
            } finally {
                c.close();
            }
        }

        rulesMap.put(accountId, aList);
        return rulesMap.get(accountId);
    }

    public static void resetCache() {
        rulesMap.clear();
    }

    public static Cursor getRulesCursorForAccount(Context ctxt, long accountId) {
        return ctxt.getContentResolver().query(CallFirewallScheme.URI, CallFirewallScheme.FULL_PROJECTION, CallFirewallScheme.FIELD_ACCOUNT_ID + "=?", new String[]{Long.toString(accountId)}, CallFirewallScheme.DEFAULT_ORDER);
    }

    public void createFromDb(Cursor c) {
        ContentValues args = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(c, args);

        createFromContentValue(args);
    }

    public void createFromContentValue(ContentValues args) {
        Integer tmp_i;
        String tmp_s;

        tmp_i = args.getAsInteger(CallFirewallScheme.FIELD_ID);
        if (tmp_i != null) {
            id = tmp_i;
        }
        tmp_i = args.getAsInteger(CallFirewallScheme.FIELD_PRIORITY);
        if (tmp_i != null) {
            priority = tmp_i;
        }
        tmp_i = args.getAsInteger(CallFirewallScheme.FIELD_ACTION);
        if (tmp_i != null) {
            action = tmp_i;
        }
        tmp_s = args.getAsString(CallFirewallScheme.FIELD_CRITERIA);
        if (tmp_s != null) {
            matchPattern = tmp_s;
        }
        tmp_i = args.getAsInteger(CallFirewallScheme.FIELD_ACCOUNT_ID);
        if (tmp_i != null) {
            account = tmp_i;
        }
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();

        if (id != null) {
            args.put(CallFirewallScheme.FIELD_ID, id);
        }

        args.put(CallFirewallScheme.FIELD_ACCOUNT_ID, account);
        args.put(CallFirewallScheme.FIELD_CRITERIA, matchPattern);
        args.put(CallFirewallScheme.FIELD_ACTION, action);
        args.put(CallFirewallScheme.FIELD_PRIORITY, priority);
        return args;
    }

    private boolean patternMatches(Context ctxt, String number, Bundle extraHdr, boolean defaultValue) {
        try {
            return Pattern.matches(matchPattern, number);
        } catch (PatternSyntaxException e) {
            Log.e(TAG, "Regex problem", e);
        }

        return defaultValue;
    }

    /**
     * Does the filter allows to call ?
     *
     * @param ctxt   Application context
     * @param number number to test
     * @return true if we can call this number
     */
    public boolean canCall(Context ctxt, String number) {
        if (action == ACTION_CANT_CALL) {
            return !patternMatches(ctxt, number, null, false);

        }
        return true;
    }

    /**
     * Should the filter avoid next filters ?
     *
     * @param ctxt   Application context
     * @param number number to test
     * @return true if we should not process next filters
     */
    public boolean stopProcessing(Context ctxt, String number) {
        if (action == ACTION_CAN_CALL) {
            return patternMatches(ctxt, number, null, false);
        }
        return false;
    }

    /**
     * Does the filter auto answer a call ?
     *
     * @param ctxt   Application context
     * @param number number to test
     * @return true if the call should be auto-answered
     */
    public boolean canAnswer(Context ctxt, String number) {
        return canAnswer(ctxt, number, null);
    }

    public boolean canAnswer(Context ctxt, String number, Bundle extraHdr) {
        if (action == ACTION_CAN_ANSWER) {
            return patternMatches(ctxt, number, extraHdr, false);
        }
        return false;
    }

    /**
     * Set matches field according to a RegExpRepresentation (for UI display)
     *
     * @param representation the regexp representation
     */
    public void setMatcherRepresentation(RegExpCriteria representation) {
        criteria = representation.criteriaType;
        switch (representation.criteriaType) {
            case CRITERIA_STARTS:
                matchPattern = "^" + Pattern.quote(representation.target) + "(.*)$";
                break;
            case CRITERIA_ENDS:
                matchPattern = "^(.*)" + Pattern.quote(representation.target) + "$";
                break;
            case CRITERIA_CONTAINS:
                matchPattern = "^(.*)" + Pattern.quote(representation.target) + "(.*)$";
                break;
            case CRITERIA_ALL:
                matchPattern = "^(.*)$";
                break;
            case CRITERIA_EXACT_MATCH:
                matchPattern = "^(" + Pattern.quote(representation.target) + ")$";
                break;
            case CRITERIA_REGEXP:
            default:
                criteria = CRITERIA_REGEXP;        // In case hit default:
                matchPattern = representation.target;
                break;
        }
    }

    public static final class RegExpCriteria {
        public Integer criteriaType;
        public String target;
    }
}
