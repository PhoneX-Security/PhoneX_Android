package net.phonex.db;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

import net.phonex.core.Constants;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by dusanklinec on 10.06.15.
 * Helper to insert multiple number of object to the database.
 */
public class DBBulkInserter {
    private static final String TAG = "DBBulkInserter";
    private static final int OPERATION_THRESHOLD = 250;

    /**
     * Content resolver to use for DB operations.
     */
    private ContentResolver cr;

    /**
     * Update uri.
     */
    private Uri uri;

    /**
     * Threshold for number of elements in the queue to trigger the operation.
     * If -1 operation is not triggered until finish() is called.
     */
    private int operationThreshold = OPERATION_THRESHOLD;

    /**
     * If true, bulkInsert() will be used, otherwise ContentProviderOperation will be used.
     */
    private boolean useBulkInsert = false;

    /**
     * List of string arguments in IN(...) part.
     */
    private final Queue<ContentValues> argsIn = new ConcurrentLinkedQueue<>();

    public DBBulkInserter() {
    }

    public DBBulkInserter(ContentResolver cr, Uri uri) {
        this.cr = cr;
        this.uri = uri;
    }

    /**
     * Flush all buffered records. Last call.
     */
    public void finish(){
        trigger(true);
    }

    /**
     * Returns true if there is enough data for next query.
     * @return
     */
    public boolean shouldTrigger(boolean force){
        final int size = argsIn.size();
        if (size == 0){
            return false;
        }

        return force || operationThreshold < 0 || size >= operationThreshold;
    }

    /**
     * Adds ContentValues argument to the buffer, update is triggered once number of elements reached threshold or
     * after calling finish().
     * @param elem
     */
    public void add(ContentValues elem){
        argsIn.add(elem);
        trigger(false);
    }

    /**
     * Adds all ContentValues arguments to the buffer, update is triggered once number of elements reached threshold or
     * after calling finish().
     * @param elems
     */
    public void add(List<ContentValues> elems){
        argsIn.addAll(elems);
        trigger(false);
    }

    /**
     * Takes N parameters from the string list, returns as string (already SQL escaped).
     * Modifies queue, removes elements from it.
     * @return
     */
    protected List<ContentValues> takeN(boolean all) {
        final int limitToSelect = (operationThreshold < 0 || all) ? argsIn.size() : operationThreshold;
        final ArrayList<ContentValues> toProcess = new ArrayList<>(limitToSelect);

        // Fill array list with data in queue.
        for (int i = 0; i < limitToSelect; i++) {
            final ContentValues curArg = argsIn.poll();
            if (curArg == null) {
                break;
            }

            toProcess.add(curArg);
        }

        return toProcess;
    }

    /**
     * Inserts given content values to the database.
     * Does not process nor modifies internal state.
     * @param toProcess
     */
    public void insert(Collection<ContentValues> toProcess){
        try {
            if (useBulkInsert) {
                int res = cr.bulkInsert(uri, toProcess.toArray(new ContentValues[toProcess.size()]));
                Log.vf(TAG, "Operation bulkInsert finished, res=%d, totalParts=%d, uri=%s", res, toProcess.size(), uri);

            } else {
                final ArrayList<ContentProviderOperation> operations = new ArrayList<>(toProcess.size());
                for (ContentValues cv : toProcess) {
                    operations.add(
                            ContentProviderOperation
                                    .newInsert(uri)
                                    .withValues(cv)
                                    .withYieldAllowed(true)
                                    .build()
                    );
                }

                final ContentProviderResult[] res = cr.applyBatch(Constants.AUTHORITY, operations);
                Log.vf(TAG, "Operation bulkInsert (CPO) finished, resSize=%d, totalParts=%d, uri=%s", res.length, operations.size(), uri);
            }
        } catch(Exception ex){
            Log.ef(TAG, ex, "Bulk insert operation failed, exception.");
        }
    }

    /**
     * Do the database operation here on argsIn.
     */
    protected void trigger(boolean force){
        while(shouldTrigger(force)) {
            try {
                // Use batch operation here instead of bulk insert - notification check.
                final List<ContentValues> toProcess = takeN(false);
                insert(toProcess);

            } catch (Exception ex) {
                Log.ef(TAG, ex, "Exception in bulk operation");
            }
        }
    }

    public int getOperationThreshold() {
        return operationThreshold;
    }

    public void setOperationThreshold(int operationThreshold) {
        this.operationThreshold = operationThreshold;
    }

    public ContentResolver getCr() {
        return cr;
    }

    public void setCr(ContentResolver cr) {
        this.cr = cr;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public boolean isUseBulkInsert() {
        return useBulkInsert;
    }

    public void setUseBulkInsert(boolean useBulkInsert) {
        this.useBulkInsert = useBulkInsert;
    }
}
