package net.phonex.db.entity;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import net.phonex.core.Constants;
import net.phonex.ft.storage.FileStorageUri;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.crypto.CryptoHelper;
import net.phonex.util.system.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Keeps information about encrypted files.
 *
 * Files are encrypted and saved under "fsName" (file system name) at path "path" (excluding the file name).
 * "filename" is the user friendly name of the file.
 * "filesize" is size of the cleartext
 *
 * If uris are used, they are formatted according to FileStorageUri
 */
public class FileStorage implements Parcelable {
    public final static String THIS_FILE = "FileStorage";
    public static final String TABLE = "FileStorage";
    /**
     * URI for content provider.<br/>
     */
    public static final Uri URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE);
    /**
     * Base URI for contact content provider.<br/>
     * To append with {@link #FIELD_ID}
     *
     * @see android.content.ContentUris#appendId(android.net.Uri.Builder, long)
     */
    public static final Uri ID_URI_BASE = Uri.parse(ContentResolver.SCHEME_CONTENT + "://"
            + Constants.AUTHORITY + "/" + TABLE + "/");

    public static final String BC = org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    // AES in Galois Counter Mode (Authenticated encryption)
    public static final String AES = "AES/GCM/NoPadding";
    // AES block size, same holds for IV length.
    public static final int AES_BLOCK_SIZE = 16;
    // AES key size - fix to 256b.
    public static final int AES_KEY_SIZE = 32;

    public static final String FIELD_ID = "_id";
    public static final String FIELD_FS_NAME = "fsName";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_FILENAME = "filename";
    public static final String FIELD_FILE_KEY = "fileKey";
    public static final String FIELD_FILE_I_V = "fileIV";
    public static final String FIELD_FILE_SIZE = "fileSize";
    public static final String FIELD_CRC32 = "crc32";
    public static final String FIELD_CLEARTEXT_PATH = "clearPath";

    public static final String[] FULL_PROJECTION = new String[]{
            FIELD_ID, FIELD_FS_NAME, FIELD_PATH, FIELD_FILENAME, FIELD_FILE_KEY, FIELD_FILE_I_V, FIELD_FILE_SIZE, FIELD_CRC32, FIELD_CLEARTEXT_PATH
    };

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "
            + TABLE
            + " ("
            + FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FIELD_FS_NAME + " TEXT, "
            + FIELD_PATH + " TEXT, "
            + FIELD_FILENAME + " TEXT, "
            + FIELD_FILE_KEY + " BLOB, "
            + FIELD_FILE_I_V + " BLOB, "
            + FIELD_FILE_SIZE + " INTEGER DEFAULT 0, "
            + FIELD_CRC32 + " INTEGER DEFAULT 0, "
            + FIELD_CLEARTEXT_PATH + " TEXT "
            + ");";

    public final static long INVALID_ID = -1;
    public final static int FILENAME_LENGTH = 32; // in bytes, is converted to base64 afterwards and stripped of equals signs
    public final static int FILENAME_SUFFIX_LENGTH = 4; // in bytes, used for name conflicts; 4 B ~ 2^32 files with same name
    // <ATTRIBUTES>
    protected Long id;
    protected String fsName;
    protected String path;
    protected String filename;
    protected byte[] fileKey;
    protected byte[] fileIV;
    protected long fileSize;
    protected long crc32;
    protected String cleartextPath; // full path to the decrypted version of this file
    // </ATTRIBUTES>

    protected FileStorage(Cursor c) {
        createFromCursor(c);
    }

    protected FileStorage(String fsName, String path, String filename, byte[] fileKey, byte[] fileIV, String cleartextPath) {
        this.id = null;
        this.fsName = fsName;
        this.filename = filename;
        this.path = path;
        this.fileKey = fileKey;
        this.fileIV = fileIV;
        this.fileSize = 0;
        this.crc32 = 0;
        this.cleartextPath = cleartextPath;
    }

    /**
     * Retrieves FileStorage from DB by filesystem name, if such record exists. Null otherwise.
     *
     * @param fileSystemName name of the file in the filesystem, path independent (must not include path)
     * @param cr             content resolver
     * @return FileSystem ready for reading or null if does not exist
     * @throws FileStorageException if constraints were broken (i.e. fs name is not unique)
     */
    public static FileStorage getFileStorageByFileSystemName(String fileSystemName, ContentResolver cr) throws FileStorageException {
        if (fileSystemName == null) {
            throw new IllegalArgumentException("fileSystemName == null");
        }
        Cursor cursor = getInternalByFSName(fileSystemName, cr);

        if (nullOrEmptyCursor(cursor)) {
            if (cursor != null) cursor.close();
            return null;
        }

        if (cursor.getCount() != 1) {
            cursor.close();
            throw new FileStorageException("Duplicate file system name");
        }

        FileStorage fileStorage = new FileStorage(cursor);
        cursor.close();
        return fileStorage;
    }

    /**
     * Retrieves FileStorage from DB by path and file name, if such record exists. Null otherwise.
     *
     * @param path     path to the file
     * @param filename name of the file as given to newFileStorage
     * @param cr       content resolver for DB access
     * @return FileSystem ready for reading or null if does not exist
     * @throws FileStorageException if path is invalid or file does not exist in DB
     */
    public static FileStorage getFileStorageByPathAndName(String path, String filename, ContentResolver cr) throws FileStorageException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        if (filename == null) {
            throw new IllegalArgumentException("filename == null");
        }
        path = canonicalizePath(path);
        Cursor cursor = getInternal(path, filename, cr);

        if (nullOrEmptyCursor(cursor)) {
            if (cursor != null) cursor.close();
            //throw new FileStorageException("File not found");
            return null;
        }

        FileStorage fileStorage = new FileStorage(cursor);
        cursor.close();
        return fileStorage;
    }

    /**
     * Retrieves FileStorage from DB by uri, if such record exists. Null otherwise.
     *
     * @param uri URI for the file in format defined by FileStorageUri
     * @param cr content resolver
     * @return FileSystem ready for reading or null if does not exist
     * @throws FileStorageException if path is invalid or file does not exist in DB
     */
    public static FileStorage getFileStorageByUri(Uri uri, ContentResolver cr) throws FileStorageException {
        if (!FileStorageUri.STORAGE_SCHEME_SECURE.equals(uri.getScheme())) {
            return null;
        }
        return getFileStorageByFileSystemName(uri.getQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME), cr);
    }

    protected static boolean exists(String path, String filename, ContentResolver cr) throws FileStorageException {
        path = canonicalizePath(path);
        Cursor cursor = getInternal(path, filename, cr);

        if (nullOrEmptyCursor(cursor)) {
            if (cursor != null) cursor.close();
            return false;
        }
        cursor.close();
        return true;
    }

    public String getCleartextPath() {
        return cleartextPath;
    }

    /**
     * Returns list of FileStorage objects for a given path.
     *
     * @param path the path
     * @param cr   content resolver for DB access
     * @return List of FileStorages at the given path, empty list if none
     * @throws FileStorageException
     */
    public static List<FileStorage> getFileStoragesByFolderPath(String path, ContentResolver cr) throws FileStorageException {
        path = canonicalizePath(path);
        List<FileStorage> storages = new ArrayList<>();
        Cursor cursor = getInternalByPath(path, cr);
        while (cursor.moveToNext()) {
            storages.add(new FileStorage(cursor));
        }
        cursor.close();
        return storages;
    }

    /**
     * Query by path and filename.
     */
    private static Cursor getInternal(String path, String filename, ContentResolver cr) {
        String where = String.format("%s=? AND %s=?", FIELD_PATH, FIELD_FILENAME);
        String[] args = new String[]{String.format("%s", path), String.format("%s", filename)};

        return cr.query(URI, FULL_PROJECTION, where, args, null);
    }

    /**
     * Query by fs name.
     */
    private static Cursor getInternalByFSName(String filesystemName, ContentResolver cr) {
        String where = String.format("%s=?", FIELD_FS_NAME);
        String[] args = new String[]{String.format("%s", filesystemName)};

        return cr.query(URI, FULL_PROJECTION, where, args, null);
    }

    /**
     * Query by path.
     */
    private static Cursor getInternalByPath(String path, ContentResolver cr) {
        String where = String.format("%s=?", FIELD_PATH);
        String[] args = new String[]{String.format("%s", path)};

        return cr.query(URI, FULL_PROJECTION, where, args, null);
    }

    /**
     * Test if cursor is null or empty. Moves to first row of the cursor if not empty.
     *
     * @param cursor cursor to be tested
     * @return true if null or empty, false otherwise
     */
    private static boolean nullOrEmptyCursor(Cursor cursor) {
        return cursor == null || !cursor.moveToFirst();
    }

    /**
     * All paths must be in the same format, otherwise query results are undefined!
     *
     * @throws FileStorageException if path is not a directory
     */
    private static String canonicalizePath(String path) throws FileStorageException {
        File file = new File(path);
        if (!file.isDirectory()) {
            throw new FileStorageException("path is not a directory");
        }
        return file.getAbsolutePath();
    }

    /**
     * Creates new record in DB for file on "path" with name "filename".
     *
     * TOC vs TOU
     * we want to make a record in the DB when new file is created,
     * otherwise there could be multiple objects with the same path+filename writing to the same file
     *
     * @param path     Path where to save the file. The path is not checked for existence!
     * @param filename Original name of the file
     * @param cr       content resolver
     * @return FileStorage object for the file, ready for writing
     * @throws FileStorageException for badly formatted path or if file already exists in DB
     */
    public static FileStorage newFileStorage(String path, String filename, ContentResolver cr) throws FileStorageException {
        path = canonicalizePath(path);
        // check in db, if filename is already used
        Cursor cursor = getInternal(path, filename, cr);
        if (!nullOrEmptyCursor(cursor)) {
            // already exists
            cursor.close();
            throw new FileStorageException("Path+filename already exists in DB");
        }
        cursor.close();

        SecureRandom rand = new SecureRandom();

        byte fsName[] = new byte[FILENAME_LENGTH];
        byte iv[] = new byte[AES_BLOCK_SIZE];
        rand.nextBytes(iv);

        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) { //| NoSuchProviderException e) {
            throw new FileStorageException(e);
        }
        keyGen.init(AES_KEY_SIZE * 8);
        SecretKey secretKey = keyGen.generateKey();
        byte key[] = secretKey.getEncoded();

        String fileSystemName = null;

        boolean exists = true;

        while (exists) {
            rand.nextBytes(fsName);
            fileSystemName = getFilenameFromBytes(fsName);
            // test if this FS name already exists in DB
            cursor = getInternalByFSName(fileSystemName, cr);
            exists = !nullOrEmptyCursor(cursor);
            if (!exists) {
                // test if exists in FS
                File file = new File(path + File.separator + fileSystemName);
                exists = file.exists();
            }
            if (cursor != null) cursor.close();
        }

        FileStorage result = new FileStorage(fileSystemName, path, filename, key, iv, null);
        // insert into db
        Uri uri = cr.insert(URI, result.getDbContentValues());
        result.id = ContentUris.parseId(uri);

        return result;
    }

    /**
     * Creates new record in DB for file on "path" with name "filename".
     *
     * TOC vs TOU
     * we want to make a record in the DB when new file is created,
     * otherwise there could be multiple objects with the same path+filename writing to the same file
     *
     * This will try to save under original name and then assign random suffix to name if there is a name conflict.
     *
     * @param path     Path where to save the file. The path is checked for existence and name conflict is resolved.
     * @param filename Original name of the file, MUST be URL safe
     * @param cr       content resolver
     * @return FileStorage object for the file, ready for writing
     * @throws FileStorageException for badly formatted path or if file already exists in DB
     */
    public synchronized static FileStorage newFileStorageResolveNameConflicts(String path, String filename, ContentResolver cr) throws FileStorageException {
        SecureRandom rand = new SecureRandom();
        byte randomSuffix[] = new byte[FILENAME_SUFFIX_LENGTH];
        boolean exists = true;
        boolean first = true;

        String newName = filename;

        while (exists) {
            String extension = FilenameUtils.getExtension(filename);
            if (first) {
                first = false;
            } else {
                rand.nextBytes(randomSuffix);
                String suffix = getFilenameFromBytes(randomSuffix);
                newName = FilenameUtils.removeExtension(filename) + "_" + suffix + "." + extension;
            }
            exists = exists(path, newName, cr);
        }

        return newFileStorage(path, newName, cr);
    }

    /**
     * Get the randomly generated name under which this file is saved in the file system.
     * File with this name is saved to getPath() path.
     *
     * @return Name of this file in the file system.
     */
    public String getFileSystemName() {
        return fsName;
    }

    /**
     * Path to parent of this file.
     *
     * @return Path to parent of this file.
     */
    public String getPath() {
        return path;
    }

    /**
     * Path to file on the file system.
     *
     * @return Path to file on the file system.
     */
    public String getFilePath() {
        return new File (path, fsName).getAbsolutePath();
    }

    /**
     * Original name of this file.
     *
     * @return Original name of this file.
     */
    public String getFilename() {
        return filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getCrc32() {
        return crc32;
    }

    public boolean cloneExists() {
        if (cleartextPath == null) return false;
        File clone = new File(cleartextPath);
        return clone.exists();
    }

    public String getCleartextPathFixInconsistency(ContentResolver cr) {
        if (cleartextPath != null && !cloneExists()) {
            cleartextPath = null;
            updateById(cr);
            return null;
        }
        return cleartextPath;
    }

    private Cipher getInitializedCipher(int mode) throws FileStorageException {
        Cipher aes;
        SecretKey aesKey = new SecretKeySpec(fileKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(fileIV);
        try {
            aes = Cipher.getInstance(AES); // when specifying provider,
            aes.init(mode, aesKey, ivSpec);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException |
                InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw new FileStorageException("Cipher initialization failed", e);
        }
        return aes;
    }

    /**
     * Create an input stream for reading from this file.
     * The stream's close() method will throw an IOException on data authenticity check fail.
     *
     * @return Input stream ready for reading from this file.
     * @throws FileNotFoundException if the path or file in the FS does not exist. Create the file first by writing to result of getOutputStream()
     * @throws FileStorageException  for internal error with Cipher. implies that DB record may be invalid.
     */
    public InputStream getInputStream() throws FileNotFoundException, FileStorageException {
        File file = new File(path + File.separator + fsName);
        if (!file.exists()) {
            throw new FileNotFoundException("File with corresponding fsName does not exist on given path.");
        }

        FileInputStream fis = new FileInputStream(file);

        Cipher aes = getInitializedCipher(Cipher.DECRYPT_MODE);

        return new CryptoHelper.NotClosingInputStream(new CipherInputStream(new BufferedInputStream(fis), aes));
    }

    /**
     * Create an output stream for writing to this file.
     * Identical to getOutputStream(false) (FileStorageException will be raised if file already exists)
     * FileStorageException is raised when overwriting
     *
     * @return output for writing
     * @throws FileNotFoundException for incorrect path / fs name
     * @throws FileStorageException  if file already exists and should not be overwritten or for error with Cipher
     */
    public OutputStream getOutputStream(ContentResolver cr) throws FileNotFoundException, FileStorageException {
        return getOutputStream(cr, false);
    }

    /**
     * Create an output stream for writing to this file.
     *
     * @param overwriteIfExists true if existing file should be overwritten,
     *                          false if FileStorageException should be raised when overwriting
     * @return OutputFor writing.
     * @throws FileNotFoundException for incorrect path / fs name
     * @throws FileStorageException  if file already exists and should not be overwritten or for error with Cipher
     */
    public OutputStream getOutputStream(ContentResolver cr, boolean overwriteIfExists) throws FileNotFoundException, FileStorageException {
        File file = new File(path + File.separator + fsName);

        if (!overwriteIfExists && file.exists()) {
            // do not overwrite, if exists
            throw new FileStorageException("File already exists");
        }

        FileOutputStream fos = new FileOutputStream(file);

        Cipher aes = getInitializedCipher(Cipher.ENCRYPT_MODE);

        return new FileStorageOutputStream(new CipherOutputStream(fos, aes), this, cr);
    }

    /**
     * Generates a file name for cleartext copy of this file and saves it to DB.
     *
     * File existence is checked and name conflict resolved, however file is not created.
     *
     * @param parentDirectory where to put the new file
     * @param preferredName preferred name (or null if field filename should be used)
     * @param cr content resolver
     * @return
     * @throws IOException
     * @throws FileStorageException
     */
    public String createCleartextClone(File parentDirectory, String preferredName, ContentResolver cr) throws IOException, FileStorageException {
        if (parentDirectory == null) {
            throw new IllegalArgumentException("parentDirectory == null");
        }
        if (!parentDirectory.isDirectory()) {
            throw new IOException("parentDirectory not a directory");
        }
        if (!parentDirectory.canWrite()) {
            throw new IOException("cannot write to parentDirectory");
        }
        if (preferredName == null || preferredName.isEmpty()) {
            preferredName = filename;
        }
        if (preferredName.contains(File.separator) || preferredName.contains(File.pathSeparator)) {
            throw new IllegalArgumentException("name contains separators");
        }
        if (cleartextPath != null) {
            // can only have one clone
            deleteCleartextClone(cr);
        }
        File preferred = new File(parentDirectory, preferredName);
        if (preferred.exists()) {
            // this is some other file, because we deleted the old file, must not overwrite
            String name = FilenameUtils.getBaseName(preferredName);
            while (name.length() < 3) {
                name.concat("_");
            }
            String extension = FilenameUtils.getExtension(preferredName);
            if (extension != null && !extension.isEmpty()) {
                extension = ".".concat(extension);
            }
            preferred = File.createTempFile(name, extension, parentDirectory);
        }

        cleartextPath = preferred.getAbsolutePath();
        if (!updateById(cr)) {
            throw new FileStorageException("Could not update the record");
        }

        // TODO work on this was postponed, see PHON-558
        // File.deleteOnExit()?
        // delete file after some time?
        // delete file on activity result?
        return cleartextPath;
    }

    /**
     * Deletes file pointed by the cleartextPath field and deletes the record of having a cleartext copy from DB.
     * @param cr content resolver
     * @return ture if cleartext copy was deleted
     */
    public boolean deleteCleartextClone(ContentResolver cr) {
        File clone = new File(cleartextPath);

        boolean result = clone.exists() && clone.delete();
        if (result) {
            Log.df(THIS_FILE, "Deleted clone %s", cleartextPath);
        }
        cleartextPath = null;
        // order is important, still want to delete the path in DB
        result = updateById(cr) && result;
        if (result) {
            Log.df(THIS_FILE, "Updated record after clone deletion");
        }
        return result;
    }

    @Override
    public String toString() {
        return "FileStorage{" +
                "id=" + id +
                ", fsName='" + fsName + '\'' +
                ", path='" + path + '\'' +
                ", filename='" + filename + '\'' +
                ", fileSize=" + fileSize +
                ", crc32=" + crc32 +
                ", clearTextPath='" + cleartextPath + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileStorage that = (FileStorage) o;

        if (!fsName.equals(that.fsName)) return false;
        if (!path.equals(that.path)) return false;
        return filename.equals(that.filename);

    }

    @Override
    public int hashCode() {
        int result = fsName.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + filename.hashCode();
        return result;
    }

    private int deleteInternal(ContentResolver cr, String fsName) {
        String where = String.format("%s=?", FIELD_FS_NAME);
        String[] args = new String[]{String.format("%s", fsName)};
        return cr.delete(URI, where, args);
    }

    private boolean deleteFromFS() {
        File file = new File(path + File.separator + fsName);
        return file.exists() && file.delete();
    }

    /**
     * Delete from DB, to get rid of tombstones in messages.
     * This depends on correctly formed URIs, since DB will do exact match on URI.
     * This would be easier, if ReceivedFile had separate field for filesystem name.
     * @return
     */
    private int deleteReceivedFileRecords(ContentResolver cr) {
        String where = String.format("%s=?", ReceivedFile.FIELD_STORAGE_URI);
        String[] args = new String[]{String.format("%s", getUri())};
        return cr.delete(ReceivedFile.URI, where, args);
    }

    protected boolean updateById(ContentResolver cr) {
        String where = String.format("%s=?", FIELD_ID);
        String[] args = new String[]{String.format("%s", id)};

        int updated = cr.update(URI, getDbContentValues(), where, args);
        if (updated < 0 || updated > 1) {
            // incorrect number of rows updated, exception?
        }
        return updated == 1;
    }

    /**
     * Delete this file from DB and from FS.
     */
    public boolean delete(ContentResolver cr, boolean deleteCloneIfExists) {
        boolean success = true;

        // TODO work on this was postponed, see PHON-558
        // clones should be deleted under some circumstances, but avoid doing so now
        deleteCloneIfExists = false;

        if (deleteCloneIfExists && cloneExists()) {
            success &= deleteCleartextClone(cr);
        }
        if (deleteFromFS()) {
            Log.df(THIS_FILE, "delete() Deleted from file system %s", fsName);
        } else {
            success = false;
            Log.wf(THIS_FILE, "delete() File was not deleted from file system %s", fsName);
        }

        // delete from DB
        int thumbs = Thumbnail.deleteByUri(cr, this.getUri().toString());
        if (0 == thumbs) {
            Log.df(THIS_FILE, "delete() No thumbs deleted");
        } else {
            Log.df(THIS_FILE, "delete() Deleted %d thumbs", thumbs);
        }

        int deletedReceived = deleteReceivedFileRecords(cr);
        Log.df(THIS_FILE, "Deleted [%d] records from ReceivedFile where uri [%s]", deletedReceived, getUri());

        if (1 == deleteInternal(cr, this.fsName)) {
            Log.df(THIS_FILE, "delete() Deleted from db %s - %s", filename, fsName);
        } else {
            success = false;
            Log.wf(THIS_FILE, "delete() Delete from db %s - %s not successful", filename, fsName);
        }
        return success;
    }

    private void createFromCursor(Cursor c) {
        int colCount = c.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            final String colname = c.getColumnName(i);
            if (FIELD_ID.equals(colname)) {
                this.id = c.getLong(i);
            } else if (FIELD_FS_NAME.equals(colname)) {
                this.fsName = c.getString(i);
            } else if (FIELD_PATH.equals(colname)) {
                this.path = c.getString(i);
            } else if (FIELD_FILENAME.equals(colname)) {
                this.filename = c.getString(i);
            } else if (FIELD_FILE_KEY.equals(colname)) {
                this.fileKey = c.getBlob(i);
            } else if (FIELD_FILE_I_V.equals(colname)) {
                this.fileIV = c.getBlob(i);
            } else if (FIELD_FILE_SIZE.equals(colname)) {
                this.fileSize = c.getLong(i);
            } else if (FIELD_CRC32.equals(colname)) {
                this.crc32 = c.getLong(i);
            } else if (FIELD_CLEARTEXT_PATH.equals(colname)) {
                this.cleartextPath = c.getString(i);
            } else {
                Log.w(THIS_FILE, "Unknown column name: " + colname);
            }
        }
    }

    public ContentValues getDbContentValues() {
        ContentValues args = new ContentValues();
        if (this.id != null)
            args.put(FIELD_ID, id);
        if (this.fsName != null)
            args.put(FIELD_FS_NAME, fsName);
        if (this.path != null)
            args.put(FIELD_PATH, path);
        if (this.filename != null)
            args.put(FIELD_FILENAME, filename);
        if (this.fileKey != null)
            args.put(FIELD_FILE_KEY, fileKey);
        if (this.fileIV != null)
            args.put(FIELD_FILE_I_V, fileIV);
        args.put(FIELD_FILE_SIZE, fileSize);
        args.put(FIELD_CRC32, crc32);
        if (this.cleartextPath != null)
            args.put(FIELD_CLEARTEXT_PATH, cleartextPath);
        return args;
    }

    public static class FileStorageException extends Exception {
        public FileStorageException() {
            super();
        }

        public FileStorageException(String detailMessage) {
            super(detailMessage);
        }

        public FileStorageException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public FileStorageException(Throwable throwable) {
            super(throwable);
        }
    }

    private class FileStorageOutputStream extends FilterOutputStream {
        long size;
        CRC32 crc;
        FileStorage fileStorage;
        ContentResolver cr;

        public FileStorageOutputStream(OutputStream out, FileStorage fileStorage, ContentResolver cr) {
            super(out);
            crc = new CRC32();
            size = 0;
            this.fileStorage = fileStorage;
            this.cr = cr;
        }

        @Override
        public void close() throws IOException {
            super.close();
            fileStorage.crc32 = crc.getValue();
            fileStorage.fileSize = size;

            Log.d(THIS_FILE, "Updating " + fileStorage.toString() + " size: " + size);
            fileStorage.updateById(cr);
        }

        @Override
        public void write(@NonNull byte[] buffer) throws IOException {
            out.write(buffer);
            crc.update(buffer, 0, buffer.length);
            size += buffer.length;
        }

        @Override
        public void write(@NonNull byte[] buffer, int offset, int length) throws IOException {
            out.write(buffer, offset, length);
            crc.update(buffer, offset, length);
            size += length;
        }

        @Override
        public void write(int oneByte) throws IOException {
            out.write(oneByte);
            crc.update(oneByte);
            size++;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.id);
        dest.writeString(this.fsName);
        dest.writeString(this.path);
        dest.writeString(this.filename);
        dest.writeByteArray(this.fileKey);
        dest.writeByteArray(this.fileIV);
        dest.writeLong(this.fileSize);
        dest.writeLong(this.crc32);
        dest.writeString(this.cleartextPath);
    }

    private FileStorage(Parcel in) {
        this.id = in.readLong();
        this.fsName = in.readString();
        this.path = in.readString();
        this.filename = in.readString();
        this.fileKey = in.createByteArray();
        this.fileIV = in.createByteArray();
        this.fileSize = in.readLong();
        this.crc32 = in.readLong();
        this.cleartextPath = in.readString();
    }

    public static final Creator<FileStorage> CREATOR = new Creator<FileStorage>() {
        public FileStorage createFromParcel(Parcel source) {
            return new FileStorage(source);
        }

        public FileStorage[] newArray(int size) {
            return new FileStorage[size];
        }
    };

    /**
     * Converts base64 string to a file name (removes / and +)
     * Substitution:
     * / --> _
     * + --> -
     *
     * @param based
     * @return
     */
    public static String getFilenameFromBytes(byte[] based) {
        return Base64.encodeBytes(based).replace("/", "_").replace("+", "-").replace("=", "");
    }

    public Uri getUri() {
        Uri.Builder uriBuilder = (new Uri.Builder());
        uriBuilder.scheme(FileStorageUri.STORAGE_SCHEME_SECURE);
        uriBuilder.encodedPath(path);
        uriBuilder.appendQueryParameter(FileStorageUri.STORAGE_FILENAME, filename);
        uriBuilder.appendQueryParameter(FileStorageUri.STORAGE_FILESYSTEM_NAME, fsName);
        return uriBuilder.build();
    }
}
