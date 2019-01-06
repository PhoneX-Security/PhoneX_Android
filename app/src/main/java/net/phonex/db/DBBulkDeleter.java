package net.phonex.db;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

import net.phonex.util.Log;

/**
 * Created by dusanklinec on 10.06.15.
 * Helper for deleting multiple objects from database in a row.
 */
public class DBBulkDeleter extends DBBulkWhere {
    private static final String TAG = "DBBulkDeleter";

    /**
     * Content values to use in update.
     */
    private ContentValues cv;

    public DBBulkDeleter() {
    }

    public DBBulkDeleter(ContentResolver cr, Uri uri, String where, String whereInColumn, String[] argsWhere) {
        super(cr, uri, where, whereInColumn, argsWhere);
    }

    public DBBulkDeleter(ContentResolver cr, Uri uri, String whereInColumn) {
        super(cr, uri, whereInColumn);
    }

    @Override
    protected int doOperation(String where, String[] argsWhere, int totalElems) {
        int res = cr.delete(uri, where, argsWhere);
        Log.vf(TAG, "Operation bulkDelete finished, res=%d, totalParts=%d", res, totalElems);

        return res;
    }

    public ContentValues getCv() {
        return cv;
    }

    public void setCv(ContentValues cv) {
        this.cv = cv;
    }
}
