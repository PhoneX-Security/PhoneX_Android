package net.phonex.ui.chat.compounds;

import android.content.Context;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import net.phonex.R;
import net.phonex.db.entity.SipMessage;
import net.phonex.ui.chat.OnMessageActionListener;
import net.phonex.util.Log;

/**
 * Displays buttons for Accept/reject or open file
 * View doesn't need to retain instance, as it is part of Adapter which calls bindView appropriately and view is re-initialized there
 * Created by miroc on 29.9.14.
 */
public class FileReceiverView extends LinearLayout implements View.OnClickListener {
    private static final String TAG = "FileReceiverView";

    private Button acceptButton;
    private Button rejectButton;
    private OnMessageActionListener listener;

    // message id associated with this view
    private Long messageId;
    private Long totalFileSize;

    public FileReceiverView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.file_receiver_view, this, true);

        acceptButton = (Button) findViewById(R.id.acceptFileButton);
        rejectButton = (Button) findViewById(R.id.rejectFileButton);
        acceptButton.setOnClickListener(this);
        rejectButton.setOnClickListener(this);
    }

    public void setTotalFileSize(long totalSize){
        totalFileSize = totalSize;
        acceptButton.setText(String.format("%s (%s)",
                getContext().getString(R.string.download_file),
                Formatter.formatFileSize(getContext(), totalSize)));
    }

    /**
     * set View state
     * @param messageType should be retrieved using SipMessage#getType
     */
    public void setState(int messageType){
        if (messageType == SipMessage.MESSAGE_TYPE_FILE_READY || messageType == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED_META){
            setVisibility(View.VISIBLE);
            acceptButton.setVisibility(View.VISIBLE);
            rejectButton.setVisibility(View.VISIBLE);
//            openFileButton.setVisibility(View.GONE);
        } else if (messageType == SipMessage.MESSAGE_TYPE_FILE_DOWNLOADED){
            setVisibility(View.VISIBLE);
            acceptButton.setVisibility(View.GONE);
            rejectButton.setVisibility(View.GONE);

            // initiate Download list of files task

        } else {
            setVisibility(View.GONE);
        }
    }

    public void setListener(OnMessageActionListener listener) {
        this.listener = listener;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    @Override
    public void onClick(View view) {
        try {
            switch (view.getId()){
                case R.id.acceptFileButton:
                    listener.onFileReceived(messageId, true);
                    break;
                case R.id.rejectFileButton:
                    listener.onFileReceived(messageId, false);
                    break;
            }
        } catch (Exception ex){
            Log.e(TAG, "onClick failed", ex);
        }
    }
}
