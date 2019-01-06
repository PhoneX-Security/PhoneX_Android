package net.phonex.ft;

import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class for duplicating written stream data to the string.
 * @author ph4r05
 *
 */
public class CopyInputStream extends InputStream {
    InputStream is;
    StringBuilder sb;

    public CopyInputStream(InputStream is){
        this.is = is;
        this.sb = new StringBuilder();
    }

    @Override
    public int read() throws IOException {
        int r = is.read();
        sb.append((char)r);

        return r;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int r = is.read(buffer, offset, length);
        for(int i=0; i<length; i++){
            sb.append((char) buffer[i+offset]);
        }

        return r;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int r = is.read(buffer);
        for(byte b : buffer){
            sb.append((char) b);
        }

        return r;
    }

    public String dump(){
        final String d = sb.toString();
        sb = new StringBuilder();
        return d;
    }
}
