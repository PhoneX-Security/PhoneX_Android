package net.phonex.ft;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Helper class for duplicating written stream data to the string.
 * @author ph4r05
 *
 */
public class CopyOutputStream extends OutputStream {
    OutputStream os;
    StringBuilder sb;

    public CopyOutputStream(OutputStream os){
        this.os = os;
        this.sb = new StringBuilder();
    }

    @Override
    public void write(int arg0) throws IOException {
        sb.append((char)arg0);
        os.write(arg0);
    }

    @Override
    public void write(byte[] b) throws IOException {
        if (b==null) return;
        for(byte i : b){
            sb.append((char)i);
        }

        os.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b==null) return;
        for(int i=0; i<len; i++){
            sb.append((char)b[off+i]);
        }

        os.write(b, off, len);
    }

    public String dump(){
        final String d = sb.toString();
        sb = new StringBuilder();
        return d;
    }
}
