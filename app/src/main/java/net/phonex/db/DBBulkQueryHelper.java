package net.phonex.db;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.net.Uri;

import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.guava.Joiner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Created by dusanklinec on 10.06.15.
 * Class used to load a big amount of data from database in a row. If single query cannot be used, this
 * splits query among more into a merged cursor.
 */
public class DBBulkQueryHelper {
    private static final String TAG = "DBBulkQueryHelper";
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
     * Projection for query.
     */
    protected String[] projection;

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
     * Ordering.
     */
    protected String orderBy;

    /**
     * Threshold for number of elements in the queue to trigger the operation.
     * If -1 operation is not triggered until finish() is called.
     */
    protected int operationThreshold = OPERATION_THRESHOLD;

    /**
     * List of string arguments in IN(...) part.
     */
    protected final Queue<String> argsIn = new LinkedList<>();

    /**
     * Returns true if there is more data in the queue to query.
     * @return
     */
    public boolean hasNext(){
        return argsIn.size() > 0;
    }

    /**
     * Main quering object.
     * @return
     */
    public Cursor queryAtOnce(){
        List<Cursor> cursors = new LinkedList<>();
        for(; hasNext(); ){
            final Cursor curCursor = doQuery();
            if (curCursor == null){
                break;
            }

            cursors.add(curCursor);
        }

        return new MergeCursor(cursors.toArray(new Cursor[cursors.size()]));
    }

    /**
     * Executes bulk query and returns a new corresponding cursor.
     * @return
     */
    protected Cursor doQuery(){
        if (!hasNext()){
            return null;
        }

        try {
            final List<String> toProcess = takeN(false);
            final String inPart = join(toProcess);
            final String totalWhere = getTotalWhere(inPart);
            return cr.query(uri, projection, totalWhere, argsWhere, orderBy);

        } catch (Exception ex) {
            Log.ef(TAG, ex, "Exception in bulk operation");
        }

        return null;
    }

    /**
     * Takes N parameters from the string list, returns as string (already SQL escaped).
     * Modifies queue, removes elements from it.
     * @return list of SQL escaped string arguments to process in where condition.
     */
    protected List<String> takeN(boolean all) {
        final int limitToSelect = (operationThreshold < 0 || all) ? argsIn.size() : operationThreshold;
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
     * Builder class to generate query helper.
     */
    public static class Builder {
        private static final String TAG = "DBBulkQueryHelper.Builder";
        private static final int OPERATION_THRESHOLD = 100;
        private final DBBulkQueryHelper stub = new DBBulkQueryHelper();

        public Builder() {

        }

        public DBBulkQueryHelper build() {
            return stub;
        }

        public Builder add(String element){
            stub.argsIn.add(element);
            return this;
        }

        public Builder add(List<String> elements){
            stub.argsIn.addAll(elements);
            return this;
        }

        public Builder setCr(ContentResolver cr) {
            stub.setCr(cr);
            return this;
        }

        public Builder setUri(Uri uri) {
            stub.setUri(uri);
            return this;
        }

        public Builder setArgsWhere(String[] argsWhere) {
            stub.setArgsWhere(argsWhere);
            return this;
        }

        public Builder setWhereInColumn(String whereInColumn) {
            stub.setWhereInColumn(whereInColumn);
            return this;
        }

        public Builder setWhere(String where) {
            stub.setWhere(where);
            return this;
        }

        public Builder setOperationThreshold(int operationThreshold) {
            stub.setOperationThreshold(operationThreshold);
            return this;
        }

        public Builder setProjection(String[] projection) {
            stub.setProjection(projection);
            return this;
        }

        public Builder setOrderBy(String orderBy) {
            stub.setOrderBy(orderBy);
            return this;
        }
    }

    protected void setCr(ContentResolver cr) {
        this.cr = cr;
    }

    protected void setUri(Uri uri) {
        this.uri = uri;
    }

    protected void setWhere(String where) {
        this.where = where;
    }

    protected void setWhereInColumn(String whereInColumn) {
        this.whereInColumn = whereInColumn;
    }

    protected void setArgsWhere(String[] argsWhere) {
        this.argsWhere = argsWhere;
    }

    protected void setOperationThreshold(int operationThreshold) {
        this.operationThreshold = operationThreshold;
    }

    protected void setProjection(String[] projection) {
        this.projection = projection;
    }

    protected void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }
}
