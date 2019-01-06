package net.phonex.util;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

/**
 * Created by miroc on 15.9.15.
 */
public class SimpleContentObserver extends ContentObserver {
    public interface Action{
        void action();
    }
    private Action action;

    public SimpleContentObserver(Handler h, Action action) {
        super(h);
        this.action = action;
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        action.action();
    }
}
