package net.phonex.util.android;

import android.content.Context;

/**
 * Clipboard compatibility class.
 */
public abstract class ClipboardManager {
    private static ClipboardManager instance;
    
    public static ClipboardManager getInstance(Context context) {
        if(instance == null) {
            instance = new ClipboardSvcManager();

            if(instance != null) {
                instance.setContext(context);
            }
        }
        
        return instance;
    }
    
    protected ClipboardManager() {

    }

    /**
     * Initialization method.
     * @param context
     */
    protected abstract void setContext(Context context);

    /**
     * Main method for setting text snippet to the clipboard.
     * @param description
     * @param text
     */
    public abstract void setText(String description, String text);
}
