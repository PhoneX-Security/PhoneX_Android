package net.phonex.db;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.List;

/**
 * Created by dusanklinec on 10.06.15.
 * Object used to query large set of objects.
 */
public class DBBulkQuery extends DBBulkWhere {
    private static final String TAG = "DBBulkQuery";

    /**
     * Projection to use for a query select.
     */
    private String[] projection;
    private String orderBy;

    /**
     * Current cursor in loading cascade.
     */
    private Cursor curCursor;

    public DBBulkQuery(String[] projection) {
        this.projection = projection;
    }

    public DBBulkQuery(ContentResolver cr, Uri uri, String[] projection, String where, String whereInColumn, String[] argsWhere) {
        super(cr, uri, where, whereInColumn, argsWhere);
        this.projection = projection;
    }

    public DBBulkQuery(ContentResolver cr, Uri uri, String[] projection, String whereInColumn) {
        super(cr, uri, whereInColumn);
        this.projection = projection;
    }

    /**
     * Do the database operation here on argsIn.
     * Do nothing here, different API for query.
     */
    protected int trigger(boolean force){
        return 0;
    }

    @Override
    protected int doOperation(String where, String[] argsWhere, int totalElems) {
        return 0;
    }

    /**
     * Moves cursor to the next record.
     * Handles multiple queries logic of there is end of the previous cursor.
     * @return true if moving to next record succeeded.
     */
    public synchronized boolean moveToNext(){
        // First call to moveToNext? Do query.
        while(true){
            // If there is existing cursor and can be moved to next element, return true.
            if (curCursor != null && curCursor.moveToNext()){
                return true;
            }

            // Close this cursor, cannot be moved, set to null.
            if (curCursor != null){
                MiscUtils.closeCursorSilently(curCursor);
                curCursor = null;
            }

            // Cursor is null here, load new, if there is any.
            if (!hasNext()){
                return false;
            }

            curCursor = doQuery();
        }
    }

    /**
     * Returns current cursor of the bulk cascade.
     * @return current cursor. May return null.
     */
    public Cursor getCurrentCursor(){
        return curCursor;
    }

    /**
     * Close all remaining cursors, deletes data.
     */
    public void close(){
        argsIn.clear();
        MiscUtils.closeCursorSilently(curCursor);
        curCursor = null;
    }

    /**
     * Executes bulk query and returns corresponding cursor.
     * @return
     */
    public Cursor doQuery(){
        if (!hasNext()){
            return null;
        }

        try {
            final List<String> toProcess = takeN(false);
            final String inPart = join(toProcess);
            final String totalWhere = getTotalWhere(inPart);

            Log.vf(TAG, "Query, numElements: %s, uri: %s", toProcess.size(), uri);
            return cr.query(uri, projection, totalWhere, argsWhere, orderBy);

        } catch (Exception ex) {
            Log.ef(TAG, ex, "Exception in bulk operation");
        }

        return null;
    }

    /**
     * Returns true if there is more data in the queue to query.
     * @return
     */
    public boolean hasNext(){
        return argsIn.size() > 0;
    }

    public String[] getProjection() {
        return projection;
    }

    public void setProjection(String[] projection) {
        this.projection = projection;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }
}
