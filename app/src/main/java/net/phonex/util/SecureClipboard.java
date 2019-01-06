package net.phonex.util;

import android.content.Context;

import net.phonex.core.MemoryPrefManager;

/**
 * Secure clipboard implementation by using in-memory-encrypted DB.
 * Only PhoneX can see it's content.
 *
 * Created by ph4r05 on 7/23/14.
 */
public class SecureClipboard {
    private static final String TAG="SecureClipboard";
    public static final String SECURE_PLACEHOLDER="[[Type:PhoneX-Protected]]";
    private Context ctxt;

    public SecureClipboard() {
    }

    public SecureClipboard(Context ctxt) {
        this.ctxt = ctxt;
    }

    public SecureClipboard setContext(Context ctxt){
        this.ctxt = ctxt;
        return this;
    }

    /**
     * Set string to the secure clipboard.
     *
     * @param description
     * @param text
     */
    public void setText(String description, String text){
        MemoryPrefManager.setClipboard(ctxt, description, text);
    }

    /**
     * Returns text stored in secure clipboard.
     * Returns null if there is none.
     *
     * @return
     */
    public String getText(){
        return MemoryPrefManager.getClipboard(ctxt);
    }

    /**
     * Returns description of a stored secure text.
     * Returns null if there is none.
     * @return
     */
    public String getDescription(){
        return MemoryPrefManager.getClipboardDesc(ctxt);
    }

}
