package net.phonex.ui.chat.compounds;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

import net.phonex.util.Log;

/**
 * Wrapper for Cursor to fix PHON-444 StaleDataException
 * This is dirty'n dirty fix, please if found later and have some time, try to fix it properly.
 * Created by miroc on 30.4.15.
 */
public class MyCursor implements Cursor{

    private static final String TAG = "MyCursor";
    final Cursor x;

    public MyCursor(Cursor x) {
        if (x.isClosed()){
            Log.wf(TAG, "<const> cursor is already closed");
        }
        this.x = x;
    }

    @Override
    public int getCount() {
        try {
            return x.getCount();
        } catch (Exception e){
            Log.ef(TAG, e, "getCountCrash" );
            return 0;
        }
    }

    @Override
    public int getPosition() {
        return x.getPosition();
    }

    @Override
    public boolean move(int i) {
        return x.move(i);
    }

    @Override
    public boolean moveToPosition(int i) {
        try {
            return x.moveToPosition(i);
        } catch (Exception e){
            Log.ef(TAG, e, "moveToPosition ");
            return false;
        }
    }

    @Override
    public boolean moveToFirst() {
        return x.moveToFirst();
    }

    @Override
    public boolean moveToLast() {
        return x.moveToLast();
    }

    @Override
    public boolean moveToNext() {
        return x.moveToNext();
    }

    @Override
    public boolean moveToPrevious() {
        return x.moveToPrevious();
    }

    @Override
    public boolean isFirst() {
        return x.isFirst();
    }

    @Override
    public boolean isLast() {
        return x.isLast();
    }

    @Override
    public boolean isBeforeFirst() {
        return x.isBeforeFirst();
    }

    @Override
    public boolean isAfterLast() {
        return x.isAfterLast();
    }

    @Override
    public int getColumnIndex(String s) {
        return x.getColumnIndex(s);
    }

    @Override
    public int getColumnIndexOrThrow(String s) throws IllegalArgumentException {
        return x.getColumnIndexOrThrow(s);
    }

    @Override
    public String getColumnName(int i) {
        return x.getColumnName(i);
    }

    @Override
    public String[] getColumnNames() {
        return x.getColumnNames();
    }

    @Override
    public int getColumnCount() {
        return x.getColumnCount();
    }

    @Override
    public byte[] getBlob(int i) {
        return x.getBlob(i);
    }

    @Override
    public String getString(int i) {
        return x.getString(i);
    }

    @Override
    public void copyStringToBuffer(int i, CharArrayBuffer charArrayBuffer) {
        x.copyStringToBuffer(i, charArrayBuffer);
    }

    @Override
    public short getShort(int i) {
        return x.getShort(i);
    }

    @Override
    public int getInt(int i) {
        return x.getInt(i);
    }

    @Override
    public long getLong(int i) {
        return x.getLong(i);
    }

    @Override
    public float getFloat(int i) {
        return x.getFloat(i);
    }

    @Override
    public double getDouble(int i) {
        return x.getDouble(i);
    }

    @Override
    public int getType(int i) {
        return x.getType(i);
    }

    @Override
    public boolean isNull(int i) {
        return x.isNull(i);
    }

    @Override
    @Deprecated
    public void deactivate() {
        x.deactivate();
    }

    @Override
    @Deprecated
    public boolean requery() {
        return x.requery();
    }

    @Override
    public void close() {
        try {
            throw new RuntimeException("MyCursor#close");
        } catch (Exception e){
            Log.wf(TAG, e, "close()");
        }
        x.close();
    }

    @Override
    public boolean isClosed() {
        return x.isClosed();
    }

    @Override
    public void registerContentObserver(ContentObserver contentObserver) {
        x.registerContentObserver(contentObserver);
    }

    @Override
    public void unregisterContentObserver(ContentObserver contentObserver) {
        x.unregisterContentObserver(contentObserver);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {
        x.registerDataSetObserver(dataSetObserver);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
        x.unregisterDataSetObserver(dataSetObserver);
    }

    @Override
    public void setNotificationUri(ContentResolver contentResolver, Uri uri) {
        x.setNotificationUri(contentResolver, uri);
    }

    @Override
    public Uri getNotificationUri() {
        return x.getNotificationUri();
    }

    @Override
    public boolean getWantsAllOnMoveCalls() {
        return x.getWantsAllOnMoveCalls();
    }

    @Override
    public void setExtras(Bundle bundle) {

    }

    @Override
    public Bundle getExtras() {
        return x.getExtras();
    }

    @Override
    public Bundle respond(Bundle bundle) {
        return x.respond(bundle);
    }
}
