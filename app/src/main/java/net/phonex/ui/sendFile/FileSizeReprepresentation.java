package net.phonex.ui.sendFile;

import net.phonex.util.Log;

import java.math.BigDecimal;

/**
 * Created by eldred on 23/02/15.
 */
public class FileSizeReprepresentation {

    static final long KILO = 1024L;
    static final long MEGA = KILO * 1024L;
    static final long GIGA = MEGA * 1024L;

    public String units;
    public BigDecimal size;

    @Override
    public String toString() {

        return String.format("%.2f %s", size.doubleValue(), units);

    }

    public static FileSizeReprepresentation bytesToRepresentation(final long sizeInBytes) {

        FileSizeReprepresentation result = new FileSizeReprepresentation();

        long divider = 1L;

        if (sizeInBytes < KILO) {
            result.units = "B";
        } else if (sizeInBytes < MEGA) {
            result.units = "KB";
            divider = KILO;
        } else if (sizeInBytes < GIGA) {
            result.units = "MB";
            divider = MEGA;
        } else {
            result.units = "GB";
            divider = GIGA;
        }

        result.size = (new BigDecimal(sizeInBytes)).divide(new BigDecimal(divider));

        return result;
    }
}
