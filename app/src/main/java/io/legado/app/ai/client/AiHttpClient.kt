package io.legado.app.ai.client

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Paid AI credentials require platform TLS checks, isolated from source/Cronet interceptors. */
internal val aiHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}
