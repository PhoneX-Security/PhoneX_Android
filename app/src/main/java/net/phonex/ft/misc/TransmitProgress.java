package net.phonex.ft.misc;

/**
 * Progress interface for transmit process (upload/download).
 * @author ph4r05
 *
 */
abstract public class TransmitProgress implements TransmitProgressI {
    /**
     * Partial progress (1 file) and total progress (overall).
     */
    abstract public void updateTxProgress(Double partial, double total);

    /**
     * Progress update for operation & suboperation. Returns number of bytes processed.
     * Used if total number is not available.
     *
     * @param cur
     */
    public void updateTxProgress(Long cur) {
        if (cur != null) {
            updateTxProgress(null, cur);
        }
    }

    /**
     * Resets current progress.
     */
    public void setTotal(Long partial, long total){ }

    /**
     * Can set total number of some units that need to be processed.
     */
    public void setTotal(long total){ }

    /**
     * Set total number of operations.
     * @param totalOps
     */
    public void setTotalOps(Integer totalOps) {}

    /**
     * Set current operation.
     * @param op
     */
    public void setCurOp(Integer op) { }

    /**
     * Set total number of sub operations in current operation.
     * @param totalSubOps
     */
    public void setTotalSubOps(Integer totalSubOps) {}

    /**
     * Set current sub-operation.
     * @param subOp
     */
    public void setCurSubOp(Integer subOp) { }

    /**
     * Can set total number of some units that need to be processed.
     */
    public void reset(){ }
}
