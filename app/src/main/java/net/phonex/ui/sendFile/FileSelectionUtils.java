package net.phonex.ui.sendFile;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import net.phonex.R;

/**
 * Created by eldred on 20/02/15.
 */
public abstract class FileSelectionUtils {

    private static final String TAG = "FileItemUtils";

    public static void selectView(final View itemView, final int orderNumber, final Context ctx) {

        setViewSelected(itemView, orderNumber, ctx);
    }

    public static void deselectView(final View itemView, final Context ctx) {

        setViewSelected(itemView, FileItemInfo.UNSELECTED, ctx);
    }

    public static void setViewSelected(final View itemView, final int orderNumber, final Context ctx) {

        final TextView textView = (TextView)itemView.findViewById(R.id.selection_order);

        if (orderNumber == FileItemInfo.UNSELECTED) {

            textView.setText("");
            textView.setWidth(0);
            textView.setPadding(0, 0, 0, 0);

            itemView.setBackgroundResource(R.color.file_unselected);
        } else {

            final int widthDpi = (int) ctx.getResources().getDimension(R.dimen.list_detail_image_size);
            final int rightPaddingDpi = (int) ctx.getResources().getDimension(R.dimen.list_detail_item_padding);

            // show count starting from 1
            textView.setText(Integer.toString(orderNumber + 1));
            textView.setWidth(widthDpi);
            textView.setPadding(0, 0, rightPaddingDpi, 0);

            itemView.setBackgroundResource(R.color.file_selected);
        }
    }
}
