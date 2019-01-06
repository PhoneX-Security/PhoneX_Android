package net.phonex.ft.misc;

/**
 * Options for resizing photos.
 *
 * Created by Matus on 06-Oct-15.
 */
public class PhotoResizeOptions {
    private boolean doResize = false;
    private int longEdgePixels;
    private double lowerBoundPercent;
    private double upperBoundPercent;
    private int jpegQuality;

    /**
     * Should the photo be resized according to these configs?
     * @return true if resize, false if photo should be sent as is
     */
    public boolean doResize() {
        return doResize;
    }

    /**
     * Should the photo be resized according to these configs?
     */
    public void setDoResize(boolean doResize) {
        this.doResize = doResize;
    }

    /**
     * length of the longer edge in pixels (1-reasonable, more than 1024 may lead to frequent OOM)
     * @return length of the longer edge in pixels
     */
    public int getLongEdgePixels() {
        return longEdgePixels;
    }

    /**
     * length of the longer edge in pixels (1-reasonable, more than 1024 may lead to frequent OOM)
     */
    public void setLongEdgePixels(int longEdgePixels) {
        this.longEdgePixels = longEdgePixels;
    }

    /**
     * how much shorter is still acceptable (0-1), 0 - exact size, 0.5 - 50 % shorter etc.
     * @return how much shorter is still acceptable (0-1)
     */
    public double getLowerBoundPercent() {
        return lowerBoundPercent;
    }

    /**
     * how much shorter is still acceptable (0-1), 0 - exact size, 0.5 - 50 % shorter etc.
     */
    public void setLowerBoundPercent(double lowerBoundPercent) {
        this.lowerBoundPercent = lowerBoundPercent;
    }

    /**
     * how much longer is still acceptable (0-1), 0 - exact size, 0.5 - 50 % longer etc.
     * @return how much longer is still acceptable (0-1), 0 - exact size, 0.5 - 50 % longer etc.
     */
    public double getUpperBoundPercent() {
        return upperBoundPercent;
    }

    /**
     * how much longer is still acceptable (0-1), 0 - exact size, 0.5 - 50 % longer etc.
     */
    public void setUpperBoundPercent(double upperBoundPercent) {
        this.upperBoundPercent = upperBoundPercent;
    }

    /**
     * JPEG compression quality (percent 0-100)
     * @return JPEG compression quality (percent 0-100)
     */
    public int getJpegQuality() {
        return jpegQuality;
    }

    /**
     * JPEG compression quality (percent 0-100)
     */
    public void setJpegQuality(int jpegQuality) {
        this.jpegQuality = jpegQuality;
    }
}
