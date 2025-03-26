package com.outsystems.plugins.sslpinning;

import android.util.Pair;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;

import com.outsystems.plugins.oscache.cache.helpers.MimeTypesHelper;
import com.outsystems.plugins.oslogger.OSLogger;
import com.outsystems.plugins.oslogger.interfaces.Logger;
import com.outsystems.plugins.sslpinning.pinning.OkHttpClientWrapper;
import com.outsystems.plugins.sslpinning.pinning.X509TrustManagerWrapper;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AddPinningWebClient {

    private final Logger logger = OSLogger.getInstance();
    private static final ConnectionPool sharedPool = new ConnectionPool(5, 300, TimeUnit.SECONDS);
    private final OkHttpClient cachedClient = getHttpClientBuilder().build();

    /**
     * COMMENT from now use another one for performance no block Main Thread
     * public WebResourceResponse getSSLUrlValidation(String url) {
        CompletableFuture<Pair<Boolean, String>> sslPinningFuture = new CompletableFuture<>();
        requestSSLPinning(url, new SSLErrorCallback() {
            @Override
            public void onError(String code, String message) {
                logger.logError("Failed to parse pinning JSON file: "+message, "OSSSLPinning");
                sslPinningFuture.complete(new Pair<>(false, message));
            }

            @Override
            public void onSuccess() {
                sslPinningFuture.complete(new Pair<>(true, "success"));
            }
        });

        try {
            Pair<Boolean, String> result = sslPinningFuture.get();
            if (!result.first) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(url);
                String mimeType = MimeTypesHelper.getInstance().getMimeType(extension);
                return new WebResourceResponse(mimeType, "UTF-8", 525, "SSLPinning found some problem with the request "+ sslPinningFuture.get().second, null, null);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.logError("Failed to parse pinning ExecutionException: " + e.getMessage(), "OSSSLPinning");
            return new WebResourceResponse("text/plain", "UTF-8", 525, "SSLPinning execution error: " + e.getMessage(), null, null);
        }
        return null;
    }**/

    public WebResourceResponse getSSLUrlValidation(String url) {
        AtomicReference<Pair<Boolean, String>> result = new AtomicReference<>();        CountDownLatch latch = new CountDownLatch(1);
        requestSSLPinning(url, new SSLErrorCallback() {
            @Override
            public void onError(String code, String message) {
                result.set(new Pair<>(false, message));
                latch.countDown();
            }

            @Override
            public void onSuccess() {
                result.set(new Pair<>(true, "success"));
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(7, TimeUnit.SECONDS);
            Pair<Boolean, String> finalResult = result.get();

            if (!completed || finalResult == null || !finalResult.first) {
                String ext = MimeTypeMap.getFileExtensionFromUrl(url);
                String mime = MimeTypesHelper.getInstance().getMimeType(ext);
                String msg = (finalResult != null) ? finalResult.second : "SSL timeout or unknown error";
                return new WebResourceResponse(mime, "UTF-8", 525, "SSLPinning error: " + msg, null, null);
            }

        } catch (InterruptedException e) {
            logger.logError("Interrupted while waiting for SSL pinning: " + e.getMessage(), "OSSSLPinning");
            return new WebResourceResponse("text/plain", "UTF-8", 525, "SSLPinning interrupted: " + e.getMessage(), null, null);
        }

        return null;
    }

    private void requestSSLPinning(final String url, final SSLErrorCallback callback) {
        Request request = new Request.Builder().url(url).build();
        Call call = cachedClient.newCall(request);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                logger.logError("Failure to requestSSLPinning : " + e.getMessage(), "OSSSLPinning");
                callback.onError("525", "SSLPinning found some problem with the request: "+e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                logger.logDebug("OnResponse to requestSSLPinning : response: " + response, "OSSSLPinning");

                try {
                    response.close();
                } catch (Exception e) {
                    logger.logError("⚠️ Failed to close response: " + e.getMessage(), "OSSSLPinning");
                }

                callback.onSuccess();
            }
        });
    }

    public interface SSLErrorCallback {
        void onError(String code, String message);
        void onSuccess();
    }

    private OkHttpClient.Builder getHttpClientBuilder() {
        OkHttpClient.Builder clientBuilder = OkHttpClientWrapper.getInstance().getOkHttpClient().newBuilder();
        CertificatePinner remoteCertificate = X509TrustManagerWrapper.getCertificatePinner();

        if (remoteCertificate != null && !remoteCertificate.getPins().isEmpty()) {
            clientBuilder.certificatePinner(remoteCertificate);
        }

        try {
            clientBuilder.connectionPool(sharedPool);
        } catch (Exception e) {
            logger.logError("Failed to get preference " + "sslpinning-connection-keep-alive" + ": " + e.getMessage(), "OSSSLPinning");
        }
        return clientBuilder;
    }
}
