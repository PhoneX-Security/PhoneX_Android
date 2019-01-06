package net.phonex.ui.chat;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import net.phonex.R;

import java.util.ArrayList;

/**
 * ArrayAdapter for attachment options
 *
 * Created by Matus on 19-Aug-15.
 */
public class AttachmentAdapter extends ArrayAdapter<AttachmentItem> {

    private Context ctxt;
    private int resourceId;

    public AttachmentAdapter(Context context, int resource) {
        super(context, resource);
        resourceId = resource;
        ctxt = context;
        clear();
        addAll(getDefaultOptions());
    }

    private ArrayList<AttachmentItem> getDefaultOptions() {
        ArrayList<AttachmentItem> items = new ArrayList<>();
        String[] attachmentArray = ctxt.getResources().getStringArray(R.array.attachment_array);
        TypedArray attachmentIconArray = ctxt.getResources().obtainTypedArray(R.array.attachment_icon_array);
        for (int i = 0; i < attachmentArray.length; i++) {
            items.add(new AttachmentItem(attachmentArray[i], i < attachmentIconArray.length() ? attachmentIconArray.getResourceId(i, -1) : R.drawable.ic_folder_black_48px));
        }
        attachmentIconArray.recycle();
        return items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            if (resourceId < 0) {
                resourceId = R.layout.attachment_item;
            }
            convertView = LayoutInflater.from(getContext()).inflate(resourceId, null);

            holder = new ViewHolder();
            holder.optionName = (TextView) convertView.findViewById(R.id.attachment_item);
            holder.optionImage = (ImageView) convertView.findViewById(R.id.attachment_icon);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.optionName.setText(getItem(position).optionName);
        holder.optionImage.setImageResource(getItem(position).imageResource);

        return convertView;
    }

    public static class ViewHolder{
        public TextView optionName;
        public ImageView optionImage;
    }
}
