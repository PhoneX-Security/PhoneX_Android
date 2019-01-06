package net.phonex.camera.util;

import net.phonex.util.Log;

/**
 * Created by Matus on 21-Jul-15.
 */
public class Exif {
    private static final String TAG = "Exif";

    // Returns the degrees in clockwise. Values are 0, 90, 180, or 270.
    public static int getOrientation(byte[] jpeg) {
        if (jpeg == null) {
            return 0;
        }

        int offset = 0;
        int length = 0;

        // ISO/IEC 10918-1:1993(E)
        while (offset + 3 < jpeg.length && (jpeg[offset++] & 0xFF) == 0xFF) {
            int marker = jpeg[offset] & 0xFF;

            // Check if the marker is a padding.
            if (marker == 0xFF) {
                continue;
            }
            offset++;

            // Check if the marker is SOI or TEM.
            if (marker == 0xD8 || marker == 0x01) {
                continue;
            }
            // Check if the marker is EOI or SOS.
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }

            // Get the length and check if it is reasonable.
            length = pack(jpeg, offset, 2, false);
            if (length < 2 || offset + length > jpeg.length) {
                Log.e(TAG, "Invalid length");
                return 0;
            }

            // Break if the marker is EXIF in APP1.
            if (marker == 0xE1 && length >= 8 &&
                    pack(jpeg, offset + 2, 4, false) == 0x45786966 &&
                    pack(jpeg, offset + 6, 2, false) == 0) {
                offset += 8;
                length -= 8;
                break;
            }

            // Skip other markers.
            offset += length;
            length = 0;
        }

        // JEITA CP-3451 Exif Version 2.2
        if (length > 8) {
            // Identify the byte order.
            int tag = pack(jpeg, offset, 4, false);
            if (tag != 0x49492A00 && tag != 0x4D4D002A) {
                Log.e(TAG, "Invalid byte order");
                return 0;
            }
            boolean littleEndian = (tag == 0x49492A00);

            // Get the offset and check if it is reasonable.
            int count = pack(jpeg, offset + 4, 4, littleEndian) + 2;
            if (count < 10 || count > length) {
                Log.e(TAG, "Invalid offset");
                return 0;
            }
            offset += count;
            length -= count;

            // Get the count and go through all the elements.
            count = pack(jpeg, offset - 2, 2, littleEndian);
            while (count-- > 0 && length >= 12) {
                // Get the tag and check if it is orientation.
                tag = pack(jpeg, offset, 2, littleEndian);
                if (tag == 0x0112) {
                    // We do not really care about type and count, do we?
                    int orientation = pack(jpeg, offset + 8, 2, littleEndian);
                    switch (orientation) {
                        case 1:
                            return 0;
                        case 3:
                            return 180;
                        case 6:
                            return 90;
                        case 8:
                            return 270;
                    }
                    Log.i(TAG, "Unsupported orientation");
                    return 0;
                }
                offset += 12;
                length -= 12;
            }
        }

