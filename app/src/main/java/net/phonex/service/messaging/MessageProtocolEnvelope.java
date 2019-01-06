package net.phonex.service.messaging;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import net.phonex.pub.proto.Messages;
import net.phonex.util.Base64;

import java.io.IOException;

/**
 * Application message protocol
 * Created by miroc on 9.10.14.
 */
public class MessageProtocolEnvelope {

    private MessageProtocolEnvelope(Messages.MessageProtocolEnvelope envelope) {
        this.envelope = envelope;
    }

    private Messages.MessageProtocolEnvelope envelope;

    public static MessageProtocolEnvelope createEnvelope(byte[] payload, int protocolType, int protocolVersion){
        Messages.MessageProtocolEnvelope.Builder builder = Messages.MessageProtocolEnvelope.newBuilder();
        builder.setPayload(ByteString.copyFrom(payload));
        builder.setProtocolType(protocolType);
        builder.setProtocolVersion(protocolVersion);
        return new MessageProtocolEnvelope(builder.build());
    }

    public static MessageProtocolEnvelope createEnvelope(String textPayload) throws IOException {
        return createEnvelope(Base64.decode(textPayload));
    }

    public static MessageProtocolEnvelope createEnvelope(byte[] serializedEnvelope) throws InvalidProtocolBufferException {
        Messages.MessageProtocolEnvelope obj = Messages.MessageProtocolEnvelope.parseFrom(serializedEnvelope);
        return new MessageProtocolEnvelope(obj);
    }

    public int getProtocolType(){
        return envelope.getProtocolType();
    }

    public int getProtocolVersion(){
        return envelope.getProtocolVersion();
    }

    public byte[] getPayload(){
        return envelope.getPayload().toByteArray();
    }

    public byte[] getSerialized(){
        return envelope.toByteArray();
    }

    public String getBase64EncodedSerialized() {
        return Base64.encodeBytes(getSerialized());
    }
}
