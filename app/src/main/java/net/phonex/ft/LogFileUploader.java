package net.phonex.ft;

import net.phonex.ft.misc.TransmitProgress;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;
import net.phonex.util.crypto.CryptoHelper;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Log file uploader
 *
 * @author miroc
 *
 */
public class LogFileUploader extends GenericUploader{
    private static final String FILENAME = "logfile_android.log";
    private long uploadedSize = 0;

    private String appVersion;
    private String userMessage;

    private File fileToUpload;

    interface UploadParams {
        String VERSION = "version";
        String RESOURCE = "resource";
        String APP_VERSION = "appVersion";
        String MESSAGE = "message";
        String AUX_JSON = "auxJSON";
        String LOG_FILE = "logfile";
    }

    public LogFileUploader(String targetUrl, File fileToUpload, String userMessage, String appVersion) {
        super(targetUrl);
        this.fileToUpload = fileToUpload;
        this.userMessage = userMessage;
        this.appVersion = appVersion;
    }

    @Override
    protected void uploadFileInternals(DataOutputStream outputStream) throws IOException {
        outputStream.writeBytes(twoHyphens + boundary + lineEnd);

        // Load file sizes in order to initialize progress bar.
//        totalTxmitSize = holder.fileSize[DHKeyHelper.META_IDX] + holder.fileSize[DHKeyHelper.ARCH_IDX];
        uploadedSize = 0;

        // Write request
        writeString(UploadParams.VERSION, "1", false);
        writeString(UploadParams.RESOURCE, "", false);
        writeString(UploadParams.APP_VERSION, appVersion, false);
        writeString(UploadParams.MESSAGE, userMessage, false);
        writeString(UploadParams.AUX_JSON, "", false);

        // Dump after only text was written (displayable)
        if (debug){
            Log.vf(TAG, "Server dump1: %s", cos.dump());
        }


        // filename is not that important
        writeFile(fileToUpload, UploadParams.LOG_FILE, FILENAME, true);
        Log.v(TAG, "All fields uploaded");
    }

    /**
     * Writes application/octet-stream file as multipart/form-data.
     */
    private void writeFile(File file, String name, String filename, boolean lastOne) throws IOException {
        if (!file.exists()){
            throw new IOException("Specified file does not exist, cannot continue.");
        }

        outputStream.writeBytes(String.format(fileHeader, name, filename));
        outputStream.writeBytes("Content-Type: application/octet-stream" + lineEnd);
        outputStream.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);
        outputStream.writeBytes(lineEnd);

        writeFileToStream(outputStream, file, new TransmitProgress() {
            @Override
            public void updateTxProgress(Double partial, double total) {
//                DHKeyHelper.updateProgress(progress, total, (curFileLen * total + (double) uploadedSize) / (double) totalTxmitSize);
            }
        });

        outputStream.writeBytes(lineEnd);
        outputStream.writeBytes(twoHyphens + boundary + (lastOne ? twoHyphens : "") + lineEnd);

        // For progress bar.
//        uploadedSize += holder.fileSize[fileIdx];
        Log.vf(TAG, "Upload of fileIdx=%s done, uploaded=%s;", file.getName(), uploadedSize);
    }


    protected void writeFileToStream(OutputStream os, File file, TransmitProgress progress) throws IOException{
        long totalSize = file.length();
        Log.df(TAG, "writeFileToStream; fileSize=%d bytes", totalSize);
//
//        // Some additional progress information that may be useful.
//        if (progress!=null){
//            progress.reset();
//            progress.setTotal(totalSize);
//        }

        ZipEntry zipEntry = new ZipEntry(FILENAME);

        ZipOutputStream zipOutputStream = new ZipOutputStream(new CryptoHelper.NotClosingOutputStream(os));
        zipOutputStream.putNextEntry(zipEntry);

        // Write encrypted file
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            int numBytes;
            byte[] bytes = new byte[1024];
            while ((numBytes = bis.read(bytes)) != -1) {
//                os.write(bytes, 0, numBytes);
                zipOutputStream.write(bytes, 0, numBytes);

//                updateProgress(progress, null, (double)written / (double)totalSize);
//                if (progress!=null){
//                    os.flush();
//                }

                // Was operation cancelled?
//                checkIfCancelled();
            }
            zipOutputStream.closeEntry();
            os.flush();
        } catch(IOException e){
            MiscUtils.closeSilently(bis);
            bis=null;

            throw e;
        } finally {
            MiscUtils.closeSilently(zipOutputStream);
            MiscUtils.closeSilently(bis);
        }
    }
}
