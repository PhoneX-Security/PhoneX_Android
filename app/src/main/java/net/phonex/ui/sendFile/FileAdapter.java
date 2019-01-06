package net.phonex.ui.sendFile;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import net.phonex.R;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.system.FilenameUtils;

import android.text.format.DateFormat;

import java.util.Date;
import java.util.List;

/**
 * Adapter for displaying files/directories in filePickerDialogFragment
 * - asynchronously loads images using Glide
 * WIKI: https://github.com/bumptech/glide/wiki
 * @author miroc
 *
 */
public class FileAdapter extends ArrayAdapter<FileItemInfo>{
	private static String TAG = "FileAdapter";
	private Context ctx;
    private int resourceId;
    private FilePickerFragment.DisplayMode displayMode;

    public FileAdapter(Context context, int resource, FilePickerFragment.DisplayMode displayMode) {
		super(context, resource);
		ctx = context;
        resourceId = resource;
        this.displayMode = displayMode;
	}

//    public FileAdapter(Context context, int resource, List<FileItemInfo> data) {
//        super(context, resource);
//        ctx = context;
////        setData(data);
//    }
	
    public void setData(List<FileItemInfo> data){
        clear();
        if (data != null) {
            addAll(data);
        }
    }

    public void setResourceId(int resourceId) {
        this.resourceId = resourceId;
    }

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		ViewHolder holder;

		// Check if an existing view is being reused, otherwise inflate the view
		if (convertView ==  null) {
            if (resourceId < 0) {
                resourceId = R.layout.filechooser_item;
            }
			convertView = LayoutInflater.from(getContext()).inflate(resourceId, null);
						
			holder = new ViewHolder();
			holder.TV_filename = (TextView) convertView.findViewById(R.id.text1);
            holder.TV_size = (TextView) convertView.findViewById(R.id.size);
            holder.TV_datetime = (TextView) convertView.findViewById(R.id.datetime);
            holder.I_thumbnail = (ImageView) convertView.findViewById(R.id.photo);
            holder.TV_selectionOrder = (TextView) convertView.findViewById(R.id.selection_order);
            holder.VG_details = (ViewGroup) convertView.findViewById(R.id.details);
			convertView.setTag(holder);

		} else {

			holder = (ViewHolder) convertView.getTag();
		}

        final FileItemInfo fileItemInfo = getItem(position);
        if (fileItemInfo == null){
            Log.wf(TAG, "getView; item is null");
            return convertView;
        }

		// TODO different icons for different file types
        holder.TV_filename.setVisibility(View.VISIBLE);
		holder.TV_filename.setText(fileItemInfo.filename);
        holder.TV_filename.setSingleLine(true);
        holder.TV_filename.setEllipsize(TextUtils.TruncateAt.MIDDLE);

        if (!fileItemInfo.canBeSelected) {
            holder.VG_details.setVisibility(View.GONE);
        } else {
            holder.VG_details.setVisibility(View.VISIBLE);
            holder.TV_size.setText(FileSizeReprepresentation.bytesToRepresentation(fileItemInfo.sizeInBytes).toString());
            holder.TV_datetime.setText(DateFormat.getDateFormat(getContext()).format(new Date(fileItemInfo.lastModified)));
        }


        // load thumbnail for the item
        // Because of PHON-325:
        // thumbnails for items using image resources (e.g. goUp, folder, etc..)
        // are faking being loaded using Glide and the result image is set as
        // error fallback of the loading sequence (see .error)
        // IT IS A QUICK DIRTY FIX
        if (ctx != null) {
            // image or video, normal file
            if (FileUtils.isImage(fileItemInfo.filename) || FileUtils.isVideo(fileItemInfo.filename)) {
                MiscUtils.loadIntoImageViewByUri(ctx, fileItemInfo.getUri().toString(),
                        holder.I_thumbnail, fileItemInfo.icon, R.drawable.ic_broken_image_black_48px, false);

                // Hide image details in grid mode - only for images
                if (FileUtils.isImage(fileItemInfo.filename) && displayMode == FilePickerFragment.DisplayMode.GRID){
                    holder.VG_details.setVisibility(View.GONE);
                    holder.TV_filename.setVisibility(View.GONE);
                }

            }
            // gallery with valid representative path that is an image or video
            else if ((fileItemInfo.placeholderType == FilePickerFragment.PLACEHOLDER_GALLERY_THUMBNAIL
                    && fileItemInfo.uriRepresentative != null)
                    && (FileUtils.isImage(fileItemInfo.uriRepresentative.getFilename())
                        || FileUtils.isVideo(fileItemInfo.uriRepresentative.getFilename()))) {
                MiscUtils.loadIntoImageViewByUri(ctx, fileItemInfo.uriRepresentative.getUri().toString(),
                        holder.I_thumbnail, fileItemInfo.icon, R.drawable.ic_broken_image_black_48px, false);
                // make a folder symbol in the left lower corner in case picture is displayed in grid view
                if (displayMode == FilePickerFragment.DisplayMode.GRID){
                    holder.TV_filename.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder_black_12px, 0, 0, 0);
                }
            }
            // format not supported by glide, use placeholder
//            holder.I_thumbnail.setImageResource(fileItemInfo.icon);

            else {
                // Activity (context object) may be destroyed, therefore rather use application context to prevent Glide from crashing while changing orientation
                Glide.with(ctx.getApplicationContext())
                        .load("")
                        .centerCrop()
                        .placeholder(fileItemInfo.icon)
                                .dontAnimate()
//                        .crossFade(android.R.anim.fade_in, 150)
                        .error(fileItemInfo.icon)
                        .into(holder.I_thumbnail);
            }
        }
        FileSelectionUtils.setViewSelected(convertView, fileItemInfo.selectionOrder, ctx);

		return convertView;
	}

    public void setDisplayMode(FilePickerFragment.DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public static class ViewHolder{
        public TextView TV_selectionOrder;
		public TextView TV_filename;
        public TextView TV_size;
        public TextView TV_datetime;
        public ImageView I_thumbnail;
        public ViewGroup VG_details; // currently LinearLayout or RelativeLayout
//        public View separator;
	}	
}
