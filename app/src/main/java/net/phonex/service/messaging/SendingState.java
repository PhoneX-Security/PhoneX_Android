package net.phonex.service.messaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by miroc on 20.10.14.
 */
public class SendingState {
    public enum Type{
        // message is taken from queue (marked as processed, and we are trying to send it, which may include multiple resends)
        SENDING,
        // destination acknowledged receiving
        ACK_POSITIVE,
        // negative ack, sending failed and we know it
        ACK_NEGATIVE,
        // Marked in MessageQueue as unprocessed and with given timeout for resending
        SET_FOR_BACKOFF,
        // errors
        FAILED_INVALID_DESTINATION,
        FAILED_MISSING_REMOTE_CERT,
        FAILED_REACHED_MAX_NUM_OF_RESENDS,
        FAILED_GENERIC // any other error that might happen
        ;
    }

    public SendingState(Type type) {
        this.type = type;
    }

    public SendingState(Type type, int pjsipErrorCode, String pjsipErrorText) {
        this.type = type;
        this.pjsipErrorCode = pjsipErrorCode;
        this.pjsipErrorText = pjsipErrorText;
    }

    public static SendingState setForBackoff(long resendTime){
        SendingState s = new SendingState(Type.SET_FOR_BACKOFF);
        s.resendTime = resendTime;
        return s;
    }

    public Type type;
    public int pjsipErrorCode;
    public String pjsipErrorText;
    public long resendTime;

    // helper static classes for easy access
    public static final SendingState SENDING = new SendingState(Type.SENDING);
    public static final SendingState ACK_POSITIVE = new SendingState(Type.ACK_POSITIVE);
    public static final SendingState FAILED_INVALID_DESTINATION = new SendingState(Type.FAILED_INVALID_DESTINATION);
    public static final SendingState FAILED_MISSING_REMOTE_CERT = new SendingState(Type.FAILED_MISSING_REMOTE_CERT);
    public static final SendingState FAILED_GENERIC = new SendingState(Type.FAILED_GENERIC);
    public static final SendingState FAILED_REACHED_MAX_NUM_OF_RESENDS = new SendingState(Type.FAILED_REACHED_MAX_NUM_OF_RESENDS);

    @Override
    public String toString() {
        return "SendingState{" +
                "type=" + type +
                ", pjsipErrorCode=" + pjsipErrorCode +
                ", pjsipErrorText='" + pjsipErrorText + '\'' +
                ", resendTime=" + resendTime +
                '}';
    }
}