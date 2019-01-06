package net.phonex.ui.customViews;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

// An EditText that lets you use actions ("Done", "Go", etc.) on multi-line edits.
public class ActionEditText extends SecurePasteEditText
{
    public ActionEditText(Context context)
    {
        super(context);
    }

    public ActionEditText(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public ActionEditText(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs)
    {
        InputConnection conn = super.onCreateInputConnection(outAttrs);
        outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        return conn;
    }
}