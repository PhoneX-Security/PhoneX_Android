package net.phonex.ui.chat.compounds;

import android.content.Context;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.core.IService;
import net.phonex.db.entity.SipMessage;
import net.phonex.pub.parcels.FileTransferProgress;
import net.phonex.util.Log;

/**
 * Created by miroc on 29.9.14.
 */
public class FileTransferProgressView extends LinearLayout{
    private static final String TAG = "FileTransferProgressView";


    private ProgressBar progressBar;
    private TextView progressText;


    public FileTransferProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.file_transfer_progress_view, this, true);

        progressBar = (ProgressBar) findViewById(R.id.file_transfer_progress);
        progressText = (TextView) findViewById(R.id.file_transfer_text);
    }

    public void updateProgress(FileTransferProgress progress){
        final int step = progress.getProgress();//progress.getDeltaStep();
        final String msg = progress.getTitle();//();

        progressText.setText(msg);

        // Negative means indeterminate progress.
        if (step<0){
            progressBar.setIndeterminate(true);
        } else if (step > 0){
            progressBar.setIndeterminate(false);
            progressBar.setProgress(step);
        }
    }

    /**
     * set View state
     */
    public void setState(int messageType, long messageId, IService service){
        switch (messageType){

            case SipMessage.MESSAGE_TYPE_FILE_UPLOADING:
            case SipMessage.MESSAGE_TYPE_FILE_UPLOADING_FILES:
            case SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING:
            case SipMessage.MESSAGE_TYPE_FILE_DOWNLOADING_META:
                setVisibility(View.VISIBLE);
                // retrieve uploading/downloading status from XService
                if (service!=null){
                    try {
                        FileTransferProgress progress = service.getFileTransferProgress(messageId);
                        updateProgress(progress);

                    } catch (RemoteException e) {
                        Log.wf(TAG, e, "unable to retrieve the progress of file transfer for SipMessge with id=%s", messageId);
                    } catch (Exception e){
                        Log.e(TAG, "Exception in processing file transfer progress", e);
                    }
                }

                break;
            default:
                setVisibility(View.GONE);
        }

    }
}
