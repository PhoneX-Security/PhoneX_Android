package net.phonex.ft;

import android.content.ContentResolver;
import android.net.Uri;

import net.phonex.db.entity.FileStorage;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.pub.proto.FileTransfer;
import net.phonex.pub.proto.ServerProtoBuff;
import net.phonex.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;

/**
 * Class stores file transfer protocol dependent data.
 *
 * @author ph4r05
 *
 */
public class FTHolder {
    public static final String TAG = "FTHolder";

    public byte[] saltb;
    public byte[] nonceb;
    public KeyPair kp;
    public byte[] c;
    public byte[] salt1;
    public byte[][] ci;
    public byte[] MB;
    public FileTransfer.UploadFileXb XB;
    public byte[] encXB;
    public byte[] macEncXB;
    public byte[] nonce2;

    // Aux data for uploader. Redundant field, to enable easy serialization and upload resumption.
    public byte[] ukeyData;

    // Not required for crypto operation.
    public String nonce1; // base64encoded.

    // Meta and archive files. MetaIdx=0, archiveIdx=1
    public byte fileIv[][];
    public byte fileMac[][];
    public byte fileHash[][];
    public String filePath[]; // Path to the already created file (encrypted).
    public File file[]; // Path to the already created file (encrypted).
    public long fileSize[]; // Just informative for progress bar during sending.

    public byte filePrepRec[][]; //long[], File prepend record containing IV and MAC.

    public long srcFilesTotalSize;
    public long thumbFilesTotalSize;
    public Cipher fileCipher[];
    public String filePackPath[]; // Path to the ZIP files
    public final Set<String> fnames;        // file names for duplicity detection.
    public final List<String> orderedFnames; // ordered list of file names for building notification message.
    public boolean fnameCollisionFound = false;
    public ZipOutputStream thumbZip;
    public ZipOutputStream archZip;
    public String thumbZipPath;

    public Long archiveOffset; // offset in encrypted file at which the archive begins
    public Long metaInArchiveOffset;

    // message id and sender for thumbnails
    public String sender;
    public Long messageId;

    public FTHolder() {
        fnames = new HashSet<>();
        orderedFnames = new LinkedList<>();
        initFileStruct();
        resetFileData();
    }

    public void initFileStruct(){
        fileIv = new byte[2][];
        fileMac = new byte[2][];
        fileHash = new byte[2][];
        filePath = new String[2];
        fileSize = new long[2];
        filePrepRec = new byte[2][];
        fileCipher = new Cipher[2];
        filePackPath = new String[2];
        file = new File[2];
        thumbZip = null;
        archZip = null;
        thumbZipPath = null;
    }

    /**
     * Resets all file related data stored in the holder.
     */
    public void resetFileData() {
        fnameCollisionFound = false;
        thumbFilesTotalSize = 0;
        srcFilesTotalSize = 0;
        thumbZip = null;
        archZip = null;
        thumbZipPath = null;

        for(int i=0; i < DHKeyHelper.HOLDER_NUM_ELEMS; i++){
            fileIv[i] = null;
            fileMac[i] = null;
            fileHash[i] = null;
            filePath[i] = null;
            fileSize[i] = 0;
            filePrepRec[i] = null;
            fileCipher[i] = null;
            filePackPath[i] = null;
            file[i] = null;
        }

        fnames.clear();
        orderedFnames.clear();
    }

    /**
     * Returns encoded public DH key stored in the holder.
     * @return
     */
    public byte[] getGyData() {
        if (kp == null || kp.getPublic() == null){
            Log.w(TAG, "Cannot generate GyData, key pair is null");
            return null;
        }

        return kp.getPublic().getEncoded();
    }

    /**
     * Action in case of file name conflicts in copy operation.
     *
     * @author ph4r05
     */
    public static enum FilenameConflictCopyAction {OVERWRITE, THROW_EXCEPTION, RENAME_NEW}

    /**
     * Holder class for files to be added to the ZIP archive.
     * @author ph4r05
     */
    public static class FileEntry {
        public File file;
        public String filename;
        public String ext;
        public long fileSize;
        public byte[] sha256;
        public boolean doGenerateThumb;
        public FileTransfer.MetaFileDetail.Builder metaB;
        public FileTransfer.MetaFileDetail metaMsg;

        public boolean isSecure;
        public String fileSystemName;

        /**
         * FileStorageUri of temporary file, that should be deleted after successful or failed upload.
         * All values above should work for the new file using old code.
         */
        public FileStorageUri tempUri;
        /**
         * Original values stored for creating local ReceivedFile record. These will not work for upload.
         */
        public File originalFile;
        public String originalFilename;
        public long originalFileSize;
        public byte[] originalSha256;
        public Uri originalUri;

