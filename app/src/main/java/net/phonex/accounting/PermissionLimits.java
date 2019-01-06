package net.phonex.accounting;

import android.content.ContentResolver;
import android.content.Context;

import net.phonex.db.entity.AccountingPermission;
import net.phonex.db.entity.SipCallSession;
import net.phonex.db.entity.TrialEventLog;
import net.phonex.util.Log;

import java.util.Date;
import java.util.List;

import static net.phonex.accounting.PermissionType.CALLS_OUTGOING_SECONDS;
import static net.phonex.accounting.PermissionType.FILES_OUTGOING_FILES;
import static net.phonex.accounting.PermissionType.MESSAGES_OUTGOING_DAY_LIMIT;
import static net.phonex.accounting.PermissionType.MESSAGES_OUTGOING_LIMIT;

/**
 * Helper for retrieving permission limit values
 * Created by miroc on 4.11.15.
 */
public class PermissionLimits {
    private static final String TAG = "PermissionLimits";

    public static void updateCallLimit(ContentResolver cr, SipCallSession callInfo) {
        Log.vf(TAG, "updateCallLimit; callInfo=%s", callInfo);
        if (!callInfo.isIncoming()){
            callInfo.setSecondsLimit((int) amountAvailable(cr, CALLS_OUTGOING_SECONDS));
        } else {
            callInfo.setSecondsLimit(-1);
        }
    }

    public static int getCallLimit(ContentResolver cr){
        return (int) amountAvailable(cr, CALLS_OUTGOING_SECONDS);
    }

    public static int getMessagesLimit(ContentResolver cr){
        return (int) amountAvailable(cr, MESSAGES_OUTGOING_LIMIT);
    }

    public static int getMessagesDayLimit(ContentResolver cr) {
        return (int) amountAvailable(cr, MESSAGES_OUTGOING_DAY_LIMIT);
    }

    public static int getFilesLimit(ContentResolver cr){
        return (int) amountAvailable(cr, FILES_OUTGOING_FILES);
    }

    public static boolean isMessageLimitExceeded(Context context) {
        return isMessagesDayLimitExceeded(context); // atm only day limit is considered
    }

    private static boolean isMessagesDayLimitExceeded(Context context){
        int dayLimit = getMessagesDayLimit(context.getContentResolver());
        if (dayLimit >= 0){
            int outgoingMessageCount = TrialEventLog.getOutgoingMessageCount(context, 1);
            return outgoingMessageCount >= dayLimit;
        }
        return false;
    }

    /**
     *
     * @param type
     * @return available amount, negative means infinite
     */
    private static long amountAvailable(ContentResolver cr, PermissionType type){
        return amountAvailable(cr, type, new Date());
    }

    private static long amountAvailable(ContentResolver cr, PermissionType type, Date date){
        List<AccountingPermission> permissions = AccountingPermission.getLocalPermissions(cr, type.toString(), date);
        int totalAvailable = 0;
        for (AccountingPermission perm : permissions){
            if (perm.getValue() < 0){
                // infinite
                totalAvailable = -1;
                break;
            }
            long available = perm.getValue() - perm.getSpent();
            totalAvailable += available;
        }
        Log.inf(TAG, "amountAvailable; type=%s, available=%d, date=%s", type, totalAvailable, date);
        printPerms(permissions);

        return totalAvailable;
    }

    public static void printPerms(List<AccountingPermission> perms){
        if (perms.size() == 0){
            Log.df(TAG, "printPerm: empty");
        }
        for(AccountingPermission p : perms){
            Log.df(TAG, "printPerm: %s", p);
        }
    }
}
