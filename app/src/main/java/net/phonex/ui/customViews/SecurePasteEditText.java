package net.phonex.ui.customViews;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

import net.phonex.util.SecureClipboard;

/**
 * Created by ph4r05 on 7/24/14.
 */
public class SecurePasteEditText extends EditText {
    private final Context ctxt;
    private final SecureClipboard secureClipboard;

    public SecurePasteEditText(Context context) {
        super(context);
        this.ctxt = context;
        this.secureClipboard = new SecureClipboard(context);
    }

    public SecurePasteEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.ctxt = context;
        this.secureClipboard = new SecureClipboard(context);
    }

    public SecurePasteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.ctxt = context;
        this.secureClipboard = new SecureClipboard(context);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        // Do your thing:
        boolean consumed = super.onTextContextMenuItem(id);

        String curText =  getText().toString();
        int min = 0;
        int max = curText==null ? 0 : curText.length();

        if (isFocused()) {
            final int selStart = getSelectionStart();
            final int selEnd = getSelectionEnd();

            min = Math.max(0, Math.min(selStart, selEnd));
            max = Math.max(0, Math.max(selStart, selEnd));
        }

      /*  switch (id) {
            case android.R.id.paste:
                paste(min, max);
                return true;

            case android.R.id.cut:
                setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
                deleteText_internal(min, max);
                stopSelectionActionMode();
                ;
                return true;

            case android.R.id.copy:
                setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
                stopSelectionActionMode();
                return true;
        }*/
        return false;
    }

    /**
     * Paste clipboard content between min and max positions.
     */
    /*private void paste(int min, int max) {
        ClipboardSvcManager clipboard =
                (ClipboardSvcManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            boolean didFirst = false;
            for (int i=0; i<clip.getItemCount(); i++) {
                CharSequence paste = clip.getItemAt(i).coerceToStyledText(getContext());
                if (paste != null) {
                    if (!didFirst) {
                        long minMax = prepareSpacesAroundPaste(min, max, paste);
                        min = TextUtils.unpackRangeStartFromLong(minMax);
                        max = TextUtils.unpackRangeEndFromLong(minMax);
                        Selection.setSelection((Spannable) mText, max);
                        ((Editable) mText).replace(min, max, paste);
                        didFirst = true;
                    } else {
                        ((Editable) mText).insert(getSelectionEnd(), "\n");
                        ((Editable) mText).insert(getSelectionEnd(), paste);
                    }
                }
            }
            stopSelectionActionMode();
        }
    }*/

}
