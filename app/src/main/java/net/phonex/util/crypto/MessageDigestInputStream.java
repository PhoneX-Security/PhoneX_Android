package net.phonex.util.crypto;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

/**
 * Filter input stream for computing java.security.MessageDigest while we read the file for some
 * other purpose.
 */
public class MessageDigestInputStream extends FilterInputStream {

    private java.security.MessageDigest digest;

    /**
     * Constructs a new {@code FilterInputStream} with the specified input
     * stream as source.
     * <p>
     * <p><strong>Warning:</strong> passing a null source creates an invalid
     * {@code FilterInputStream}, that fails on every method that is not
     * overridden. Subclasses should check for null in their constructors.
     *
     * @param in the input stream to filter reads on.
     */
    public MessageDigestInputStream(InputStream in) {
        super(in);
    }

    public void setHashAlgorithm(String hashAlgorithm) throws NoSuchAlgorithmException {
        if (digest != null) {
            throw new IllegalStateException("Cannot change hash algorithm once it has been set");
        }
        digest = java.security.MessageDigest.getInstance(hashAlgorithm);
    }

    public byte[] digest() {
        return digest == null ? null : digest.digest();
    }

    @Override
    public int read() throws IOException {
        int read = super.read();
        if (read != -1 && digest != null) {
            digest.update((byte)read);
        }
        return read;
    }

    @Override
    public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
        int read = super.read(buffer, byteOffset, byteCount);
        if (read > 0 && digest != null) {
            digest.update(buffer, byteOffset, read);
        }
        return read;
    }

    @Override
    public long skip(long byteCount) throws IOException {
        return super.skip(byteCount);
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int read = super.read(buffer);
        if (read > 0 && digest != null) {
            digest.update(buffer, 0, read);
        }
        return read;
    }
}
