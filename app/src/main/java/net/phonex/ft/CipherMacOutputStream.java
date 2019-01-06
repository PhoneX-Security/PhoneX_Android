package net.phonex.ft;

import net.phonex.util.Log;

import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.util.io.Streams;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NullCipher;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * This class wraps an output stream and a cipher so that {@code write} methods
 * send the data through the cipher before writing them to the underlying output
 * stream.
 * <p>
 * The cipher must be initialized for the requested operation before being used
 * by a {@code CipherOutputStream}. For example, if a cipher initialized for
 * encryption is used with a {@code CipherOutputStream}, the {@code
 * CipherOutputStream} tries to encrypt the data writing it out.
 */
public class CipherMacOutputStream extends FilterOutputStream {
    private final Cipher cipher;
    private final Mac mac;

    /* the buffer holding one byte of incoming data */
    private byte[] ibuffer = new byte[1];

    // the buffer holding data ready to be written out
    private byte[] obuffer;

    /**
     * Creates a new {@code CipherOutputStream} instance for an {@code
     * OutputStream} and a {@code Cipher}.
     *
     * @param os
     *            the output stream to write data to.
     * @param c
     *            the cipher to process the data with.
     */
    public CipherMacOutputStream(OutputStream os, Cipher c, Mac m) {
        super(os);
        cipher = c;
        mac = m;
    }

    /**
     * Creates a new {@code CipherOutputStream} instance for an {@code
     * OutputStream} without a cipher.
     * <p>
     * A {@code NullCipher} is created to process the data.
     *
     * @param os
     *            the output stream to write the data to.
     */
    protected CipherMacOutputStream(OutputStream os) {
        this(os, new NullCipher(), null);
    }

    /**
     * Writes the single byte to this cipher output stream.
     *
     * @param b
     *            the byte to write.
     * @throws java.io.IOException
     *             if an error occurs.
     */
    public void write(int b) throws IOException {
        ibuffer[0] = (byte) b;
        obuffer = cipher.update(ibuffer, 0, 1);
        if (obuffer != null) {
            if (mac != null && obuffer.length > 0){
                mac.update(obuffer);
            }

            out.write(obuffer);
            obuffer = null;
        }
    }

    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes the {@code len} bytes from buffer {@code b} starting at offset
     * {@code off} to this cipher output stream.
     *
     * @param b
     *            the buffer.
     * @param off
     *            the offset to start at.
     * @param len
     *            the number of bytes.
     * @throws IOException
     *             if an error occurs.
     */
    @Override public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        byte[] result = cipher.update(b, off, len);
        if (mac != null && result != null && result.length > 0){
            mac.update(result, 0, result.length);
        }

        if (result != null) {
            out.write(result);
        }
    }

    /**
     * Flushes this cipher output stream.
     *
     * @throws IOException
     *             if an error occurs
     */
    @Override
    public void flush() throws IOException {
        if (obuffer != null) {
            out.write(obuffer);
            obuffer = null;
        }
        out.flush();
    }

    public Mac getMac() {
        return mac;
    }

    /**
     * Close this cipher output stream.
     * <p>
     * On the underlying cipher {@code doFinal} will be invoked, and any
     * buffered bytes from the cipher are also written out, and the cipher is
     * reset to its initial state. The underlying output stream is also closed.
     *
     * @throws IOException
     *             if an error occurs.
     */
    @Override
    public void close() throws IOException {
        byte[] result;
        try {
            if (cipher != null) {
                result = cipher.doFinal();
                if (result != null) {
                    if (mac != null && result.length > 0){
                        mac.update(result, 0, result.length);
                    }

                    out.write(result);
                }
            }
            if (out != null) {
                out.flush();
            }
        } catch (BadPaddingException e) {
            throw new IOException(e.getMessage());
        } catch (IllegalBlockSizeException e) {
            throw new IOException(e.getMessage());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}

