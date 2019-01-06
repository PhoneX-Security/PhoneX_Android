package net.phonex.service.messaging;

import net.phonex.util.crypto.CryptoHelper;

/**
 * Secure transport protocol interface
 * Created by miroc on 2.11.14.
 */
public interface Stp {
    byte[] buildMessage(byte[] payload, String destination, int ampType, int ampVersion) throws CryptoHelper.CipherException;
    StpProcessingResult readMessage(byte[] serializedStpMessage, int stpType, int stpVersion) throws CryptoHelper.CipherException;

    void setVersion(Integer transportProtocolVersion);
}
