package net.phonex.rest;

import java.io.IOException;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import retrofit.client.Request;
import retrofit.client.UrlConnectionClient;

/**
 * Url connection client for use with retrofit REST calls over https with self-signed phonex cert
 * Created by miroc on 12.5.15.
 */
public class PhonexCertUrlConnectionClient extends UrlConnectionClient{
    private SSLSocketFactory sslSocketFactory;

    public PhonexCertUrlConnectionClient(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    @Override
    protected HttpURLConnection openConnection(Request request) throws IOException {
        HttpURLConnection connection = super.openConnection(request);
        HttpsURLConnection sslConnection = (HttpsURLConnection) connection;
        sslConnection.setSSLSocketFactory(sslSocketFactory);
        return sslConnection;
    }
}
