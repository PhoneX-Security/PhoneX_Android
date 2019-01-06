package net.phonex.service.messaging;

import net.phonex.util.UserMessageOutput;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * TransportProtocolDispatcher -> AmpProtocolDispatcher data packet
 * Contains payload + additional information from transport layer
 * Created by miroc on 17.10.14.
 */
public class DecryptedTransportPacket {
    public byte[] payload;
    public boolean payloadIsString = false;
    public int ampType;
    public int ampVersion;

    public boolean isValid;

    public Integer nonce = null;
    public Long sendDate = null;
    public String from = null;
    public String to = null;

    public boolean macValid;
    public boolean signatureValid;
    public Map<String, String> properties;

    // legacy properties
    String transportPacketHash;

    // legacy way
    public static DecryptedTransportPacket createFrom(UserMessageOutput output, int ampType, int ampVersion){
        DecryptedTransportPacket packet = new DecryptedTransportPacket();
        // payload retrieved from S/MIME, it's Base64 encoded
        packet.payloadIsString = true;

        packet.isValid = output.isTextPartValid();
        packet.from = output.getSourceSip();
        packet.signatureValid = output.isVerifiedSuccessfully();
        packet.macValid = output.isHmacValid();
        packet.properties = new HashMap<String, String>();
        // text part is stored as bytes
        packet.payload = output.getTextPart().getBytes();
        packet.nonce = output.getRandNum();
        packet.sendDate = output.getSendDate();

        packet.ampType = ampType;
        packet.ampVersion = ampVersion;
        return packet;
    }

    public static DecryptedTransportPacket createFrom(StpProcessingResult output){
        DecryptedTransportPacket packet = new DecryptedTransportPacket();
        packet.ampType = output.ampType;
        packet.ampVersion = output.ampVersion;
        packet.to = output.destination;
        packet.from = output.sender;
        packet.nonce = output.nonce;

        packet.sendDate = output.sendDate;
        packet.macValid = output.hmacValid;
        packet.signatureValid = output.signatureValid;

        packet.isValid = output.signatureValid && output.hmacValid && output.decryptionValid;

        packet.properties = new HashMap<String, String>();
        packet.payload = output.payload;
        return packet;
    }

    @Override
    public String toString() {
        return "DecryptedTransportPacket{" +
                "payload=" + Arrays.toString(payload) +
                ", payloadIsString=" + payloadIsString +
                ", ampType=" + ampType +
                ", ampVersion=" + ampVersion +
                ", isValid=" + isValid +
                ", nonce=" + nonce +
                ", sendDate=" + sendDate +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", macValid=" + macValid +
                ", signatureValid=" + signatureValid +
                ", properties=" + properties +
                ", transportPacketHash='" + transportPacketHash + '\'' +
                '}';
    }
}
