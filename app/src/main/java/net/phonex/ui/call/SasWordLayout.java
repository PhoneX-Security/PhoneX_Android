package net.phonex.ui.call;

import android.content.Context;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.phonex.R;

/**
 * Created by ph4r05 on 8/25/14.
 */
public class SasWordLayout extends LinearLayout {

    private View rootView;
    private LinearLayout sasOne;
    private LinearLayout sasTwo;
    private TextView oneHead;
    private TextView oneTail;
    private TextView twoHead;
    private TextView twoTail;

    private String one;
    private String two;

    /**
     * Constructor to be used.
     * @param context
     * @param attrs
     */
    public SasWordLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        rootView = inflater.inflate(R.layout.sas_part, this, true);

        sasOne = (LinearLayout) rootView.findViewById(R.id.sas_one);
        sasTwo = (LinearLayout) rootView.findViewById(R.id.sas_two);

        oneHead = (TextView) rootView.findViewById(R.id.one_head);
        oneTail = (TextView) rootView.findViewById(R.id.one_tail);
        twoHead = (TextView) rootView.findViewById(R.id.two_head);
        twoTail = (TextView) rootView.findViewById(R.id.two_tail);
    }

    /**
     * Sets individual SAS words.
     * @param one
     * @param two
     */
    public void setSas(String one, String two){
        this.one = one;
        this.two = two;

        if (!TextUtils.isEmpty(one)){
            String oneFirst = one.substring(0, 1);
            SpannableString content = new SpannableString(oneFirst);
            content.setSpan(new UnderlineSpan(), 0, oneFirst.length(), 0);
            oneHead.setText(content);
            oneTail.setText(one.length()>1 ? one.substring(1, one.length()) : "");
        }

        if (!TextUtils.isEmpty(two)){
            String twoFirst = two.substring(0, 1);
            SpannableString content = new SpannableString(twoFirst);
            content.setSpan(new UnderlineSpan(), 0, twoFirst.length(), 0);
            twoHead.setText(content);
            twoTail.setText(two.length()>1 ? two.substring(1, two.length()) : "");
        }
    }


}