        public FileEntry(String fileUri) {
            Uri uri = Uri.parse(fileUri);
            this.fileSystemName = uri.getQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME);
            this.filename = uri.getQueryParameter(FileStorageUri.STORAGE_FILENAME);
            this.file = new File(uri.getPath(), fileSystemName);
            this.isSecure = FileStorageUri.STORAGE_SCHEME_SECURE.equals(uri.getScheme());
        }

        public InputStream getInputStream(ContentResolver cr) throws FileNotFoundException {
            if (tempUri != null) {
                try {
                    FileStorage read = FileStorage.getFileStorageByUri(tempUri.getUri(), cr);
                    if (read == null) {
                        Log.w(TAG, "No FileStorage for temp file " + tempUri.getFilesystemName());
                        throw new FileNotFoundException();
                    }
                    Log.d(TAG, "FileEntry.getInputStream() " + read.toString());
                    return read.getInputStream();
                } catch (FileStorage.FileStorageException e) {
                    Log.e(TAG, "Secure Storage error " + e);
                    throw new FileNotFoundException();
                }
            }
            if (isSecure) {
                try {
                    FileStorage read = FileStorage.getFileStorageByFileSystemName(fileSystemName, cr);
                    if (read == null) {
                        Log.w(TAG, "No FileStorage for fs name " + fileSystemName);
                        throw new FileNotFoundException();
                    }
                    Log.d(TAG, "FileEntry.getInputStream() " + read.toString());
                    return read.getInputStream();
                } catch (FileStorage.FileStorageException e) {
                    Log.e(TAG, "Secure Storage error " + e);
                    throw new FileNotFoundException();
                }
            } else {
                return new FileInputStream(file);
            }
        }

        public long getSize(ContentResolver cr) {
            Log.d(TAG, "FileEntry.getSize() isSecure: " + isSecure);
            if (tempUri != null) {
                try {
                    FileStorage read = FileStorage.getFileStorageByUri(tempUri.getUri(), cr);
                    if (read == null) {
                        Log.w(TAG, "No FileStorage for temp file " + tempUri.getFilesystemName());
                        return 0;
                    }
                    return read.getFileSize();
                } catch (FileStorage.FileStorageException e) {
                    Log.e(TAG, "Secure Storage error " + e);
                    return 0;
                }
            }
            if (isSecure) {
                try {
                    FileStorage read = FileStorage.getFileStorageByFileSystemName(fileSystemName, cr);
                    if (read == null) {
                        Log.w(TAG, "No FileStorage for fs name " + fileSystemName);
                        return 0;
                    }
                    return read.getFileSize();
                } catch (FileStorage.FileStorageException e) {
                    Log.e(TAG, "Secure Storage error " + e);
                    return 0;
                }
            } else {
                return file.length();
            }
        }

        public Long getChecksum(ContentResolver cr) {
            Log.d(TAG, "FileEntry.getChecksum() isSecure: " + isSecure);
            if (tempUri != null) {
                try {
                    FileStorage read = FileStorage.getFileStorageByUri(tempUri.getUri(), cr);
                    if (read == null) {
                        Log.w(TAG, "No FileStorage for temp file " + tempUri.getFilesystemName());
                        return null;
                    }
                    return read.getCrc32();
                } catch (FileStorage.FileStorageException e) {
                    Log.e(TAG, "Secure Storage error " + e);
                    return null;
                }
            }
            if (isSecure) {
                try {
                    FileStorage read = FileStorage.getFileStorageByFileSystemName(fileSystemName, cr);
                    if (read == null) {
                        Log.w(TAG, "No FileStorage for fs name " + fileSystemName);
                        return null;
                    }
                    return read.getCrc32();
                } catch (FileStorage.FileStorageException e) {
                    Log.e(TAG, "Secure Storage error " + e);
                    return null;
                }
            } else {
                return file != null ? DHKeyHelper.checksumFile(file) : 0;
            }
        }

