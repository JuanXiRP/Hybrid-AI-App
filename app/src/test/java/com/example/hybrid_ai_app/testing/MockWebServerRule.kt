package com.example.hybrid_ai_app.testing

import com.example.hybrid_ai_app.core.data.remote.NetworkJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource
import retrofit2.Retrofit
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * Starts a [MockWebServer] for a test and builds a real Retrofit against it.
 *
 * Repository tests go through a genuine Retrofit + OkHttp stack rather than a mocked `...Api`
 * interface, because the mock cannot fail the way production fails. Only the real stack exercises:
 *
 *  - the `@GET`/`@POST` path strings and HTTP verbs on the `...Api` interfaces,
 *  - request-body serialisation, including `encodeDefaults = false` omitting defaulted fields,
 *  - response deserialisation through the production [NetworkJson] instance,
 *  - `errorBody()` being a **one-shot stream** — `serverMessageOrNull()` and
 *    `premiumRequiredOrNull()` both consume it, and the second caller sees an empty string. A
 *    `mockk`-built `Response.error(...)` re-readable in memory hides that entirely.
 *
 * The client deliberately does **not** carry the production interceptors: no certificate pinner
 * (it is pinned to the Render host and would reject localhost), no auth interceptor (that needs a
 * `PreferencesManager` backed by the Android Keystore), and no `TokenAuthenticator`. Those belong
 * to `NetworkModule` and are tested separately. Timeouts are short so the IOException case fails
 * in milliseconds instead of the production 90 seconds.
 */
class MockWebServerRule : ExternalResource() {

    lateinit var server: MockWebServer
        private set

    lateinit var retrofit: Retrofit
        private set

    override fun before() {
        server = MockWebServer().apply { start() }
        retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .writeTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .build(),
            )
            .addConverterFactory(NetworkJson.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
    }

    override fun after() {
        server.shutdown()
    }

    /** Creates the Retrofit service under test, e.g. `rule.api(UserApi::class.java)`. */
    fun <T> api(service: Class<T>): T = retrofit.create(service)

    /** Queues a 2xx JSON body. */
    fun enqueueJson(body: String, code: Int = HttpURLConnection.HTTP_OK) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", JSON_MEDIA_TYPE)
                .setBody(body),
        )
    }

    /** Queues a response with no body at all — what `POST /api/billing/rtdn` answers with. */
    fun enqueueEmpty(code: Int) {
        server.enqueue(MockResponse().setResponseCode(code))
    }

    /**
     * Queues a response the client can never finish reading, to drive the `IOException` branch.
     *
     * `NO_RESPONSE` makes MockWebServer accept the connection and then close it without replying,
     * which surfaces as an `IOException` in OkHttp — the same branch a real dropped connection
     * takes, and far faster and less flaky than waiting out a read timeout.
     */
    fun enqueueConnectionFailure() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
    }

    /** The next request the client actually sent, for asserting on path, method and body. */
    fun takeRequest(): RecordedRequest = server.takeRequest(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        ?: error("Expected the client to send a request, but none arrived")

    private companion object {
        const val JSON_MEDIA_TYPE = "application/json"
        const val TIMEOUT_MILLIS = 2_000L
    }
}
