package net.phonex.ft.transfer;

import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by dusanklinec on 14.04.15.
 */
public interface TransferParameters extends Parcelable, Serializable {
    Long getMsgId();
    void setMsgId(Long msgId);
    Long getQueueMsgId();
    void setQueueMsgId(Long queueMsgId);
}
