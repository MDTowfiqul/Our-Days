package app.sinceus.cloud

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Gemeinsamer HTTP-Client für die Cloud-Anbieter. */
internal val cloudHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
}
