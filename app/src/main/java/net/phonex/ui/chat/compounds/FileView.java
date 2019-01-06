package net.phonex.ui.chat.compounds;

import android.content.Context;
import android.graphics.Paint;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.ui.chat.OnMessageActionListener;
import net.phonex.ui.sendFile.FileUtils;
import net.phonex.ui.sendFile.SquareImageView;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.StorageUtils;
import net.phonex.util.system.FilenameUtils;

/**
 * Used to keep message id and storage uri needed to build context menu for a file.
 *
 * Created by Matus on 26.6.2015.
 */
public class FileView extends LinearLayout {
    private static final String TAG = "FileView";

    private ReceivedFile receivedFile;

    public FileView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Long getMessageId() {
        return receivedFile != null ? receivedFile.getMsgId() : -1;
    }

    public FileStorageUri getFileStorageUri() {
        return receivedFile != null ? new FileStorageUri(receivedFile.buildUri()) : null;
    }

    public void setReceivedFile(ReceivedFile receivedFile) {
        this.receivedFile = receivedFile;
    }

    public static int getPlaceholderImageResource(String fileName) {
        if (FileUtils.isImage(fileName) || FileUtils.isVideo(fileName)) {
            return R.drawable.ic_photo_black_48px;
        }
        return R.drawable.ic_insert_drive_file_black_48px;
    }

    public static int getErrorImageResource(String fileName) {
        if (FileUtils.isImage(fileName)) {
            return R.drawable.ic_broken_image_black_48px;
        }
        return R.drawable.ic_insert_drive_file_black_48px;
    }

    public static int getDeletedImageResource() {
        return R.drawable.ic_delete_black_24px;
    }

    /**
     *
     * @param context
     * @param filesDownloaded
     * @param listener
     * @param fileHeight
     * @param smallFileHeight
     * @param largeTombstone display large tombstone for files that were supposed to have thumbnail
     * @return true if this will be placed in a separate large element, false if this should be placed in a compound view
     */
    public boolean init(Context context, final boolean filesDownloaded, final OnMessageActionListener listener,
                        final int fileHeight, final int smallFileHeight, final boolean largeTombstone) {
        if (receivedFile == null) {
            throw new IllegalStateException("First set receivedFile");
        }

        FileStorageUri fileStorageUri = new FileStorageUri(receivedFile.buildUri());

        final boolean hasThumbnail;
        final boolean fileExists;
        final boolean largeElement; // this is being added, because we want tombstone as big as the thumbnail was going to be

        if (filesDownloaded) {
            // files already downloaded, they will exist unless deleted
            fileExists = StorageUtils.existsUri(fileStorageUri.toString(), context.getContentResolver());
            // file types that support thumbnails should have large thumbnail
            largeElement = MiscUtils.supportsGenerateThumbnail(fileStorageUri.getFilename());
            // only existing file that is supported can have thumbnail now
            hasThumbnail = fileExists && largeElement;
        } else {
            // can't exist yet
            fileExists = false;
            // thumbnail only from sender
            hasThumbnail = fileStorageUri.isThumbnail();
            // if we have the thumbnail, the element will be large
            largeElement = hasThumbnail;
        }

        this.setOrientation(HORIZONTAL);
        //this.setGravity(Gravity.CENTER);

        // whether clicking on the layout opens a file
        final boolean clickable;

        final int RECEIVED_FILE_PADDING_DP = 5;
        final int padding = LayoutUtils.dp2Pix(context.getResources(), RECEIVED_FILE_PADDING_DP);

        if (hasThumbnail) {
            // large image view and no name

            LayoutParams params = new LayoutParams(fileHeight, fileHeight);
            params.setMargins(0, 0, 0, 0);
            this.setLayoutParams(params);

            final ImageView imageView = new SquareImageView(context, true);

            this.setLayoutParams(new LinearLayout.LayoutParams(fileHeight, fileHeight));
            imageView.setLayoutParams(new LinearLayout.LayoutParams(fileHeight, fileHeight));
            imageView.setPadding(padding, padding, padding, padding);
            imageView.setScaleType(ImageView.ScaleType.CENTER);

            this.addView(imageView);

            // LL will be clickable, if file was downloaded
            clickable = filesDownloaded;
            MiscUtils.loadIntoImageViewByUri(context, fileStorageUri.toString(), imageView,
                    getPlaceholderImageResource(fileStorageUri.getFilename()), // placeholder based on file name
                    getErrorImageResource(fileStorageUri.getFilename()), // error will be broken image
                    !filesDownloaded); // look into DB, if files were not downloaded yet
        } else {
            // small image view and name

            LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 0);
            this.setLayoutParams(params);

            final ImageView imageView = new ImageView(context);

            imageView.setLayoutParams(new LinearLayout.LayoutParams(smallFileHeight, smallFileHeight));
            imageView.setPadding(padding, padding, padding, padding);
            imageView.setScaleType(ImageView.ScaleType.CENTER);

            this.setGravity(Gravity.CENTER_VERTICAL);

            this.addView(imageView);
            this.addView(getFileTextLayout(context, fileStorageUri, receivedFile, padding,
                    // if this was supposed to have a thumbnail and we allow large tombstones, make it so
                    (largeTombstone && largeElement) ? fileHeight : smallFileHeight,
                    filesDownloaded && !fileExists));

            // LL will be clickable, if file was downloaded and still exists
            clickable = filesDownloaded && fileExists;

            int placeholder;

            if (fileExists || !filesDownloaded) {
                // file exists or has not been downloaded yet
                // placeholder based on file name
                placeholder = getPlaceholderImageResource(fileStorageUri.getFilename());
            } else {
                // file was downloaded, but does not exist anymore - placeholder is deleted icon
                placeholder = getDeletedImageResource();
            }
            MiscUtils.loadPlaceholderIntoImageView(context, imageView, placeholder);
        }

        if (clickable) {
            this.setClickable(true);
            this.setOnClickListener(v -> listener.openFile(receivedFile));
        } else {
            this.setClickable(false);
        }

        // this item will be large, if it has thumbnail or if it should have thumbnail and large tombstones are enabled
        return hasThumbnail || (largeElement && largeTombstone);
    }

    private LinearLayout getFileTextLayout(Context context, FileStorageUri uri, ReceivedFile file, int padding, int fileLineHeight, boolean deleted) {
        // Layout containing string information. Filename + filesize / deleted information.
        final LinearLayout textLayout = new LinearLayout(context, null, android.R.attr.borderlessButtonStyle);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, fileLineHeight));
        textLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        textLayout.setClickable(false);
        textLayout.setPadding(0, 0, 0, 0);

        // TextView tv contains file name.
        final TextView tv = new TextView(context);

        tv.setText(uri.getFilename());

        if (deleted) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        tv.setTag(file);
        tv.setPadding(padding, 0, padding / 2, 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setTextColor(context.getResources().getColor(R.color.text_black));
        tv.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        tv.setEllipsize(null);
        tv.setSingleLine(false);
        tv.setMaxLines(4);
        tv.setInputType(tv.getInputType()
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
                | InputType.TYPE_CLASS_TEXT);
        tv.setHorizontallyScrolling(false);

        textLayout.addView(tv);

        return textLayout;
    }
}
