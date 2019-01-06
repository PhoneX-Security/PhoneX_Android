package net.phonex.service;

import android.widget.Toast;

import net.phonex.PhonexSettings;
import net.phonex.core.SipUri;
import net.phonex.db.entity.SipProfile;
import net.phonex.ft.LogFileUploader;
import net.phonex.pub.proto.ServerProtoBuff;
import net.phonex.soap.RunnableWithException;
import net.phonex.soap.SOAPHelper;
import net.phonex.soap.SSLSOAP;
import net.phonex.soap.ServiceConstants;
import net.phonex.soap.TaskWithCallback;
import net.phonex.util.Log;
import net.phonex.util.LogMonitor;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Manager responsible for sending log file to soap server at REST URL :)
 * Created by miroc on 30.8.15.
 */
public class LogSendingManager {
    private static final String TAG = "LogSendingManager";
    public static final String REST_LOG_UPLOAD_URI = "/rest/rest/logupload";
    private final XService xService;
    private ExecutorService executor;

    // paring request fetch
    protected Future<?> taskFuture;

    private int lastResponseCode;
    private String lastResponseMsg;
    private ServerProtoBuff.RESTUploadPost lastResponse;

    public LogSendingManager(XService xService) {
        this.xService = xService;
        executor = Executors.newSingleThreadExecutor();
    }

    public void triggerLogUpload(String userMessage){
        if (taskFuture != null && !taskFuture.isDone()){
            Log.wf(TAG, "triggerLogUpload; logs are being uploaded, not uploading again");
            return;
        }

        SipUri.ParsedSipUriInfos sipUriInfo = SipUri.parseSipUri(SipProfile.getCurrentProfile(xService).getSip(true));
        String domain = sipUriInfo.domain;
        String url = ServiceConstants.getDefaultRESTURL(domain, xService) + REST_LOG_UPLOAD_URI;
        String appVersion = PhonexSettings.getUniversalApplicationDesc(xService);

        // Soap helper is necessary only for retrieving SSL Context Holder- legacy design
        SOAPHelper soapHelper = new SOAPHelper(xService);
        try {
            soapHelper.init();
        } catch (Exception e) {
            Log.ef(TAG, "triggerLogUpload; cannot initialize soapHelper - to get ssl context");
            return;
        }
        SSLSOAP.SSLContextHolder sslContextHolder = soapHelper.getSslContextHolder();


        // Check if log file exists
        Log.df(TAG, "triggerLogUpload");
        File logFile;
        try {
            logFile = LogMonitor.getLatestLog(xService);
            if (logFile != null && logFile.exists()){
                Log.wf(TAG, "Log found and preparing to be sent, path=%s, size=%dB", logFile.getAbsolutePath(), logFile.length());
                Toast.makeText(xService, String.format("Log found and preparing to be sent, path=%s, size=%dB.", logFile.getAbsolutePath(), logFile.length()), Toast.LENGTH_LONG).show();
            } else {
                Log.wf(TAG, "No log file found.");
                Toast.makeText(xService, "No log file found.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e){
            Log.ef(TAG, e, "sendLogs.setOnPreferenceClickListener error");
            return;
        }

        TaskWithCallback task =  new TaskWithCallback(new LogTask(url, userMessage, appVersion, logFile, sslContextHolder), new TaskWithCallback.Callback() {
            @Override
            public void onCompleted() {
                Log.df(TAG, "onCompleted");
                Toast.makeText(xService, String.format("Log file uploaded, response code=[%d]!.", lastResponseCode), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailed(Exception e) {
                Log.ef(TAG, e, "onFailed");
                Toast.makeText(xService, "Log file upload failed", Toast.LENGTH_SHORT).show();
            }
        });
        taskFuture = executor.submit(task);
    }

    private class LogTask implements RunnableWithException{
        private Exception exception;

        private SSLSOAP.SSLContextHolder sslContextHolder;
        private String urlToSend;
        private String message;
        private String appVersion;
        private File logFile;

        public LogTask(String urlToSend, String message, String appVersion, File logFile, SSLSOAP.SSLContextHolder sslContextHolder) {
            this.urlToSend = urlToSend;
            this.sslContextHolder = sslContextHolder;
            this.message = message;
            this.appVersion = appVersion;
            this.logFile = logFile;
        }

        @Override
        public Exception getThrownException() {
            return exception;
        }

        @Override
        public void run() {
            LogFileUploader logFileUploader = new LogFileUploader(urlToSend, logFile, message, appVersion);
            logFileUploader.setSslContextHolder(sslContextHolder);
            logFileUploader.setConnectionTimeoutMilli(30000);
            logFileUploader.setReadTimeoutMilli(32000);
            try {
                logFileUploader.runUpload();
            } catch(Exception ex){
                exception = ex;
            }
            // Copy result back
            lastResponseCode = logFileUploader.getResponseCode();
            lastResponseMsg = logFileUploader.getResponseMsg();
            lastResponse = logFileUploader.getResponse();
            Log.inf(TAG, "LogTask@run; responseCode=%d, responseMsg=%s, response=%s", lastResponseCode, lastResponseMsg, lastResponse);
        }
    }
}