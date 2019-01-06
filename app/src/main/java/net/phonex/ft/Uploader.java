package net.phonex.ft;

import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.ft.misc.TransmitProgress;
import net.phonex.pub.proto.ServerProtoBuff;
import net.phonex.soap.SSLSOAP;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.SecureRandom;

import javax.net.ssl.HttpsURLConnection;

/**
 * Class for HTTPS POST upload of a FTholder (implements file upload protocol).
 *
 * @author ph4r05
 *
 */
public class Uploader extends GenericUploader{
    private DHKeyHelper dhKeyHelper;
    private long totalTxmitSize;
    private long uploadedSize = 0;

    private FTHolder holder;
    private String user;

    public Uploader(DHKeyHelper dhKeyHelper, String targetUrl, FTHolder holder, String user) {
        super(targetUrl);
        this.holder = holder;
        this.user = user;
        this.dhKeyHelper = dhKeyHelper;
    }

    @Override
    protected void uploadFileInternals(DataOutputStream outputStream) throws IOException {
        //        this.outputStream = outputStream;

        // Was operation cancelled?
        dhKeyHelper.checkIfCancelled();
        outputStream.writeBytes(twoHyphens + boundary + lineEnd);

        // Dumping basic parameters
        final String nonce2 = Base64.encodeBytes(holder.nonce2);

        // Load file sizes in order to initialize progress bar.
        totalTxmitSize = holder.fileSize[DHKeyHelper.META_IDX] + holder.fileSize[DHKeyHelper.ARCH_IDX];
        uploadedSize = 0;

        // Was operation cancelled?
        dhKeyHelper.checkIfCancelled();

        // Write request
        writeString(DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_VERSION], "1", false);
        writeString(DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_NONCE2], nonce2, false);
        writeString(DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_USER],   user, false);
        writeString(DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_DHPUB],  Base64.encodeBytes(holder.ukeyData), false);
        writeString(DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_HASHMETA], Base64.encodeBytes(holder.fileHash[DHKeyHelper.META_IDX]), false);
        writeString(DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_HASHPACK], Base64.encodeBytes(holder.fileHash[DHKeyHelper.ARCH_IDX]), false);

        // Dump after only text was written (displayable)
        if (debug){
            Log.vf(TAG, "Server dump1: %s", cos.dump());
        }

        // Was operation cancelled?
        dhKeyHelper.checkIfCancelled();

        // Dump binary data (files) to the stream.
        writeFile(DHKeyHelper.META_IDX, DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_METAFILE], DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_METAFILE], false);
        writeFile(DHKeyHelper.ARCH_IDX, DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_PACKFILE], DHKeyHelper.FT_UPLOAD_PARAMS[DHKeyHelper.FT_UPLOAD_PACKFILE], true);
        Log.v(TAG, "All fields uploaded");
    }

    /**
     * Writes application/octet-stream file as multipart/form-data.
     *
     * @param fileIdx
     * @param name
     * @param filename
     * @param lastOne
     * @throws IOException
     */
    private void writeFile(int fileIdx, String name, String filename, boolean lastOne) throws IOException{
        if (fileIdx<0 || fileIdx>=2 || holder.filePath[fileIdx] == null){
            throw new IllegalArgumentException("Invalid file index / null path");
        }

        // File existence check.
        final File file = new File(holder.filePath[fileIdx]);
        if (!file.exists()){
            throw new IOException("Specified file does not exist, cannot continue.");
        }

        final long curFileLen = file.length();

        outputStream.writeBytes(String.format(fileHeader, name, filename));
        outputStream.writeBytes("Content-Type: application/octet-stream" + lineEnd);
        outputStream.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);
        outputStream.writeBytes(lineEnd);

        // Files are written in a special way (IV, MAC prepended).
        // Provide own progress monitor to the writeFileToStream to compute partial and overall progress.
        // Both files are included in the total progress.
        Log.vf(TAG, "Going to upload fileIdx=%s", fileIdx);
        dhKeyHelper.writeFileToStream(holder, outputStream, fileIdx, new TransmitProgress() {
            @Override
            public void updateTxProgress(Double partial, double total) {
                DHKeyHelper.updateProgress(progress, total, (curFileLen * total + (double) uploadedSize) / (double) totalTxmitSize);
            }
        });

        outputStream.writeBytes(lineEnd);
        outputStream.writeBytes(twoHyphens + boundary + (lastOne ? twoHyphens : "") + lineEnd);

        // For progress bar.
        uploadedSize += holder.fileSize[fileIdx];
        Log.vf(TAG, "Upload of fileIdx=%s done, uploaded=%s; total=%s", fileIdx, uploadedSize, totalTxmitSize);
    }
}
