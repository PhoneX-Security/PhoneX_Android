package net.phonex.ui.sendFile;

import android.content.ContentResolver;
import android.net.Uri;

import net.phonex.core.Constants;
import net.phonex.db.entity.FileStorage;
import net.phonex.util.system.FilenameUtils;

import java.io.File;

/**
 * Created by miroc on 3.3.15.
 */
public class FileUtils {

    public static boolean isImage(String filename){
        final String[] IMAGE_EXTENSIONS = {"jpg", "png", "jpeg", "bmp", "gif"};
        return isOfType(filename, IMAGE_EXTENSIONS);
    }

    public static boolean isVideo(String filename){
        // TODO // FIXME: 20-Aug-15
        // Currently we do not support thumbnails for video
        // if this is fixed in the future, please also update list of file formats that are video
        return false;
//        final String[] VIDEO_EXTENSIONS = {"mp4"};
//        return isOfType(filename, VIDEO_EXTENSIONS);
    }

    private static boolean isOfType(String filename, String[] extensions){
        String extension = FilenameUtils.getExtension(filename);
        if (extension == null){
            return false;
        }

        for (String imageExtension : extensions){
            if (imageExtension.equalsIgnoreCase(extension)){
                return true;
            }
        }
        return false;
    }
}
