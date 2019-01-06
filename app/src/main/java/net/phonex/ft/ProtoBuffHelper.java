package net.phonex.ft;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProtoBuffHelper {
	/**
     * Serializes Google ProtocolBuffer message to byte array.
     * @param msg
     * @return
     * @throws IOException
     */
    public static byte[] writeTo(com.google.protobuf.GeneratedMessage msg) throws IOException{
    	ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
    	msg.writeTo(bos1);
    	
    	byte[] ret = bos1.toByteArray();
    	bos1.close();
    	
    	return ret;
    }

    /**
     * Serializes Google ProtocolBuffer message to byte array.
     * @param msg
     * @return
     * @throws IOException
     */
    public static byte[] writeDelimitedTo(com.google.protobuf.GeneratedMessage msg) throws IOException{
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        msg.writeDelimitedTo(bos1);

        byte[] ret = bos1.toByteArray();
        bos1.close();

        return ret;
    }

    /**
     * Reads message size of the next message in the stream.
     * @param is
     * @return
     */
    public static int getDelimitedMessageSize(InputStream is) throws InvalidProtocolBufferException {
        int structSize = 0;
        try {
            int firstByte = is.read();
            if (firstByte == -1) {
                throw new IOException("Stream cannot be opened");
            }

            structSize = CodedInputStream.readRawVarint32(firstByte, is);
            return structSize;
        } catch (IOException e) {
            throw new InvalidProtocolBufferException(e.getMessage());
        }
    }
}