        Log.i(TAG, "Orientation not found");
        return 0;
    }

    public static byte[] rotate(byte[] jpeg, int degreesCW) {
        if (degreesCW != 90 && degreesCW != -90) {
            return jpeg;
        }

        if (jpeg == null) {
            return null;
        }

        int offset = 0;
        int length = 0;

        int lengthOffset = 0;
        int lengthOriginal = 0;
        int countOffset = 0;
        int countOriginal = 0;

        // ISO/IEC 10918-1:1993(E)
        while (offset + 3 < jpeg.length && (jpeg[offset++] & 0xFF) == 0xFF) {
            int marker = jpeg[offset] & 0xFF;

            // Check if the marker is a padding.
            if (marker == 0xFF) {
                continue;
            }
            offset++;

            // Check if the marker is SOI or TEM.
            if (marker == 0xD8 || marker == 0x01) {
                continue;
            }
            // Check if the marker is EOI or SOS.
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }

            // Get the length and check if it is reasonable.
            length = pack(jpeg, offset, 2, false);
            if (length < 2 || offset + length > jpeg.length) {
                Log.e(TAG, "Invalid length");
                return null;
            }

            // Break if the marker is EXIF in APP1.
            if (marker == 0xE1 && length >= 8 &&
                    pack(jpeg, offset + 2, 4, false) == 0x45786966 &&
                    pack(jpeg, offset + 6, 2, false) == 0) {

                lengthOffset = offset; // this will have to be modified, if prolonged
                lengthOriginal = length;

                offset += 8;
                length -= 8;
                break;
            }

            // Skip other markers.
            offset += length;
            length = 0;
        }

        // JEITA CP-3451 Exif Version 2.2
        if (length > 8) {
            // Identify the byte order.
            int tag = pack(jpeg, offset, 4, false);
            if (tag != 0x49492A00 && tag != 0x4D4D002A) {
                Log.e(TAG, "Invalid byte order");
                return null;
            }
            boolean littleEndian = (tag == 0x49492A00);

            // Get the offset and check if it is reasonable.
            int count = pack(jpeg, offset + 4, 4, littleEndian) + 2;
            if (count < 10 || count > length) {
                Log.e(TAG, "Invalid offset");
                return null;
            }
            offset += count;
            length -= count;

            // Get the count and go through all the elements.
            count = pack(jpeg, offset - 2, 2, littleEndian);

            countOriginal = count;
            countOffset = offset - 2;

            int newOrientationTag = 0;

            while (count-- > 0 && length >= 12) {
                // Get the tag and check if it is orientation.
                tag = pack(jpeg, offset, 2, littleEndian);
                if (tag == 0x0112) {
                    int orientation = pack(jpeg, offset + 8, 2, littleEndian);
                    switch (orientation) {
                        case 1:
                            // old 0;
                            if (degreesCW == 90) {
                                newOrientationTag = 6;
                            } else {
                                newOrientationTag = 8;
                            }
                            break;
                        case 3:
                            // old 180;
                            if (degreesCW == 90) {
                                newOrientationTag = 8;
                            } else {
                                newOrientationTag = 6;
                            }
                            break;
                        case 6:
                            // old 90;
                            if (degreesCW == 90) {
                                newOrientationTag = 3;
                            } else {
                                newOrientationTag = 1;
                            }
                            break;
                        case 8:
                            // old 270;
                            if (degreesCW == 90) {
                                newOrientationTag = 1;
                            } else {
                                newOrientationTag = 3;
                            }
                            break;
                    }
                    Log.i(TAG, "Setting the tag to " + newOrientationTag);
                    unpack(jpeg, offset + 8, 2, littleEndian, newOrientationTag);

                    return jpeg;
                }
                if (count == 0) {
                    switch (degreesCW) {
                        case 90:
                            newOrientationTag = 6;
                            break;
                        case -90:
                            newOrientationTag = 8;
                            break;
                    }

                    Log.i(TAG, "Orientation not found, have to put it in");
                    // increase total length
                    byte[] newJpeg = new byte[jpeg.length + 12];
                    System.arraycopy(jpeg, 0, newJpeg, 0, offset);
                    unpack(newJpeg, offset, 2, littleEndian, 0x0112); // the tag 274 or 0x0112
                    unpack(newJpeg, offset + 2, 2, littleEndian, 0x0003);
                    unpack(newJpeg, offset + 4, 2, littleEndian, 0x0001);
                    unpack(newJpeg, offset + 6, 2, littleEndian, 0x0000);
                    unpack(newJpeg, offset + 8, 2, littleEndian, newOrientationTag);
                    unpack(newJpeg, offset + 10, 2, littleEndian, 0x0000);
                    System.arraycopy(jpeg, offset, newJpeg, offset + 12, jpeg.length - offset); // copy the rest
                    unpack(newJpeg, lengthOffset, 2, false, lengthOriginal + 12); // length is always big endian
                    unpack(newJpeg, countOffset, 2, littleEndian, countOriginal + 1);
                    Log.i(TAG, "Orientation tag added");
                    return newJpeg;
                }
                offset += 12;
                length -= 12;
            }
        }

        Log.i(TAG, "Orientation not found");
        return null;
    }

    private static int pack(byte[] bytes, int offset, int length,
                            boolean littleEndian) {
        int step = 1;
        if (littleEndian) {
            offset += length - 1;
            step = -1;
        }

        int value = 0;
        while (length-- > 0) {
            value = (value << 8) | (bytes[offset] & 0xFF);
            offset += step;
        }
        return value;
    }

    private static void unpack(byte[] bytes, int offset, int length,
                               boolean littleEndian, int value) {
        int step = 1;
        if (littleEndian) {
            offset += length - 1;
            step = -1;
        }

        while (length-- > 0) {
            //value = (value << 8) | (bytes[offset] & 0xFF);
            bytes[offset] = (byte) ((value >> length * 8) & 0xFF);
            offset += step;
        }
    }
}
