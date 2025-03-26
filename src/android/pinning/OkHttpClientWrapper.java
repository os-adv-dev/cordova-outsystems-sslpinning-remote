package com.outsystems.plugins.sslpinning.pinning;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;

public class OkHttpClientWrapper {

    private static OkHttpClientWrapper instance;
    int timeoutSeconds = 30;

    private OkHttpClient client;
    private OkHttpClientWrapper() {
        if(client == null) {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
            clientBuilder.connectTimeout(timeoutSeconds, TimeUnit.SECONDS);
            clientBuilder.readTimeout(timeoutSeconds, TimeUnit.SECONDS);
            clientBuilder.retryOnConnectionFailure(true);
            clientBuilder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
            client = clientBuilder.build();
        }
    }

    public static OkHttpClientWrapper getInstance() {
        if(instance == null) {
            instance = new OkHttpClientWrapper();
        }

        return instance;
    }

    public OkHttpClient getOkHttpClient() {
        return client;
    }
}
