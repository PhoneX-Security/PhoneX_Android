package net.phonex.ft.misc;

/**
 * Class for cancelling ongoing progress.
 * @author ph4r05
 *
 */
public interface Canceller {
    /**
     * Determines if current operation is cancelled.
     * @return
     */
    public boolean isCancelled();
}
