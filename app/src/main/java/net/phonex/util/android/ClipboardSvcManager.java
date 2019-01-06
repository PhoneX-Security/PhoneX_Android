package net.phonex.util.android;

import android.content.ClipData;
import android.content.Context;

/**
 * Has to be Api 11+
 */
public class ClipboardSvcManager extends ClipboardManager {
    private android.content.ClipboardManager clipboardManager;

    @Override
    protected void setContext(Context context) {
        clipboardManager = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void setText(String description, String text) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(description, text));
    }

}
