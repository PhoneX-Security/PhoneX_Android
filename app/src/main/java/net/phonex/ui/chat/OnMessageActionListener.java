package net.phonex.ui.chat;

import net.phonex.db.entity.ReceivedFile;
import net.phonex.db.entity.SipMessage;
import net.phonex.ft.storage.FileStorageUri;

/**
 * Message action listener.
 * Created by dusanklinec on 25.04.15.
 */
public interface OnMessageActionListener {
    public void onResend(SipMessage msg);
    public void onFileReceived(long messageId, boolean isAccepted);
    public void onResendTimeoutRefresh(long messageId, long resendTime);
    public void openFile(ReceivedFile file);
    void decryptFile(FileStorageUri uri);
    void deleteFileClone(FileStorageUri uri);
}
