package net.phonex.ui.broadcast;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tokenautocomplete.TokenCompleteTextView;

import net.phonex.R;
import net.phonex.db.entity.SipClist;
import net.phonex.util.Log;

/**
 * Use with SipClistCursorAdapter
 * Created by miroc on 23.2.15.
 */
public class SipContactCompletionView extends TokenCompleteTextView {

    private static final String TAG = "SipContactCompletionView";

    public SipContactCompletionView(Context context) {
        super(context);
    }

    public SipContactCompletionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SipContactCompletionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAdapter(SipClistCursorAdapter adapter) {
        super.setAdapter(adapter);
    }

    @Override
    protected View getViewForObject(Object o) {
        Log.v("MIRO", "getViewForObject");
        SipClist contact = (SipClist) o;

        LayoutInflater l = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        RelativeLayout view = (RelativeLayout) l.inflate(R.layout.contact_token, (ViewGroup) SipContactCompletionView.this.getParent(), false);
        ((TextView)view.findViewById(R.id.name)).setText(contact.getDisplayName());

        return view;
    }

    @Override
    protected CharSequence objectToString(Object object) {
//        return ((SipClist) object).getDisplayName();
        // always enter empty string  -- deleting then works much better
        return "";
    }

    @Override
    protected Object defaultObject(String s) {
        return null;
    }
}
