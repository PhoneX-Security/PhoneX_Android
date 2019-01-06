package net.phonex.ft.misc;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Taken from protocol buffers library.
 * Created by dusanklinec on 20.04.15.
 */
public final class LimitedInputStream extends FilterInputStream {
    private int limit;

    public LimitedInputStream(InputStream in, int limit) {
        super(in);
        this.limit = limit;
    }

    @Override
    public int available() throws IOException {
        return Math.min(super.available(), limit);
    }

    @Override
    public int read() throws IOException {
        if (limit <= 0) {
            return -1;
        }
        final int result = super.read();
        if (result >= 0) {
            --limit;
        }
        return result;
    }

    @Override
    public int read(final byte[] b, final int off, int len)
            throws IOException {
        if (limit <= 0) {
            return -1;
        }
        len = Math.min(len, limit);
        final int result = super.read(b, off, len);
        if (result >= 0) {
            limit -= result;
        }
        return result;
    }

    @Override
    public long skip(final long n) throws IOException {
        final long result = super.skip(Math.min(n, limit));
        if (result >= 0) {
            limit -= result;
        }
        return result;
    }
}
