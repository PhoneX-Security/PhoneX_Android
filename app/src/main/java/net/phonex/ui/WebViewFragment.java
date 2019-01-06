package net.phonex.ui;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import net.phonex.R;
import net.phonex.soap.SSLSOAP;
import net.phonex.util.Log;
import net.phonex.util.crypto.CertificatesAndKeys;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/**
 * In order to load static file from assets: webView.loadUrl("file:///android_asset/legal.html");
 *
 * Created by ph4r05 on 7/25/14.
 */
public class WebViewFragment extends Fragment {
    private static final String TAG="WebViewFragment";
    public  static final String PARAM_URL = "param_url";
    public  static final String PARAM_INTENT_IF_CERT_BROKEN = "intent_on_broken";
    public  static final String TXNAME = "webview";

    /**
     * URL to load by webview.
     */
    private String url2load = null;
    private boolean intentIfCertBroken=true;

    private Context ctxt;
    private PhonexWebViewClient client;
    private SSLContext sslContext;
    private WebView webView;

    private X509TrustManager systm;
    private X509TrustManager webtm;

    /**
     * Factory method.
     * @return
     */
    public static WebViewFragment newInstance() {
        return new WebViewFragment();
    }

    /**
     * Creates a new instance of this fragment with given URL to load.
     * @param url2load
     * @return
     */
    public static WebViewFragment newInstance(String url2load){
        return newInstance(url2load, true);
    }

    /**
     * Creates a new instance of this fragment with given URL to load.
     * @param url2load
     * @return
     */
    public static WebViewFragment newInstance(String url2load, boolean intentIfCertBroken){
        Bundle args = new Bundle();
        args.putString(WebViewFragment.PARAM_URL, url2load);
        args.putBoolean(WebViewFragment.PARAM_INTENT_IF_CERT_BROKEN, intentIfCertBroken);

        WebViewFragment newFragment = WebViewFragment.newInstance();
        newFragment.setArguments(args);
        return newFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        url2load = getUrl();
        intentIfCertBroken = sendIntentIfCertBroken();
        if (url2load==null){
            Log.wf(TAG, "Empty URL");
            dismiss();
        }

        View v = inflater.inflate(R.layout.simple_webview, container, false);
        client = new PhonexWebViewClient(v, getActivity());
        ctxt = getActivity();

        webView = (WebView) v.findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(client);

        TrustLoader tl = new TrustLoader();
        tl.execute();

        return v;
    }

    /**
     * Returns whether to send an intent if certificate is broken.
     * @return
     */
    private boolean sendIntentIfCertBroken(){
        final Bundle arguments = getArguments();
        if (arguments == null){
            return true;
        }

        return arguments.getBoolean(PARAM_INTENT_IF_CERT_BROKEN);
    }

    /**
     * Obtain URL to visit from fragments parameters.
     * @return
     */
    private String getUrl(){
        final Bundle arguments = getArguments();
        if (arguments == null){
            return null;
        }

        return arguments.getString(PARAM_URL);
    }

    /**
     * Removes this fragment from back stack.
     */
    private void dismiss(){
        Activity act = getActivity();
        if (act==null){
            return;
        }

        act.getFragmentManager().popBackStack();
    }

    /**
     * Event invoked after loading SSL context to be used with WebView.
     */
    private void onContextLoaded(){
        Log.v(TAG, "Context loaded");
        if (isDetached() || webView==null || webtm == null){
            return;
        }

        Log.vf(TAG, "Loading URL: %s", url2load);
        webView.loadUrl(url2load);
    }

    /**
     * Determines if certificate is valid w.r.t. system TM and Web TM.
     * @param cert
     * @return
     */
    private boolean isCertValid(X509Certificate cert){
        if (cert==null){
            return false;
        }

        return (SSLSOAP.isCertificateValid(cert, webtm) || SSLSOAP.isCertificateValid(cert, systm));
    }

    /**
     * Custom web client.
     * We have custom TrustStore here.
     */
    private class PhonexWebViewClient extends WebViewClient {
        private final View parentView;
        private final Context ctxt;

        public PhonexWebViewClient(View v, Context ctxt) {
            parentView = v;
            this.ctxt = ctxt;
        }

        @Override
        public void onPageFinished(final WebView view, String url) {
            super.onPageFinished(view, url);
            LinearLayout indicator = (LinearLayout) parentView.findViewById(R.id.loading_progress);
            indicator.setVisibility(View.GONE);
            Log.v(TAG, "onPageFinished()");
        }

        /**
         * Handle SSL errors, If the certificate is valid according to our criteria, accept it.
         * @param view
         * @param handler
         * @param error
         */
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            Log.vf(TAG, "onReceivedSslError(), error: %s", error.toString());
            final SslCertificate cert = error.getCertificate();

            final Bundle bundle = SslCertificate.saveState(cert);
            byte[] b = bundle.getByteArray("x509-certificate");
            if (b!=null){
                try {
                    final X509Certificate x509Certificate = CertificatesAndKeys.buildCertificate(b);
                    final boolean valid = isCertValid(x509Certificate);
                    Log.vf(TAG, "Cert valid=%s, decoded: %s", valid, x509Certificate);

                    if (valid){
                        handler.proceed();
                        return;
                    }
                } catch (Exception e) {
                    Log.wf(TAG, e, "Certificate cannot be decoded");
                }
            }

            // SSL certificate verification failed.
            // --> blank screen.
            handler.cancel();
            if (intentIfCertBroken){
                Uri uri = Uri.parse(url2load);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                ctxt.startActivity(intent);

                dismiss();
            }
        }
    }

    /**
     * Loads trust store in background & starts webview on finish.
     */
    private class TrustLoader extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            try {
                // Initialize trustview for WebView check.
                // It is not possible to set neither SSLContext nor SSLSocketFactory to the WebView
                // without nasty reflection hacks, so for custom certificates (not valid), we use
                // our trust managers for verification. We do not set default HTTPS socket factory
                // either so SOAP calls work properly if they happen to interfere in background.
                systm = SSLSOAP.getSystemTrustManager(ctxt);
                webtm = SSLSOAP.getDefaultWebTrustManager(ctxt);

                // Just to be sure - set custom host name verifier.
                SSLSOAP.setHostnameVerifier();
            } catch (Exception ex){
                Log.e(TAG, "Exception: SSLContext cannot be loaded", ex);
                webtm = null;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            onContextLoaded();
        }
    }
}
