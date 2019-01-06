package net.phonex.util;

import android.content.Context;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

/**
 * Created by miroc on 27.2.15.
 */
public class MessageUtils {
    public static CharSequence formatMessage(String body, String contentType, Context context) {
        SpannableStringBuilder buf = new SpannableStringBuilder();
        if (!TextUtils.isEmpty(body)) {
            // Converts html to spannable if ContentType is "text/html".
            if (contentType != null && "text/html".equals(contentType)) {
                //buf.append("\n");
                buf.append(Html.fromHtml(body));
            } else {
                SmileyParser parser = SmileyParser.getInstance();
                buf.append(parser.addSmileySpans(body, context));
            }
        }

        return buf;
    }
}
