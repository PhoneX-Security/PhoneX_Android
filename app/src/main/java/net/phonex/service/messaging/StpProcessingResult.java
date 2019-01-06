package net.phonex.service.messaging;

import java.util.Arrays;

/**
 * Created by miroc on 1.11.14.
 */
public class StpProcessingResult {
    public int ampVersion;
    public int ampType;
    public Integer sequenceNumber = null;
    public Integer nonce = null;
    public Long sendDate = null;
    public String sender;
    public String destination;

    public boolean signatureValid = false;
    public boolean hmacValid = false;
    public boolean decryptionValid = false;

    public byte[] payload;

    @Override
    public String toString() {
        return "StpProcessingResult{" +
                "ampVersion=" + ampVersion +
                ", ampType=" + ampType +
                ", sequenceNumber=" + sequenceNumber +
                ", nonce=" + nonce +
                ", sendDate=" + sendDate +
                ", sender='" + sender + '\'' +
                ", destination='" + destination + '\'' +
                ", signatureValid=" + signatureValid +
                ", hmacValid=" + hmacValid +
                ", decryptionValid=" + decryptionValid +
                ", payload=" + Arrays.toString(payload) +
                '}';
    }
}
