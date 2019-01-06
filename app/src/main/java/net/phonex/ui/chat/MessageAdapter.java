package net.phonex.ui.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.InputType;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import net.phonex.R;
import net.phonex.core.IService;
import net.phonex.db.entity.FileStorage;
import net.phonex.db.entity.FileTransfer;
import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipMessage;
import net.phonex.db.entity.Thumbnail;
import net.phonex.ft.DHKeyHelper;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pub.parcels.FileTransferError;
import net.phonex.service.messaging.AmpDispatcher;
import net.phonex.sip.SipStatusCode;
import net.phonex.ui.chat.compounds.FileReceiverView;
import net.phonex.ui.chat.compounds.FileTransferProgressView;
import net.phonex.ui.chat.compounds.FileView;
import net.phonex.ui.customViews.FlowLayout;
import net.phonex.ui.sendFile.FileUtils;
import net.phonex.util.DateUtils;
import net.phonex.util.LayoutUtils;
import net.phonex.util.Log;
import net.phonex.util.MessageUtils;
import net.phonex.util.MiscUtils;
import net.phonex.util.guava.Lists;
import net.phonex.util.system.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageAdapter extends ResourceCursorAdapter implements OnClickListener {
	private static final String TAG = "MessageAdapter";
    private static final String DATE_FORMAT_SHORT = "HH:mm";
    private static final String DATE_FORMAT_LONG = "yyyy-MM-dd HH:mm";

	private final SimpleDateFormat dateFormatterShort;
	private final SimpleDateFormat dateFormatterLong;

//    private static final String TAG_RECEIVED_FILES = "tag_received_files";
//    private static final String TAG_MESSAGE_ID = "tag_message_id";

    private static final int RECEIVED_FILE_LINE_HEIGHT_DP = 240;
    private static final int RECEIVED_FILE_SMALL_ICON_HEIGHT_DP = 48;
    private static final int RECEIVED_FILE_PADDING_DP = 5;

    private boolean isFragmentForeground = false;

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private Handler timerHandler = new Handler();

	/**
	 * service interface for retrieving message upload/download progress, initialized from MessageFragment
	 */
    private IService service;
    private OnMessageActionListener actionListener;

    /**
     * Message fragment for building file specific contextual menu.
     */
    MessageFragment messageFragment;

    private int currentWrapperWidth = -1;
    private int curCols = 1;
    private int curRows = 1;

    public MessageAdapter(Context context, Cursor c, MessageFragment messageFragment) {
        super(context, R.layout.message_list_item, c, 0);
        this.messageFragment = messageFragment;
        this.dateFormatterShort = new SimpleDateFormat(DATE_FORMAT_SHORT, Locale.getDefault());
        this.dateFormatterLong = new SimpleDateFormat(DATE_FORMAT_LONG, Locale.getDefault());
    }

    public static final class ViewHolder {

        public TextView cryptoView;
        public TextView contentView;
        public TextView statusView;
        public ImageView deliveredIndicator;
        public LinearLayout containterBlock;
        public LinearLayout wrapper;
        public Button resendButton;
        public FileTransferProgressView fileTransferProgress;

        public FlowLayout downloadedFiles;
        public FileReceiverView fileReceiver;
        public int widthPx = -1;
    }

    private String errorCodeToString(int code, Context context){
        if (code <= 0){
            return "";
        }

    	String msg;
    	switch (code) {
    		case 408:
    			msg = context.getString(R.string.msg_sent_failed);
    			break;
    		case 477:
    			msg = context.getString(R.string.msg_sent_failed_477);
    			break;
    		default:
    			msg = "error, code ["+code+"]";
		}
    	return msg;
    }

    private String getFtErrorString(final Context ctxt, SipMessage msg){
        String err = ctxt.getString(R.string.msg_error);
        try {
            final int errorCode = msg.getErrorCode();
            if (FileTransferError.TIMEOUT.ordinal() == errorCode){
                return ctxt.getString(R.string.ft_err_timeout);
            }

        } catch(Exception ex){
            Log.e(TAG, "Exception, could not translate FT error", ex);
        }

        return err;
    }

    private void setDefaultVisibility(ViewHolder tagView){
        tagView.cryptoView.setVisibility(View.GONE); //display only in case of error
        tagView.resendButton.setVisibility(View.GONE);
        tagView.deliveredIndicator.setVisibility(View.GONE);
        tagView.statusView.setVisibility(View.VISIBLE);

        tagView.downloadedFiles.setVisibility(View.GONE);
    }

    private void markAsRead(Context context, SipMessage msg){
        ContentValues cv = new ContentValues();
        cv.put(SipMessage.FIELD_READ, 1);

        SipMessage.updateMessage(context.getContentResolver(), msg.getId(), cv);

        String sendFrom = msg.getTo();
        String sendTo = msg.getFrom();
        AmpDispatcher.dispatchReadAckNotification(context, sendFrom, sendTo, Lists.newArrayList(msg.getRandNum()));
    }

    public static String getResendTimeoutText(Context ctx, long resendTime){
        int time = 0;

        long t = resendTime - System.currentTimeMillis();
        if (t>0) {
            time = (int) (t / 1000);
        }
        return String.format(ctx.getString(R.string.msg_backoff_resend), time);
    }

    private String getTimeInformation(SipMessage msg, Context context){
        final long date = msg.getDate();
        String timestamp = formatTime(date);

        // offline message? (202 Accepted)
        if (msg.getErrorCode() == SipStatusCode.ACCEPTED && !msg.isRead()){
            timestamp += " (" + context.getString(R.string.msg_offline_message_title) + ")";
        }

        // If creation date of the message is too late from receiption date, show the difference.
        if (!msg.isOutgoing()){
            final long sendDate = msg.getSendDate();
            final long dateDiff = Math.abs(date - sendDate);
            final long dateCurDiff = Math.abs(System.currentTimeMillis() - sendDate);
            if (sendDate>0 && dateDiff >= 1000l * 60l * 5l){
                // If message was created more than 1 day back, show full date time.
                if (dateCurDiff >= 1000l * 60l * 60l * 24l){
                    timestamp += "; " + context.getString(R.string.msg_sent_date) + " " + dateFormatterLong.format(sendDate);
                } else {
                    timestamp += "; " + context.getString(R.string.msg_sent_date) + " " + dateFormatterShort.format(sendDate);
                }

            }
        }

        return timestamp;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final ViewHolder tagView = (ViewHolder) view.getTag();
        final SipMessage msg = new SipMessage(cursor);

        // mark incoming messages as read if not yet done
        if (!msg.isOutgoing() && !msg.isRead() && isFragmentForeground){
            markAsRead(context, msg);
        }

        String text = msg.getBodyContent();
        String mimeType = msg.getMimeType();
        int type = msg.getType();
        int errCode = msg.getErrorCode();

        setDefaultVisibility(tagView);

        // FileReceiver (Accept/Reject/Open file) state
        tagView.fileReceiver.setListener(getActionListener());
        tagView.fileReceiver.setMessageId(msg.getId());
        tagView.fileReceiver.setState(type);

        // File Transfer progress state
        tagView.fileTransferProgress.setState(type, msg.getId(), service);

        String timeInformation = getTimeInformation(msg, context);

        // Message status view state
        switch (type){
            case SipMessage.MESSAGE_TYPE_ENCRYPT_FAIL:
                setImage(tagView.deliveredIndicator, R.drawable.ic_sms_mms_not_delivered2);

                tagView.cryptoView.setVisibility(View.VISIBLE);
                // If certificate is missing, show specific error.
                if (msg.getErrorCode() == SipMessage.ERROR_MISSING_CERT){
                        tagView.cryptoView.setText("[" + context.getString(R.string.msg_no_cert) + "]");
                } else {
                        tagView.cryptoView.setText("[" + context.getString(R.string.msg_sent_failed_477) + "]");
                }
                break;

            case SipMessage.MESSAGE_TYPE_QUEUED: // Queued used to have R.string.dwn_p_in_queue text, now it's united
            case SipMessage.MESSAGE_TYPE_PENDING:
                setImage(tagView.deliveredIndicator, R.drawable.ic_sms_mms_pending2);
                tagView.statusView.setText(context.getString(R.string.msg_sending));
                break;

            case SipMessage.MESSAGE_TYPE_FAILED:
                setImage(tagView.deliveredIndicator, R.drawable.ic_sms_mms_not_delivered2);
                tagView.statusView.setText(errorCodeToString(errCode, context));
                break;
            case SipMessage.MESSAGE_TYPE_QUEUED_BACKOFF: // like failed but with timeout to resend
                setImage(tagView.deliveredIndicator, R.drawable.ic_sms_mms_not_delivered2);
                tagView.statusView.setText(getResendTimeoutText(context, msg.getResendTime()));

                planUiRefresh(msg);
                break;

            case SipMessage.MESSAGE_TYPE_INBOX:
            case SipMessage.MESSAGE_TYPE_SENT:
                // File types
            case SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED:
            case SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING:
            case SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING_META:
            case SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META:
            case SipMessage.MESSAGE_TYPE_FILE_READY:
            case SipMessage.MESSAGE_TYPE_FILE_REJECTED:
                tagView.statusView.setText(timeInformation);
                break;

            case SipMessage.MESSAGE_TYPE_FILE_UPLOADING:
            case SipMessage.MESSAGE_TYPE_FILE_UPLOADING_FILES:
            case SipMessage.MESSAGE_TYPE_FILE_UPLOADED:
                tagView.statusView.setVisibility(View.GONE);
                break;

            case SipMessage.MESSAGE_TYPE_FILE_ERROR_RECEIVING:
                tagView.cryptoView.setVisibility(View.VISIBLE);
                tagView.cryptoView.setText("[" + context.getString(R.string.msg_error_recv) + "]"); // TODO add icon instead of text
                tagView.statusView.setText(timeInformation);
                break;

            case SipMessage.MESSAGE_TYPE_FILE_DOWNLOAD_FAIL:
            case SipMessage.MESSAGE_TYPE_FILE_UPLOAD_FAIL:
                tagView.cryptoView.setVisibility(View.VISIBLE);
                tagView.cryptoView.setText(getFtErrorString(context, msg)); // TODO add icon instead of text
                tagView.statusView.setText(timeInformation);
                break;

            default:
                Log.wf(TAG, "Unexpected SipMessage type [%d]", type);
                tagView.statusView.setText(timeInformation);
                break;
        }

        // Enable/disable resend button
        if (msg.isOutgoing() && (type == SipMessage.MESSAGE_TYPE_FAILED)){
        	tagView.resendButton.setVisibility(View.VISIBLE);
        	tagView.resendButton.setTag(msg);
            tagView.resendButton.setOnClickListener(this);
        }

        // crypto extension - show decrypted text for incoming message
        if (!msg.isOutgoing()){
            text = "";
            StringBuilder buf = new StringBuilder();
            int decStatus = msg.getDecryptionStatus();

            boolean showCryptoView = false;

            if (decStatus == SipMessage.DECRYPTION_STATUS_OK) {
                if (!msg.isSignatureOK()) {
                    buf.append("[").append(context.getString(R.string.msg_enc_tit_signature_invalid)).append("]");
                    showCryptoView = true;
                } else {
                    text = msg.getBodyDecrypted();
                }
            } else if (decStatus == SipMessage.DECRYPTION_STATUS_NOT_DECRYPTED) {
                buf.append("[").append(context.getString(R.string.msg_enc_tit_encrypted)).append("]");
                showCryptoView = true;
            } else {
                buf.append("[").append(context.getString(R.string.msg_enc_tit_decryption_error)).append("]");
                showCryptoView = true;
            }
            if (showCryptoView) {
                tagView.cryptoView.setVisibility(View.VISIBLE);
                tagView.cryptoView.setText(buf.toString());
            }
        }

        // Message content
        tagView.contentView.setVisibility(View.VISIBLE);
        if (msg.isFileType()){
            if (showFiles(msg)){
                // Important: Pre-set height, so listview can compute scrollbar parameters + show files were deleted if count == 0
                final int filesCount = cursor.getInt(cursor.getColumnIndex(SipMessage.JOIN_FIELD_FILES_COUNT));
                // number of files that will have a thumbnail
                final int filesWithThumbnailCount;
                if (msg.canBeForwarded()) {
                    // downloaded message, how many of the files are images?
                    filesWithThumbnailCount = cursor.getInt(cursor.getColumnIndex(SipMessage.JOIN_FIELD_IMAGES_COUNT));
                } else {
                    // not yet downloaded, how many thumbnails did they send?
                    filesWithThumbnailCount = Thumbnail.getCountByMessageId(context.getContentResolver(), msg.getId());
                }

                // number of files without a thumbnail per cell
                final int smallFilesAgregateCount = 4;

                Log.df(TAG, "Files %d out of which are images %d", filesCount, filesWithThumbnailCount);

                tagView.contentView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                if (filesCount == 0){
                    if (msg.canBeForwarded()) {
                        // files were already downloaded, this means, they were deleted
                        tagView.contentView.setText(R.string.files_were_deleted);
                    } else {
                        // transfer failed
                        tagView.contentView.setText(R.string.files_were_not_sent);
                    }
                    tagView.statusView.setVisibility(View.GONE);

                } else {
                    if (msg.getType() == SipMessage.MESSAGE_TYPE_FILE_REJECTED){
                        tagView.contentView.setVisibility(View.VISIBLE);
                        tagView.contentView.setText(context.getResources().getQuantityText(R.plurals.files_rejected, filesCount));

                    } else if (hasOnlyMeta(msg)){
                        tagView.contentView.setVisibility(View.VISIBLE);
                        tagView.contentView.setText(context.getResources().getQuantityText(R.plurals.files_preview, filesCount));

                    } else {
                        tagView.contentView.setVisibility(View.GONE);

                    }

                    // File element width in pixels for height estimation, number of columns computation.
                    final int feWidthPx = LayoutUtils.dp2Pix(context.getResources(), getFileElementWidth());
                    final int feSpacing = LayoutUtils.dp2Pix(context.getResources(), RECEIVED_FILE_PADDING_DP);

                    // Minimal child height for file list height estimate.
                    final int minChildHeight = LayoutUtils.dp2Pix(context.getResources(), RECEIVED_FILE_LINE_HEIGHT_DP + RECEIVED_FILE_PADDING_DP);
                    final int smallMinChildHeight = LayoutUtils.dp2Pix(context.getResources(), RECEIVED_FILE_SMALL_ICON_HEIGHT_DP + RECEIVED_FILE_PADDING_DP);

                    // Width measurement, adjustment. Observer is invoked on layout rendering event when size
                    // is already computed so we have fresh data for file list height estimation.
                    tagView.downloadedFiles.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            tagView.downloadedFiles.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                            tagView.widthPx = tagView.downloadedFiles.getMeasuredWidth();
                            currentWrapperWidth = tagView.widthPx;

                            final int estimHeight = tagView.downloadedFiles.estimateHeight(
                                    currentWrapperWidth, 0,
                                    filesWithThumbnailCount, minChildHeight,
                                    (filesCount - filesWithThumbnailCount), smallMinChildHeight,
                                    smallFilesAgregateCount);
                            tagView.downloadedFiles.getLayoutParams().height = estimHeight;
                            Log.vf(TAG, "Downloaded files size determined: %d, height: %d, estim: %d, last: %d",
                                    currentWrapperWidth, tagView.downloadedFiles.getMeasuredHeight(), estimHeight, tagView.downloadedFiles.getLastMeasuredHeight());
                        }
                    });

                    // FlowGrid layout setup.
                    tagView.downloadedFiles.setVisibility(View.VISIBLE);
                    // Use all available free space evenly. Children resize up to maximal available width.
                    tagView.downloadedFiles.setStretchChildren(true);
                    // If there is last element on the row, resize to max.
                    tagView.downloadedFiles.setWidthFixedToCols(false);
                    // If objects counts in rows are 7,1, recomputes #of cells to 4,4, objects on a row.
                    tagView.downloadedFiles.setRecomputeColsToEven(true);
                    // Minimal child width for number of cols computation. Required to be as accurate as possible.
                    tagView.downloadedFiles.setMinimalChildWidth(feWidthPx);

                    if (currentWrapperWidth != -1){
                        curCols = Math.max(1, (int) Math.floor((double) currentWrapperWidth / (feWidthPx + feSpacing)));
                        curRows = Math.max(1, (int) Math.ceil(filesCount / (double) curCols));

                        final int estimHeight = tagView.downloadedFiles.estimateHeight(
                                currentWrapperWidth, 0,
                                filesWithThumbnailCount, minChildHeight,
                                (filesCount - filesWithThumbnailCount), smallMinChildHeight,
                                smallFilesAgregateCount);
                        tagView.downloadedFiles.getLayoutParams().height = estimHeight;

                    } else {
                        tagView.downloadedFiles.getLayoutParams().height = computeRoughHeightEstimate(context, filesCount);
                    }

                    // start async task to show all downloaded files
                    new GetDownloadedFilesTask(tagView, msg, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                // do not show icon here

            } else {
                tagView.contentView.setVisibility(View.VISIBLE);
                tagView.contentView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_attach_file_black_24px, 0, 0, 0);
                tagView.contentView.setText(MessageUtils.formatMessage(text, mimeType, context));
            }

        } else {
            tagView.contentView.setText(MessageUtils.formatMessage(text, mimeType, context));
            tagView.contentView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }

        // align and set background according to whether is outgoing or not
        LayoutParams params = new LayoutParams(
                msg.isFileType() ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tagView.containterBlock.setLayoutParams(params); // this is required to prevent view recycling to incorrectly adjusting width
        tagView.containterBlock.setOrientation(LinearLayout.VERTICAL);

        // Delivery ack & read ack.
        tagView.deliveredIndicator.setVisibility(View.GONE);
        if (type == SipMessage.MESSAGE_TYPE_SENT && !msg.isRead()){
            setImage(tagView.deliveredIndicator, R.drawable.ic_check_black_24px);
        } else if (type == SipMessage.MESSAGE_TYPE_SENT && msg.isRead()){
            setImage(tagView.deliveredIndicator, R.drawable.ic_doublecheck_black_24px);
        }

        if(msg.isOutgoing()) {
            tagView.containterBlock.setBackgroundResource(R.drawable.msg_background2_a);
            LayoutParams lp = (RelativeLayout.LayoutParams) tagView.containterBlock.getLayoutParams();
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lp.setMargins(LayoutUtils.dp2Pix(context.getResources(), 45), 0, 0, 0);
            tagView.containterBlock.setLayoutParams(lp);

        } else {
            tagView.containterBlock.setBackgroundResource(R.drawable.msg_background2_b);
            LayoutParams lp = (RelativeLayout.LayoutParams) tagView.containterBlock.getLayoutParams();
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            lp.setMargins(0, 0, LayoutUtils.dp2Pix(context.getResources(), 45), 0);
            tagView.containterBlock.setLayoutParams(lp);
        }
//        tagView.deliveredIndicator.setVisibility(View.GONE);
    }

    /**
     * Returns true if we have only meta (thumbs) for given file.
     * @param msg
     * @return
     */
    private boolean hasOnlyMeta(SipMessage msg){
        if (!msg.isFileType()){
            return false;
        }

        final int type = msg.getType();
        return (!msg.isOutgoing()
                && (
                           type == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING
                        || type == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META)
        );
    }

    /**
     * Returns true if relevant files associated to the message should be displayed.
     * @param msg
     * @return
     */
    private boolean showFiles(SipMessage msg){
        if (!msg.isFileType()){
            return false;
        }

        final int type = msg.getType();
        return (!msg.isOutgoing()
                      && (
                            type == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED
                        || type == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING
                        || type == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META
                        || type == SipMessage.MESSAGE_TYPE_FILE_REJECTED))

                || (msg.isOutgoing()
                     && (
                           type == SipMessage.MESSAGE_TYPE_FILE_UPLOADING_FILES
                        || type == SipMessage.MESSAGE_TYPE_FILE_UPLOADED
                        || type == SipMessage.MESSAGE_TYPE_FILE_UPLOAD_FAIL
                        || type == SipMessage.MESSAGE_TYPE_PENDING
                        || type == SipMessage.MESSAGE_TYPE_QUEUED
                        || type == SipMessage.MESSAGE_TYPE_QUEUED_BACKOFF
                        || type == SipMessage.MESSAGE_TYPE_SENT
                        )
                    );
    }

    /**
     * Computes rough file view height estimate, maximal space it could take.
     * @param ctxt
     * @param filesCount
     * @return
     */
    private int computeRoughHeightEstimate(Context ctxt, int filesCount){
        return LayoutUtils.dp2Pix(ctxt.getResources(), RECEIVED_FILE_LINE_HEIGHT_DP + RECEIVED_FILE_PADDING_DP) * filesCount;
    }

    private int getFileElementWidth(){
        return /*2 **/ RECEIVED_FILE_LINE_HEIGHT_DP + RECEIVED_FILE_PADDING_DP;
    }

    /**
     * Handles loaded information about file transfer and reflects it to the UI.
     *
     * @param ti
     * @param viewHolder
     * @param context
     * @throws IOException
     */
    private void showReceivedFiles(TransferInfo ti, ViewHolder viewHolder, Context context) throws IOException {
        Log.df(TAG, "showReceivedFiles");
        final FlowLayout rootView = viewHolder.downloadedFiles;
        rootView.removeAllViews();

        final int padding = LayoutUtils.dp2Pix(context.getResources(), RECEIVED_FILE_PADDING_DP);
        final int fileLineHeight = LayoutUtils.dp2Pix(context.getResources(), RECEIVED_FILE_LINE_HEIGHT_DP);
        final int smallFileLineHeight = LayoutUtils.dp2Pix(context.getResources(), RECEIVED_FILE_SMALL_ICON_HEIGHT_DP);
        final List<ReceivedFile> receivedFiles = ti.files;
        //final int filesNum = MiscUtils.collectionSize(receivedFiles);

        // Compute total size.
        long totalSize = 0;
        for(final ReceivedFile file : receivedFiles){
            totalSize += file.getSize();
        }

        List<FileView> smallItems = new LinkedList<>();

        viewHolder.fileReceiver.setTotalFileSize(totalSize);
        for(final ReceivedFile file : receivedFiles){
            //LinearLayout.LayoutParams lpx = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            //lpx.setMargins(0, 0, 0, 0);
            FileView linearLayout = new FileView(context, null, android.R.attr.borderlessButtonStyle);
            Log.df(TAG, "ReceivedFile [%s]", file.buildUri().toString());
            linearLayout.setReceivedFile(file);

            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            //linearLayout.setLayoutParams(lpx);
            linearLayout.setGravity(Gravity.START);
            linearLayout.setPadding(0, padding, 0, 0);

            linearLayout.setContentDescription(context.getString(R.string.open_file));

            messageFragment.registerForContextMenu(linearLayout);

            boolean hasThumbnail = linearLayout.init(context, ti.msg.canBeForwarded(),
                    actionListener, fileLineHeight, smallFileLineHeight, true);
            if (hasThumbnail) {
                rootView.addView(linearLayout, new FlowLayout.LayoutParams(5, 0));
            } else {
                // aggregate small views into one bigger view
                smallItems.add(linearLayout);
                if (smallItems.size() == 4) {
                    LinearLayout compoundItem = new LinearLayout(context);
                    compoundItem.setOrientation(LinearLayout.VERTICAL);
                    for (FileView fv : smallItems) {
                        compoundItem.addView(fv);
                    }
                    rootView.addView(compoundItem, new FlowLayout.LayoutParams(5, 0));
                    smallItems.clear();
                }
            }
        }
        // remaining small views at the end
        if (!smallItems.isEmpty()) {
            LinearLayout compoundItem = new LinearLayout(context);
            compoundItem.setOrientation(LinearLayout.VERTICAL);
            for (FileView fv : smallItems) {
                compoundItem.addView(fv);
            }
            rootView.addView(compoundItem, new FlowLayout.LayoutParams(5, 0));
            smallItems.clear();
        }
    }

    public void setFragmentForeground(boolean isFragmentForeground) {
        this.isFragmentForeground = isFragmentForeground;
    }

    private void setImage(ImageView imageView, int resid){
        imageView.setVisibility(View.VISIBLE);
        imageView.setImageResource(resid);
    }

    public OnMessageActionListener getActionListener() {
        return actionListener;
    }

    public void setActionListener(OnMessageActionListener actionListener) {
        this.actionListener = actionListener;
    }

    private void planUiRefresh(SipMessage msg) {
        int secondsLeft = (int) (msg.getResendTime() - System.currentTimeMillis()) / 1000;
        if (secondsLeft <= 0){
                return;
        }

        if (isRunning.compareAndSet(false, true)){
            Runnable timerRunnable = new ResendTimeRefreshRunnable(secondsLeft, msg.getId(), msg.getResendTime());
            timerHandler.postDelayed(timerRunnable, 1000);
        }
    }

    public void cancelUiRefresh(){
        timerHandler.removeCallbacksAndMessages(null);
        isRunning.set(false);
    }

    /**
     * Formats time in the UTC to the human readable form, using abbreviations if applicable.
     * @param tstamp
     * @return
     */
    private String formatTime(long tstamp){
    	String timestamp = "";
        if (System.currentTimeMillis() - tstamp > 1000l * 60l * 60l * 24l) {
            // If it was recieved one day ago or more display relative
            // timestamp - SMS like behavior
            timestamp = (String) DateUtils.relativeTimeFromNow(tstamp);
        } else {
            // If it has been recieved recently show time of reception - IM
            // like behavior
            timestamp = dateFormatterShort.format(new Date(tstamp));
        }

        return timestamp;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
    	View view = super.newView(context, cursor, parent);

        final ViewHolder tagView = new ViewHolder();
        tagView.containterBlock = (LinearLayout) view.findViewById(R.id.message_block);
        tagView.wrapper = (LinearLayout) view.findViewById(R.id.message_wrapper);
        tagView.contentView = (TextView) view.findViewById(R.id.text_view);
        tagView.cryptoView =  (TextView) view.findViewById(R.id.crypto_view);
        tagView.statusView = (TextView) view.findViewById(R.id.status_view);

        tagView.deliveredIndicator = (ImageView) view.findViewById(R.id.delivered_indicator);
        tagView.resendButton = (Button) view.findViewById(R.id.resendButton);

        tagView.fileTransferProgress = (FileTransferProgressView) view.findViewById(R.id.progress_block);
        tagView.fileReceiver = (FileReceiverView) view.findViewById(R.id.file_receiver_block);

        tagView.downloadedFiles = (FlowLayout) view.findViewById(R.id.downloaded_files);

        // In order to compute download files height we need to know its width based on the parent layout.
        // This observer is invoked when size is already measured, stores it to the internal variable
        // used later in bindView for height estimate.
        tagView.wrapper.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                tagView.wrapper.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                tagView.widthPx = tagView.wrapper.getMeasuredWidth();
                currentWrapperWidth = tagView.widthPx;
            }
        });

        view.setTag(tagView);

        return view;
    }

    public void setXService(IService service) {
        this.service = service;
    }

	@Override
	public void onClick(View v) {
		if (actionListener ==null){
			Log.e(TAG, "actionListener is null, cannot perform action on button in MessageAdapter");
		}
		if (v.getId() == R.id.resendButton){
			SipMessage msg = (SipMessage) v.getTag();
			actionListener.onResend(msg);
		} else if (v.getId() == R.id.acceptFileButton){
			long msgId = (Long) v.getTag();
			actionListener.onFileReceived(msgId, true);
		} else if (v.getId() == R.id.rejectFileButton){
			long msgId = (Long) v.getTag();
			actionListener.onFileReceived(msgId, false);
		}
	}

    // Resend interface implemented in MessageFragment
    private class ResendTimeRefreshRunnable implements Runnable {
        private int maxRunCount;
        private final long messageId;
        private final long resendTime;
        private final AtomicInteger runCount = new AtomicInteger();

        public ResendTimeRefreshRunnable(int maxCount, long messageId, long resendTime) {
            maxRunCount = maxCount;
            this.messageId = messageId;
            this.resendTime = resendTime;
        }

        @Override
        public void run() {
            if(runCount.incrementAndGet() >= maxRunCount) {
                Log.inf(TAG, "UiRefreshRunnable is cancelled!");
                isRunning.set(false);
                return;
            }

            Log.inf(TAG, "UiRefreshRunnable runs! []");
            actionListener.onResendTimeoutRefresh(messageId, resendTime);
            timerHandler.postDelayed(this, 1000);
        }
    }

    /**
     * Holder class for async file transfer information load.
     * Contains list of files, associated transfer object and message related to the transfer.
     */
    public static class TransferInfo {
        public List<ReceivedFile> files;
        public FileTransfer transfer;
        public SipMessage msg;
    }

    /**
     * Async task loader for messages representing file transfer.
     * Loads file transfer details and received files.
     */
    public class GetDownloadedFilesTask extends AsyncTask<Void, Void, TransferInfo> {
        private ViewHolder viewHolder;
        private SipMessage msg;
        private Context context;

        public GetDownloadedFilesTask(ViewHolder viewHolder, SipMessage msg, Context context) {
            this.viewHolder = viewHolder;
            this.msg = msg;
            this.context = context;
        }

        @Override
        protected TransferInfo doInBackground(Void... params) {
            Log.df(TAG, "GetDownloadedFilesTask; doInBackground");
            TransferInfo ti = new TransferInfo();
            ti.msg = msg;
            ti.files = ReceivedFile.getFilesByMsgId(context.getContentResolver(), msg.getId());
            //ti.transfer = FileTransfer.initWithNonce2(null, msg.getId(), context.getContentResolver());
            return ti;
        }

        @Override
        protected void onPostExecute(TransferInfo ti) {
            Log.df(TAG, "GetDownloadedFilesTask; onPostExecute");
            if (ti == null || ti.files==null || ti.files.isEmpty()){
                Log.wf(TAG, "No file found for nonce %s", msg.getFileNonce());
                return;
            }

            Log.df(TAG, "Received files, number: %s", ti.files.size());
            viewHolder.downloadedFiles.setTag(ti);

            try {
                showReceivedFiles(ti, viewHolder, context);
            } catch (Throwable e) {
                Log.e(TAG, "Exception in handling received files", e);
            }
        }
    }

}
