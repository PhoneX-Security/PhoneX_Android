package net.phonex.db;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

import net.phonex.util.Log;

/**
 * Created by dusanklinec on 10.06.15.
 * Used to update large number of records in the database with the same content values.
 */
public class DBBulkUpdater extends DBBulkWhere {
    private static final String TAG = "DBBulkUpdater";

    /**
     * Content values to use in update.
     */
    private ContentValues cv;

    public DBBulkUpdater() {
    }

    public DBBulkUpdater(ContentResolver cr, Uri uri, ContentValues cv, String where, String whereInColumn, String[] argsWhere) {
        super(cr, uri, where, whereInColumn, argsWhere);
        this.cv = cv;
    }

    public DBBulkUpdater(ContentResolver cr, Uri uri, String whereInColumn, ContentValues cv) {
        super(cr, uri, whereInColumn);
        this.cv = cv;
    }

    @Override
    protected int doOperation(String where, String[] argsWhere, int totalElems) {
        int res = cr.update(uri, cv, where, argsWhere);
        Log.vf(TAG, "Operation bulkUpdate finished, res=%d, totalParts=%d", res, totalElems);

        return res;
    }

    public ContentValues getCv() {
        return cv;
    }

    public void setCv(ContentValues cv) {
        this.cv = cv;
    }
}
