package net.phonex.ft;

import net.phonex.ft.misc.OperationCancelledException;
import net.phonex.ft.misc.TransmitProgress;
import net.phonex.pub.proto.ServerProtoBuff;
import net.phonex.soap.SSLSOAP;
import net.phonex.util.Base64;
import net.phonex.util.Log;
import net.phonex.util.MiscUtils;

import java.io.DataOutputStream;
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
public abstract class GenericUploader {
    protected static final String TAG        = "GenericUploader";
    protected static final String lineEnd    = "\r\n";
    protected static final String twoHyphens = "--";
    protected static final String strHeader  = "Content-Disposition: form-data; name=\"%s\"; " + lineEnd;
    protected static final String fileHeader = "Content-Disposition: form-data; name=\"%s\";filename=\"%s\"" + lineEnd;
    protected String boundary;

    private SSLSOAP.SSLContextHolder sslContextHolder;
    private SecureRandom rand;
    private String urlServer;

    protected DataOutputStream outputStream = null;
    protected CopyOutputStream cos=null;

    public boolean debug=false;
    public int responseCode;
    public String responseMsg;
    public ServerProtoBuff.RESTUploadPost response;
    public TransmitProgress progress;
    private int connectionTimeoutMilli;
    private int readTimeoutMilli;

    public GenericUploader(String targetUrl){
        urlServer = targetUrl;
        rand = new SecureRandom();

        // Generate random boundary between multi-part files,
        // long enough so it does not collide (p is small).
        StringBuilder sb = new StringBuilder("Boundary--");
        for(int i=0; i<16; i++){
            sb.append(DHKeyHelper.MULTIPART_CHARS[rand.nextInt(DHKeyHelper.MULTIPART_CHARS.length)]);
        }

        boundary = "----------------" + sb.toString();
    }

    /**
    * Writes simple string value as multipart/form-data.
    *
    *
    * @param name
    * @param value
    * @param lastOne
    * @throws IOException
    */
    protected void writeString(String name, String value, boolean lastOne) throws IOException {
        outputStream.writeBytes(String.format(strHeader, name));
        outputStream.writeBytes("Content-Type: text/plain;charset=UTF-8" + lineEnd);
        outputStream.writeBytes(lineEnd);
        outputStream.writeBytes(value);
        outputStream.writeBytes(lineEnd);
        outputStream.writeBytes(twoHyphens + boundary + (lastOne ? twoHyphens : "") + lineEnd);
    }

    /**
    * Main entry point for uploading a files according to file transfer protocol to the given url.
    * Holder has to be initialized (files have to be already encrypted, fileNames of the encrypted
    * files set in the holder, etc...).
    * HTTPs has to be used (forced).
    *
    * Method takes care about whole HTTPS POST request with multipart/form-data encoding of the
    * protocol attributes.
    *
//    * @param holder		FTHolder with prepared file protocol attributes.
//    * @param user			Field needed for protocol attribute, user recipient.
    * @throws Exception
    */

    protected HttpsURLConnection createConnection(URL url) throws IOException {

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
//        HttpsURLConnection connection = (HttpsURLConnection) new OkUrlFactory(client).open(url);

        // By default, default SSL socket factory & hostname verifier is used, but
        // set it here specifically.
        if (sslContextHolder==null || sslContextHolder.sslSocketFactory ==null || sslContextHolder.hostVerifier==null){
            throw new SecurityException("Empty SSL holder");
        }

        connection.setHostnameVerifier(sslContextHolder.hostVerifier);
        connection.setSSLSocketFactory(sslContextHolder.sslSocketFactory);

        // Allow Inputs & Outputs
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setChunkedStreamingMode(1024);
        connection.setRequestProperty("Transfer-Encoding", "chunked");
        connection.setConnectTimeout(connectionTimeoutMilli);
        connection.setReadTimeout(readTimeoutMilli);

        // Set POST method & required headers.
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", "PhoneX");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        return connection;
    }

    protected abstract void uploadFileInternals(DataOutputStream outputStream) throws IOException;

    public void runUpload() throws Exception {
        try {
            URL url = new URL(urlServer);
            HttpsURLConnection connection = createConnection(url);
            OutputStream sos = connection.getOutputStream();
            if (debug){
                // FIXME disabled because of occasional OOM on file transfer in debug mode
                //cos = new CopyOutputStream(sos);
                debug = false;
            }
            // Starting writing multipart data.
            outputStream = new DataOutputStream(debug ? cos : sos);

            // the upload itself
            uploadFileInternals(outputStream);

            outputStream.flush();
            Log.v(TAG, "Upload output stream flushed");

            if (debug){
                Log.vf(TAG, "Server dump2: %s", cos.dump());
            }

            // Responses from the server (code and message)
            responseCode = connection.getResponseCode();
            responseMsg = connection.getResponseMessage();
            Log.vf(TAG, "Server Response Code: %s", responseCode);
            Log.vf(TAG, "Server Response Message: %s", responseMsg);

            // Read server response string (message is encoded in it).
            InputStream is = connection.getInputStream();

            // Decode response
            if (responseCode!=200){
                response=null;
                Log.v(TAG, "Server code is not 200, cannot process.");

            } else {
                try {
                    // Read whole response to the array.
                    final byte[] resp = Base64.decode(DHKeyHelper.readInputStream(is));
                    response = ServerProtoBuff.RESTUploadPost.parseFrom(resp);

                    Log.vf(TAG, "Response from server: %s", response);
                } catch(Exception ex){
                    Log.w(TAG, "Cannot decode ProtoBuff POST response", ex);
                }
            }

            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch(OperationCancelledException cex){
            Log.i(TAG, "Operation was cancelled");
            MiscUtils.closeSilently(outputStream);

            throw cex;

        } catch (Exception ex) {
            Log.e(TAG, "Send file Exception", ex);
            throw ex;
        } finally {
            MiscUtils.closeSilently(outputStream);
        }
    }

    /**
     * @param progress the progress to set
     */
    public void setProgress(TransmitProgress progress) {
        this.progress = progress;
    }

    /**
     * @return the responseCode
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * @return the responseMsg
     */
    public String getResponseMsg() {
        return responseMsg;
    }

    /**
     * @return the response
     */
    public ServerProtoBuff.RESTUploadPost getResponse() {
        return response;
    }

    @SuppressWarnings("unused")
    public void setDebug(boolean debug){
        this.debug=debug;
    }

    public SSLSOAP.SSLContextHolder getSslContextHolder() {
        return sslContextHolder;
    }

    public void setSslContextHolder(SSLSOAP.SSLContextHolder sslContextHolder) {
        this.sslContextHolder = sslContextHolder;
    }

    public int getConnectionTimeoutMilli() {
        return connectionTimeoutMilli;
    }

    public void setConnectionTimeoutMilli(int connectionTimeoutMilli) {
        this.connectionTimeoutMilli = connectionTimeoutMilli;
    }

    public int getReadTimeoutMilli() {
        return readTimeoutMilli;
    }

    public void setReadTimeoutMilli(int readTimeoutMilli) {
        this.readTimeoutMilli = readTimeoutMilli;
    }
}
