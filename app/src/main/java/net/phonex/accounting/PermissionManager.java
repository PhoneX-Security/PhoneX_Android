package net.phonex.accounting;

import android.content.ContentValues;
import android.os.SystemClock;

import net.phonex.db.DBBulkInserter;
import net.phonex.db.entity.AccountingLog;
import net.phonex.db.entity.AccountingPermission;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipCallSession;
import net.phonex.service.XService;
import net.phonex.util.Log;
import net.phonex.util.guava.Lists;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.phonex.accounting.PermissionType.CALLS_OUTGOING_SECONDS;
import static net.phonex.accounting.PermissionType.FILES_OUTGOING_FILES;

/**
 * Class managing permissions - primarily saving spent permissions (=consuming)
 * Created by miroc on 16.10.15.
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private final XService xService;
    private ExecutorService executor;

    public PermissionManager(XService xService) {
        this.xService = xService;
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Do the check if call has to be consumed and init consumption asynchronously
     * @param callInfo
     */
    public void asyncConsumeCall(SipCallSession callInfo){
        Log.vf(TAG, "asyncConsumeCall; secondsLimit=%d, zrtpStart=%d", callInfo.getSecondsLimit(), callInfo.getZrtpStart());
        if (!callInfo.isIncoming() && callInfo.getSecondsLimit() >= 0 && callInfo.getZrtpStart() >= 0){
            long elapsedMillis = SystemClock.elapsedRealtime() - callInfo.getZrtpStart();

            new Thread(() -> {
                consume(CALLS_OUTGOING_SECONDS, elapsedMillis/1000);
            }).start();
        }
    }


    public void asyncConsumeFiles(int sipMessageId) {
        Log.vf(TAG, "asyncConsumeFiles; sipMessageId=%d", sipMessageId);
        new Thread(() -> {
            List<ReceivedFile> files = ReceivedFile.getFilesByMsgId(xService.getContentResolver(), sipMessageId);
            Integer filesCount = files != null ? files.size() : 0;
            consume(FILES_OUTGOING_FILES, filesCount);
        }).start();
    }

    /**
     * Call when permission is consumed
     * @param type Permission type
     * @param value Consumed value
     */
    private void consume(PermissionType type, long value){
        consume(type, value, new Date());
    }

    private void consume(PermissionType type, long value, Date date){
        Log.inf(TAG, "consume; type=%s, value=%d, date=%s", type, value, date);
        List<AccountingPermission> permissions = AccountingPermission.getLocalPermissions(xService.getContentResolver(), type.toString(), date);
        List<ContentValues> logsToCreate = Lists.newArrayList();
        // here relevant permissions are in order we want to consume them (subscriptions are prioritized over consumables + consume older subscriptions first)

        long remainingToConsume = value;
        for (AccountingPermission perm : permissions){
            if (perm.getValue() < 0){
                // unlimited permission found
                remainingToConsume = 0;
                break;
            }
            if (remainingToConsume <= 0){
                // no more spending required
                break;
            }

            long available = perm.getValue() - perm.getSpent();
            if (available <= 0){
                // skip, this permission is already spent
                continue;
            }

            long permConsume = Math.min(available, remainingToConsume);
            Log.df(TAG, "spending; remainingToConsume=%d, permConsume=%d, value=%d, spent=%d, available=%d", remainingToConsume, permConsume, perm.getValue(), perm.getSpent(), available);

            // spend it!
            perm.setSpent(perm.getSpent() + permConsume);
            AccountingPermission.update(xService.getContentResolver(), perm.getId(), perm.getDbContentValues());
            remainingToConsume -= permConsume;

            // create log
            AccountingLog log = AccountingLog.logFromPermission(perm);
            log.setAmount(permConsume); // do not count total spent, only currently spent
            logsToCreate.add(log.getDbContentValues());
            // TODO implement daily limits of messages for trial version, done separately at the moment
        }
        Log.vf(TAG, "consume; remainingToConsume=%d, totalToConsume=%d", remainingToConsume, value);

        // TODO test
        // log on remote
        DBBulkInserter inserter = new DBBulkInserter(xService.getContentResolver(), AccountingLog.URI);
        inserter.insert(logsToCreate);
//
        if (logsToCreate.size() > 0){
            AccountingLogUpdaterCall call =  new AccountingLogUpdaterCall(xService, xService.getXmppManager().getXmppResourceString());
            executor.submit(call);
        }
    }

}
