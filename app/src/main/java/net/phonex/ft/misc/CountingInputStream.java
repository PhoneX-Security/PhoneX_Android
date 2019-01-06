package net.phonex.ft.misc;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by dusanklinec on 20.04.15.
 */
public class CountingInputStream  extends InputStream {
    protected InputStream is;
    protected long ctr = 0;

    public CountingInputStream(InputStream is){
        this.is = is;
        this.ctr = 0;
    }

    @Override
    public int read() throws IOException {
        int r = is.read();
        ctr += r > 0 ? 1 : 0;

        return r;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int r = is.read(buffer, offset, length);
        ctr += r > 0 ? r : 0;

        return r;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int r = is.read(buffer);
        ctr += r > 0 ? r : 0;

        return r;
    }

    public void resetCtr(){
        ctr = 0;
    }

    public long getCtr(){
        return ctr;
    }
}
