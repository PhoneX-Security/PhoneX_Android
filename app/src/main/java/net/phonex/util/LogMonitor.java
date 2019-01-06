package net.phonex.util;

import android.content.Context;

import net.phonex.PhonexSettings;
import net.phonex.pref.PreferencesManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPOutputStream;

/**
 * Logcat help:
 * <p/>
 * Usage: logcat [options] [filterspecs]
 * options include:
 * -s              Set default filter to silent.
 * Like specifying filterspec '*:s'
 * -f <filename>   Log to file. Default to stdout
 * -r [<kbytes>]   Rotate log every kbytes. (16 if unspecified). Requires -f
 * -n <count>      Sets max number of rotated logs to <count>, default 4
 * -v <format>     Sets the log print format, where <format> is one of:
 * <p/>
 * brief process tag thread raw time threadtime long
 * <p/>
 * -c              clear (flush) the entire log and exit
 * -d              dump the log and then exit (don't block)
 * -t <count>      print only the most recent <count> lines (implies -d)
 * -g              get the size of the log's ring buffer and exit
 * -b <buffer>     Request alternate ring buffer, 'main', 'system', 'radio'
 * or 'events'. Multiple -b parameters are allowed and the
 * results are interleaved. The default is -b main -b system.
 * -B              output the log in binary
 * filterspecs are a series of
 * <tag>[:priority]
 * <p/>
 * where <tag> is a log component tag (or * for all) and priority is:
 * V    Verbose
 * D    Debug
 * I    Info
 * W    Warn
 * E    Error
 * F    Fatal
 * S    Silent (supress all output)
 * <p/>
 * '*' means '*:d' and <tag> by itself means <tag>:v
 * <p/>
 * If not specified on the commandline, filterspec is set from ANDROID_LOG_TAGS.
 * If no filterspec is found, filter defaults to '*:I'
 * <p/>
 * If not specified with -v, format is set from ANDROID_PRINTF_LOG
 * or defaults to "brief"
 *
 * @author ph4r05
 */
public class LogMonitor extends Thread {

    private static final String TAG = "LogMonitor";
    private static final int LINE_DUMP_LIMIT = 1000;
    private static final int LOG_CHAR_LIMIT = 70*1024*1024; //70MB - ~73 millions characters, zipped size is about 1/4, let's assume 18.5 MB
    private static final long TIMEOUT_DUMP = 1000 * 60 * 5;
    private final Object lockObject = new Object();
    private final Object outputStreamLock = new Object();
    private final String separator;
    private Process process;
    private BufferedReader reader;
    private Semaphore shutdownSemaphore;
    private volatile boolean running = false;
    private File outFile = null;
    private Context ctxt;

    private OutputStream gstream;
    private StreamGobbler errStream;

    public LogMonitor() {
        separator = System.getProperty("line.separator");
    }

    public void setContext(Context ctxt) {
        this.ctxt = ctxt;
    }

