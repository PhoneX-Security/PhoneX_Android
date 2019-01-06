package net.phonex.db;

import android.content.ContentResolver;
import android.database.DatabaseUtils;
import android.net.Uri;

import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.guava.Joiner;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by dusanklinec on 10.06.15.
 * Abstract class for bulk database operation of type "x IN (...)"
 */
public abstract class DBBulkWhere {
    private static final String TAG = "DBBulkWhere";
    private static final int OPERATION_THRESHOLD = 100;

    /**
     * Content resolver to use for DB operations.
     */
    protected ContentResolver cr;

    /**
     * Update uri.
     */
    protected Uri uri;

    /**
     * SQL WHERE part, constant part. May be null / empty.
     */
    protected String where;

    /**
     * Column for x IN (...) query part.
     */
    protected String whereInColumn;

    /**
     * Arguments for where part. May be null.
     */
    protected String[] argsWhere;

    /**
     * Threshold for number of elements in the queue to trigger the operation.
     * If -1 operation is not triggered until finish() is called.
     */
    protected int operationThreshold = OPERATION_THRESHOLD;

    /**
     * List of string arguments in IN(...) part.
     */
    protected final Queue<String> argsIn = new ConcurrentLinkedQueue<>();

    public DBBulkWhere() {
    }

    public DBBulkWhere(ContentResolver cr, Uri uri, String where, String whereInColumn, String[] argsWhere) {
        this.cr = cr;
        this.uri = uri;
        this.where = where;
        this.whereInColumn = whereInColumn;
        this.argsWhere = argsWhere;
    }

    public DBBulkWhere(ContentResolver cr, Uri uri, String whereInColumn) {
        this.cr = cr;
        this.uri = uri;
        this.whereInColumn = whereInColumn;
    }

    /**
     * Flush all buffered records. Last call.
     */
    public int finish(){
        return trigger(true);
    }

    /**
     * Returns true if there is enough data for next query.
     * @return true if there is something to trigger && threshold is reached.
     */
    public boolean shouldTrigger(boolean force){
        final int size = argsIn.size();
        if (size == 0){
            return false;
        }

        return force || operationThreshold < 0 || size >= operationThreshold;
    }

    /**
     * Adds string argument to the buffer, update is triggered once number of elements reached threshold or
     * after calling finish().
     * @param elem element to add to queue.
     */
    public void add(String elem){
        argsIn.add(elem);
        trigger(false);
    }

    /**
     * Adds all strings argument to the buffer, update is triggered once number of elements reached threshold or
     * after calling finish().
     * @param elems list of elements to add to the queue.
     */
    public void add(List<String> elems){
        argsIn.addAll(elems);
        trigger(false);
    }

    /**
     * Takes N parameters from the string list, returns as string (already SQL escaped).
     * Modifies queue, removes elements from it.
     * @return list of SQL escaped string arguments to process in where condition.
     */
    protected List<String> takeN(boolean all) {
        final int size = argsIn.size();
        final int limitToSelect = (operationThreshold < 0 || all || size <= operationThreshold) ? size : operationThreshold;
        final ArrayList<String> toProcess = new ArrayList<>(limitToSelect);

        // Fill array list with data in queue.
        for (int i = 0; i < limitToSelect; i++) {
            final String curArg = argsIn.poll();
            if (curArg == null) {
                break;
            }

            toProcess.add(DatabaseUtils.sqlEscapeString(curArg));
        }

        return toProcess;
    }

    /**
     * Build IN part from toProcess.
     * @param toProcess list of string arguments to process.
     * @return returns string of arguments joined with ','.
     */
    protected String join(List<String> toProcess){
        Joiner joiner = Joiner.on(",");
        return joiner.join(toProcess);
    }

    /**
     * Returns resulting SQL WHERE part for the query.
     * @param inPart text arguments for IN(...) part, SQL escaped, joined with ','.
     * @return total SQL where part with predefined constant part.
     */
    protected String getTotalWhere(String inPart){
        final String whereInPart = String.format("%s IN (%s)", whereInColumn, inPart);
        return MiscUtils.isEmpty(where) ? whereInPart : where + " AND " + whereInPart;
    }

    /**
     * Do the database operation here on argsIn.
     */
    protected int trigger(boolean force){
        int counter = 0;
        while(shouldTrigger(force)) {
            try {
                final List<String> toProcess = takeN(false);
                final String inPart = join(toProcess);
                final String totalWhere = getTotalWhere(inPart);
                counter += doOperation(totalWhere, argsWhere, toProcess.size());

            } catch (Exception ex) {
                Log.ef(TAG, ex, "Exception in bulk operation");
            }
        }

        return counter;
    }

    /**
     * DB operation to perform.
     * @param where SQL where part.
     * @param argsWhere SQL where part values.
     */
    abstract protected int doOperation(String where, String[] argsWhere, int totalElems);

    public String getWhere() {
        return where;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public String getWhereInColumn() {
        return whereInColumn;
    }

    public void setWhereInColumn(String whereInColumn) {
        this.whereInColumn = whereInColumn;
    }

    public String[] getArgsWhere() {
        return argsWhere;
    }

    public void setArgsWhere(String[] argsWhere) {
        this.argsWhere = argsWhere;
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
}
