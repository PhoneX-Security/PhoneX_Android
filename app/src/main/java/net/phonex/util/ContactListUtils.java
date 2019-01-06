package net.phonex.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import net.phonex.db.DBBulkInserter;
import net.phonex.db.entity.CallFirewall;
import net.phonex.db.scheme.CallFirewallScheme;
import net.phonex.soap.ClistFetchCall;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by miroc on 11.3.15.
 */
public class ContactListUtils {
    /**
     * Adds given user as filter rule to allow call to be answered
     * @param context
     * @param accountId
     * @param target
     */
    public static void addToFilterWhitelist(Context context, long accountId, String target){
        ArrayList<String> al = new ArrayList<String>();
        al.add(target);

        addToFilterWhitelist(context, accountId, al);
    }

    /**
     * Adds given user as filter rule to allow call to be answered
     * @param context
     * @param accountId
     * @param targetList
     */
    public static void addToFilterWhitelist(Context context, long accountId, List<String> targetList){
        // Obtain current priority number.
        int firstPriority = 0;
        Cursor currentCursor = context.getContentResolver().query(
                CallFirewallScheme.URI,
                new String[] {CallFirewallScheme.FIELD_ID},
                CallFirewallScheme.FIELD_ACCOUNT_ID + "=?",
                new String[] { String.valueOf(accountId) },
                null);

        if(currentCursor != null) {
            firstPriority = currentCursor.getCount();
            currentCursor.close();
        }

        // Create a new filter.
        int cur = 0;
        List<ContentValues> cvs = new ArrayList<ContentValues>(targetList.size());
        for(String target : targetList){
            CallFirewall callFirewall = new CallFirewall();
            callFirewall.account = (int) accountId;
            callFirewall.action = CallFirewall.ACTION_CAN_ANSWER;
            callFirewall.priority = firstPriority + cur;

            //Matcher
            CallFirewall.RegExpCriteria crit = new CallFirewall.RegExpCriteria();
            crit.criteriaType = CallFirewall.CRITERIA_CONTAINS;
            crit.target = target;
            callFirewall.setMatcherRepresentation(crit);

            // Store to the list.
            ContentValues cv = callFirewall.getDbContentValues();
            cvs.add(cv);

            Log.df(ClistFetchCall.TAG, "Added2list, whitelist rule for user [%s]; data inserted=%s", target, cv.toString());
            cur+=1;
        }

        DBBulkInserter whitelistInserter = new DBBulkInserter(context.getContentResolver(), CallFirewallScheme.URI);
        whitelistInserter.insert(cvs);
        Log.d(ClistFetchCall.TAG, "Whitelist rules were bulk-inserted.");
    }
}
