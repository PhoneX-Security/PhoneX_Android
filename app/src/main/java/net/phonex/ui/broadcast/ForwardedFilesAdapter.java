package net.phonex.ui.broadcast;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import net.phonex.R;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ui.sendFile.FileUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.util.ArrayList;

/**
 * @author miroc
 */
public class ForwardedFilesAdapter extends ArrayAdapter<String>{
	private static final String TAG = "ForwardedFilesAdapter";
	private ArrayList<String> files;
	private Context ctx;

	public ForwardedFilesAdapter(Context context, int resource, ArrayList<String> files) {
		super(context, resource, files);

		this.files = files;
		ctx = context;
	}
	
	@Override
	public int getCount() {			
		return files.size();
	}
	
	@Override
	public String getItem(int position) {
        return (files.size() - 1 < position) ? null : files.get(position);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		
		// Check if an existing view is being reused, otherwise inflate the view
		if (convertView ==  null) {
			convertView = LayoutInflater.from(getContext()).inflate(R.layout.filechooser_checkbox_item, null);
						
			holder = new ViewHolder();
			holder.filename = (TextView) convertView.findViewById(R.id.text1);
            holder.imageThumbnail = (ImageView) convertView.findViewById(R.id.photo);
			holder.position = position;
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		String fileUri = files.get(position);
        if (fileUri == null){
            Log.wf(TAG, "getView; item is null");
            return convertView;
        }

		Uri uri = Uri.parse(fileUri);
		String filename = uri.getQueryParameter(FileStorageUri.STORAGE_FILENAME);

        // TODO different icons for different file types
        holder.filename.setText(filename);

        // load thumbnail for the item
        // Because of PHON-325:
        // thumbnails for items using image resources (e.g. goUp, folder, etc..)
        // are faking being loaded using Glide and the result image is set as
        // error fallback of the loading sequence (see .error)
        // IT IS A QUICK DIRTY FIX
        final String imageFileToThumbnail =
                (FileUtils.isImage(filename) ||
                        FileUtils.isVideo(filename) &&
                        (ctx != null)) ?
                        fileUri :
                        "";
		if ("".equals(imageFileToThumbnail)) {
			// this will load the file icon
			Glide.with(ctx)
					.load("")
					.centerCrop()
					.crossFade(android.R.anim.fade_in, 150)
					.placeholder(R.drawable.file_icon)
					.error(R.drawable.file_icon)
					.into(holder.imageThumbnail);
		} else {
			MiscUtils.loadIntoImageViewByUri(getContext(), fileUri, holder.imageThumbnail,
					R.drawable.file_icon, R.drawable.ic_broken_image_black_48px, false);
		}
		return convertView;
	}

	public static class ViewHolder{
		public TextView filename;
        public ImageView imageThumbnail;
		public int position;
	}	
}
