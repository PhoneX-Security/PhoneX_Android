package net.phonex.ft.misc;

/**
 * Progress interface for transmit process (upload/download).
 * @author ph4r05
 *
 */
public interface TransmitProgressI {
    public void updateTxProgress(Double partial, double total); // partial progress (1 file) and total progress (overall).
}