    public static File getLatestLog(Context context){
        File dir = PreferencesManager.getLogsFolder(context);
        File logFile = null;
        if (dir != null) {
            // last modified file should be the newest log
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                return null;
            }

            File lastModifiedFile = files[0];
            for (int i = 1; i < files.length; i++) {
                if (lastModifiedFile.lastModified() < files[i].lastModified()) {
                    lastModifiedFile = files[i];
                }
            }
            logFile = lastModifiedFile;
        }
        return logFile;
    }

    public void run() {
        StringBuilder log = null;

        gstream = null;
        errStream = null;
        running = true;
        shutdownSemaphore = new Semaphore(0);

        //synchronized (lockObject) {
        FileOutputStream outputStream = null;
        try {
            outputStream = openNewLogFile();

            // Get device & release info @first
            log = new StringBuilder(65536);
            logIntro(log);

            // Read std & err out from logcat
            String line;
            int lineCounter = 0;
            long charCounter = 0;
            long lastLogDump = System.currentTimeMillis();
            while ((line = reader.readLine()) != null && running) {
                lineCounter += 1;
                log.append(line);
                log.append(separator);

                // do the file line check later only for every 64th line
                // very effective, saves instructions and CPU time :)
                if ((lineCounter & 0x3f) != 0) {
                    continue;
                }

                // flush to file if too long
                final long curTime = System.currentTimeMillis();
                if (lineCounter >= LINE_DUMP_LIMIT || (curTime - lastLogDump) > TIMEOUT_DUMP) {
                    final String toWrite = log.toString();
                    synchronized (outputStreamLock) {
                        charCounter += toWrite.length();
                        gstream.write(toWrite.getBytes());
                        gstream.flush();
                    }

                    // Flush StringBuilder - reuse it so no further allocation is needed.
                    Log.d(TAG, "DumpToStream, size=" + toWrite.length() + "B");
                    log.delete(0, log.length());
                    lineCounter = 0;
                    lastLogDump = curTime;

//                  Rotate file by the size
                    if (charCounter > LOG_CHAR_LIMIT){
                        synchronized (outputStreamLock){
                            Log.vf(TAG, "rotating file, char limit (%d) reached=%d", LOG_CHAR_LIMIT, charCounter);
                            closeStream(outputStream);
                            outputStream = openNewLogFile();
                            charCounter = 0;
                        }
                    }
                }

            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in log collection", e);
        } finally {
            // Close file output stream
            if (outputStream != null) {
                try {
                    gstream.write(log.toString().getBytes());
                    gstream.close();
                    Log.d(TAG, "Logging to file was stopped now");
                } catch (IOException e) {
                    Log.e(TAG, "Cannot close log file", e);
                }
            }

            // Handle condition where the process ends before the threads finish
            try {
                errStream.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Exception during joining error stream reader", e);
            }
        }

        shutdownSemaphore.release();
        Log.i(TAG, "Shutdown semaphore released");
        //}
    }

    /**
     * Performs log file rotation. Closes current one and opens a new one.
     */
    private void closeStream(FileOutputStream outputStream) {
        if (outputStream != null) {
            try {
                gstream.close();
                Log.d(TAG, "Logging to file was stopped now");

            } catch (IOException e) {
                Log.e(TAG, "Cannot close log file", e);
            }
        }
    }

    private FileOutputStream openNewLogFile() throws IOException {
        outFile = PreferencesManager.getLogsFile(ctxt, false, true);
        Log.df(TAG, "Going to start logging by starting logcat process, logfile=%s", outFile.getAbsolutePath());

        //ProcessBuilder pb = new ProcessBuilder("logcat -v time *:V");
        //pb			 = pb.redirectErrorStream(true);
        //pb.directory(outFile.getParentFile().getAbsoluteFile());
        //process		 = pb.start();		// Runtime.getRuntime().exec("logcat -v time *:V");


        process = Runtime.getRuntime().exec("logcat -v time *:V");
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // store logs compressed
        FileOutputStream outputStream = new FileOutputStream(outFile, true);
        gstream = new GZIPOutputStream(outputStream);

        // error stream
        errStream = new StreamGobbler(process.getErrorStream(), gstream);
        errStream.start();
        return outputStream;
    }

    /**
     * Writes device information to the log file.
     *
     * @param log
     */
    private void logIntro(StringBuilder log) {
        log.append(PhonexSettings.getApplicationDesc(ctxt));
        log.append(separator);
        log.append(MiscUtils.getDeviceInfo());
        log.append(separator);
        log.append(PhonexSettings.getApplicationDesc(ctxt));
    }

    /**
     * Stops this thread from running.
     */
    public void stopLogging() {
        if (!running) {
            return;
        }

        Log.d(TAG, "Going to stop logging");
        running = false;

        try {
            if (process != null) {
                process.destroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in stopping logcat", e);
        }
    }

    public Semaphore getShutdownSemaphore() {
        return shutdownSemaphore;
    }

    public Process getProcess() {
        return this.process;
    }

    public boolean isRunning() {
        return running;
    }

    private class StreamGobbler extends Thread {
        private final InputStream is;
        private final OutputStream os;

        StreamGobbler(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
        }

        public void run() {
            try {
                int bytesRead = 0;
                byte buff[] = new byte[512];
                while (running) {
                    bytesRead = is.read(buff);
                    if (bytesRead == -1) {
                        break;
                    }

                    if (os != null) {
                        synchronized (outputStreamLock) {
                            os.write(buff, 0, bytesRead);
                        }
                    }
                }
            } catch (IOException x) {
                ;
            }
        }
    }
}