        public Uri getUri() {
            Uri.Builder uriBuilder = (new Uri.Builder());
            uriBuilder.scheme(isSecure ? FileStorageUri.STORAGE_SCHEME_SECURE : FileStorageUri.STORAGE_SCHEME_NORMAL);
            uriBuilder.encodedPath(file.getParentFile().getAbsolutePath());
            uriBuilder.appendQueryParameter(FileStorageUri.STORAGE_FILENAME, filename);
            uriBuilder.appendQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME, fileSystemName);
            return uriBuilder.build();
        }
    }

    // TODO none of the attributes are ever assigned, eliminate this class?
    public static class FileToSendEntry {
        /**
         * Mandatory part identifying particular file to send.
         */
        public String fileUri;

        /**
         * Preferred file name to be used, if original one is not suitable.
         * If nil, original one is used.
         */
        //public String prefFileName;

        /**
         * Preferred MIME type to be used. Can signalize voice message and other message types.
         * If nil, mime type will be automatically detected from file.
         */
        public String mimeType;

        /**
         * Should file transfer generate thumb for this file if it is able to do it?
         */
        public boolean doGenerateThumbIfPossible;

        /**
         * Datetime to be associated with send file.
         * If nil, original one is taken.
         */
        public Date fileDate;

        /**
         * Title directly associated to this file.
         */
        public String title;

        /**
         * Description associated to this file.
         */
        public String desc;

        public FileToSendEntry(String fileUri) {
            this.fileUri = fileUri;
        }
    }

    /**
     * Options for extracting
     * @author ph4r05
     *
     */
    public static class UnpackingOptions {
        public String destinationDirectory=null;
        public boolean createDirIfMissing=false;
        public FilenameConflictCopyAction actionOnConflict=FilenameConflictCopyAction.THROW_EXCEPTION;
        public boolean deleteArchiveOnSuccess=false;
        public boolean deleteMetaOnSuccess=false;
        public String fnamePrefix;

        /**
         * If some exception happens during extracting files,
         * and this attribute is set to true, all previous files
         * will be removed as well.
         */
        public boolean deleteNewFilesOnException=true;
    }

    public static class UnpackingFile {
        public String originalFname;
        public String destination;
        public String uri;
        public long size;

        public UnpackingFile() {
        }

        public UnpackingFile(String originalFname, String destination) {
            this.originalFname = originalFname;
            this.destination = destination;
            this.uri = null;
            this.size = 0;
        }

        public UnpackingFile(String originalFname, String destination, String uri, long size) {
            this.originalFname = originalFname;
            this.destination = destination;
            this.uri = uri;
            this.size = size;
        }
    }

    /**
     * Result of the files extraction.
     * @author ph4r05
     */
    public static class UnpackingResult {
        private List<UnpackingFile> files;
        private boolean finishedOK = true;
        private IOException ex;

        public List<UnpackingFile> getFiles() {
            if (files==null) files = new LinkedList<UnpackingFile>();
            return files;
        }

        public void setFiles(List<UnpackingFile> files) {
            this.files = files;
        }

        public boolean isFinishedOK() {
            return finishedOK;
        }

        public void setFinishedOK(boolean finishedOK) {
            this.finishedOK = finishedOK;
        }

        public IOException getEx() {
            return ex;
        }

        public void setEx(IOException ex) {
            this.ex = ex;
        }
    }

    /**
     * Holder for the upload response.
     * @author ph4r05
     *
     */
    public static class UploadResult {
        public int code;
        public String message;
        public boolean cancelDetected = false;
        public boolean ioErrorDetected = false;
        public ServerProtoBuff.RESTUploadPost response;
    }

    public static class DownloadResult {
        public int code;
        public int downloaderFinishCode;
        // TODO: @property (nonatomic) PEXFtDownloader * task;
    }

    /**
     * Holder used during download operation to store information about file in archive.
     * Stores information from meta file + thumbnail details.
     */
    public static class DownloadFile {
        // Fields from meta file detail.
        public String  fileName;
        public String  extension;
        public String  mimeType;
        public Long    fileSize;
        public byte[]  xhash;
        public Integer prefOrder;
        public String  thumbNameInZip;
        public String  title;
        public String  desc;
        public Long    fileTimeMilli;

        // Additional fields.
        public String  thumbFname;
        public String  thumbFileStorageUriString;
        public Long    receivedFileId;

        public static DownloadFile buildFromMeta(FileTransfer.MetaFileDetail meta){
            final DownloadFile df = new DownloadFile();
            df.fileName       = meta.hasFileName()  ? meta.getFileName()  : null;
            df.extension      = meta.hasExtension() ? meta.getExtension() : null;
            df.mimeType       = meta.hasMimeType()  ? meta.getMimeType()  : null;
            df.fileSize       = meta.hasFileSize()  ? meta.getFileSize()  : null;
            df.xhash          = meta.hasHash()      ? meta.getHash().toByteArray()  : null;
            df.prefOrder      = meta.hasPrefOrder() ? meta.getPrefOrder() : null;
            df.thumbNameInZip = meta.hasThumbNameInZip() ? meta.getThumbNameInZip() : null;
            df.title          = meta.hasTitle()     ? meta.getTitle()     : null;
            df.desc           = meta.hasDesc()      ? meta.getDesc()      : null;
            df.fileTimeMilli  = meta.hasFileTimeMilli()  ? meta.getFileTimeMilli()  : null;
            return df;
        }
    }

    public static class FtPreUploadFilesHolder {
        public FileTransfer.MetaFile mf;
        public List<FileEntry> files2send;
    }
}
