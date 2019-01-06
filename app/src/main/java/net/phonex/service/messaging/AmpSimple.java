package net.phonex.service.messaging;

import com.google.protobuf.ByteString;

import net.phonex.pub.proto.Messages;
import net.phonex.util.MiscUtils;

import java.io.IOException;

/**
 * Application message protocol
 * Created by miroc on 5.10.14.
 * AmpSimple uses Gzipping before storing message string
 */
public class AmpSimple {
    private static final String TAG = "AmpSimple";

    private final Integer nonce;
    private final String message;

    private AmpSimple(Integer nonce, String message) {
        this.nonce = nonce;
        this.message = message;
    }

    public static byte[] buildSerializedMessage(String message, int nonce) throws IOException {
        return buildMessage(message, nonce).toByteArray();
    }

    private static Messages.AMPSimple buildMessage(String message, int nonce) throws IOException {
        Messages.AMPSimple.Builder builder = Messages.AMPSimple.newBuilder();

        byte[] messageBytes = message.getBytes("UTF-8");
        byte[] compressed = MiscUtils.compressToBytes(messageBytes);

        builder.setMessage(ByteString.copyFrom(compressed));
        builder.setNonce(nonce);

        return builder.build();
    }

    public static AmpSimple loadMessage(byte[] serializedAmpSimple) throws IOException {
        Messages.AMPSimple ampSimple = Messages.AMPSimple.parseFrom(serializedAmpSimple);
        byte[] compressedString = ampSimple.getMessage().toByteArray();

        byte[] decoded = MiscUtils.decompress(compressedString);

        String msg = new String(decoded, "UTF-8");
        return new AmpSimple(ampSimple.hasNonce() ? ampSimple.getNonce() : null, msg);
    }

    public Integer getNonce() {
        return nonce;
    }

    /**
     * get decoded text (=plaintext)
     * @return
     */
    public String getMessage() {
        return message;
    }
}
