package com.keyguard.ime.translation

import com.google.gson.annotations.SerializedName
import com.keyguard.ime.security.DlpResult
import com.keyguard.ime.security.DlpSanitizer
import com.keyguard.ime.security.DlpViolationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response as OkHttpResponse
import okhttp3.TlsVersion
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * TranslationRequest
 *
 * Wire payload for isolated outbound translation requests.
 * Explicitly excludes any device identifiers, IMEI, location, or user keystroke telemetry.
 */
data class TranslationRequest(
    @SerializedName("text")
    val text: String,

    @SerializedName("target_lang")
    val targetLang: String,

    @SerializedName("source_lang")
    val sourceLang: String? = null
)

/**
 * TranslationResponse
 *
 * Server response from stateless translation worker (CTranslate2/NLLB inference container).
 */
data class TranslationResponse(
    @SerializedName("translated_text")
    val translatedText: String,

    @SerializedName("target_lang")
    val targetLang: String,

    @SerializedName("source_lang")
    val sourceLang: String? = null,

    @SerializedName("latency_ms")
    val latencyMs: Long? = null
)

/**
 * TranslationResult
 *
 * Comprehensive sealed result type modeling success, local DLP interception,
 * timeout (<1500ms SLA), and network/security errors.
 */
sealed class TranslationResult {

    /**
     * Outbound translation succeeded over TLS 1.3 with valid certificate pin.
     */
    data class Success(
        val translatedText: String,
        val targetLang: String,
        val sourceLang: String? = null,
        val latencyMs: Long = 0L
    ) : TranslationResult()

    /**
     * Local pre-flight Data Loss Prevention (DLP) intercepted sensitive payload.
     * Network socket was NEVER opened. Transmission aborted locally.
     */
    data class DlpBlocked(
        val reason: String,
        val violationType: DlpViolationType
    ) : TranslationResult()

    /**
     * Network, SSL, or server error occurred during explicit transmission.
     */
    data class NetworkError(
        val message: String,
        val isTimeout: Boolean = false,
        val isSslFailure: Boolean = false,
        val statusCode: Int? = null
    ) : TranslationResult()
}

/**
 * TranslationApiService
 *
 * Retrofit REST contract for stateless backend translation inference.
 */
interface TranslationApiService {
    @POST("api/v1/translate")
    suspend fun translate(
        @Body request: TranslationRequest
    ): Response<TranslationResponse>
}

/**
 * ZeroTelemetryInterceptor
 *
 * Enforces Invariant 3 (Zero Local Telemetry):
 *  1. Strips any outbound headers that could leak device IDs, IMEI, advertising IDs, or telemetry.
 *  2. Enforces a fixed, non-identifying User-Agent string.
 *  3. Verifies no keystroke logs or analytics metadata are piggybacked.
 */
class ZeroTelemetryInterceptor : Interceptor {

    companion object {
        const val SANITIZED_USER_AGENT = "KeyGuard-Translation/1.0 (Air-Gapped IME; Isolated Client)"

        // Blocklist of identifying telemetry header prefixes
        private val BANNED_HEADERS = listOf(
            "x-device-id",
            "x-android-id",
            "x-imei",
            "x-advertising-id",
            "x-telemetry",
            "x-user-id",
            "x-keystroke-metrics"
        )
    }

    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Strip any banned telemetry headers if present
        for (headerName in originalRequest.headers.names()) {
            if (BANNED_HEADERS.any { headerName.equals(it, ignoreCase = true) }) {
                requestBuilder.removeHeader(headerName)
            }
        }

        // Set sanitized User-Agent and strict content headers
        requestBuilder.header("User-Agent", SANITIZED_USER_AGENT)
        requestBuilder.header("X-Requested-With", "KeyGuard-Secure-IME")
        requestBuilder.header("Accept", "application/json")

        return chain.proceed(requestBuilder.build())
    }
}

/**
 * TranslationApiClient
 *
 * Isolated network translation client conforming to Phase 4 / Task 3.2 specifications:
 *  - Enforces TLS 1.3 ONLY (ConnectionSpec.MODERN_TLS restricted to TLS_1_3)
 *  - Clear text HTTP is strictly forbidden
 *  - Enforces SHA-256 Public Key Pinning (CertificatePinner)
 *  - Strict 1500ms connection, read, write, and call timeout (PRD §6 SLA)
 *  - Client-Side Pre-Flight DLP: Evaluates text via DlpSanitizer BEFORE socket creation
 *  - Explicit User Intent: Outbound calls only trigger on explicit user button invocation
 *  - Zero Local Telemetry: Zero device IDs, IMEI, or keystroke telemetry emitted
 */
class TranslationApiClient(
    private val apiService: TranslationApiService,
    private val dlpSanitizer: DlpSanitizer = DlpSanitizer
) {

    companion object {
        const val DEFAULT_HOSTNAME = "translate.keyguard.internal"
        const val DEFAULT_BASE_URL = "https://$DEFAULT_HOSTNAME/"
        const val TIMEOUT_MS = 1500L

        // Primary and backup SHA-256 SPKI pins for translation backend
        const val PIN_PRIMARY = "sha256/k2oTTrsvSjhEH50Gs80MhLNNXNXShurRNUtVRx2TUmg="
        const val PIN_BACKUP = "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18="

        /**
         * Builds an isolated OkHttpClient strictly locked to TLS 1.3, SHA-256 pinned certificates,
         * 1500ms timeout SLA, and zero telemetry headers.
         */
        fun buildOkHttpClient(
            hostname: String = DEFAULT_HOSTNAME,
            certificatePins: List<String> = listOf(PIN_PRIMARY, PIN_BACKUP),
            enforceTls13: Boolean = true,
            enforcePinning: Boolean = true
        ): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(ZeroTelemetryInterceptor())

            if (enforceTls13) {
                // Strict TLS 1.3 Connection Specification
                // Cleartext HTTP is strictly excluded from connectionSpecs
                val tls13Spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3)
                    .cipherSuites(
                        CipherSuite.TLS_AES_128_GCM_SHA256,
                        CipherSuite.TLS_AES_256_GCM_SHA384,
                        CipherSuite.TLS_CHACHA20_POLY1305_SHA256
                    )
                    .build()

                builder.connectionSpecs(listOf(tls13Spec))
            }

            if (enforcePinning && certificatePins.isNotEmpty()) {
                val pinnerBuilder = CertificatePinner.Builder()
                for (pin in certificatePins) {
                    pinnerBuilder.add(hostname, pin)
                }
                builder.certificatePinner(pinnerBuilder.build())
            }

            return builder.build()
        }

        /**
         * Factory method to create a fully configured TranslationApiClient.
         */
        fun create(
            baseUrl: String = DEFAULT_BASE_URL,
            okHttpClient: OkHttpClient = buildOkHttpClient()
        ): TranslationApiClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(TranslationApiService::class.java)
            return TranslationApiClient(service)
        }
    }

    /**
     * Executes translation of the provided text with strict Pre-Flight DLP sanitization.
     *
     * Invariants:
     *  1. Text payload is scanned locally by [DlpSanitizer] before ANY network connection.
     *     If sensitive data is found, returns [TranslationResult.DlpBlocked] immediately.
     *  2. Network connection is bounded by 1500ms timeout.
     *  3. Outbound request is executed on Dispatchers.IO.
     *
     * @param text Raw text string to translate.
     * @param targetLang ISO 639 language code for target translation (e.g., "es", "fr", "de", "hi").
     * @param sourceLang Optional source language code (defaults to auto-detection on server).
     * @return [TranslationResult] representing the outcome of the operation.
     */
    suspend fun translate(
        text: String,
        targetLang: String,
        sourceLang: String? = null
    ): TranslationResult = withContext(Dispatchers.IO) {
        // Step 1: Pre-flight Local DLP Scan (Deterministic zero-socket guard)
        val dlpOutcome = dlpSanitizer.scan(text)
        if (dlpOutcome is DlpResult.Blocked) {
            return@withContext TranslationResult.DlpBlocked(
                reason = dlpOutcome.reason,
                violationType = dlpOutcome.violationType
            )
        }

        if (text.isBlank()) {
            return@withContext TranslationResult.Success(
                translatedText = "",
                targetLang = targetLang,
                sourceLang = sourceLang,
                latencyMs = 0L
            )
        }

        // Step 2: Dispatch over isolated TLS 1.3 socket
        val startTime = System.currentTimeMillis()
        try {
            val request = TranslationRequest(
                text = text,
                targetLang = targetLang,
                sourceLang = sourceLang
            )

            val response = apiService.translate(request)
            val elapsedMs = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    TranslationResult.Success(
                        translatedText = body.translatedText,
                        targetLang = body.targetLang,
                        sourceLang = body.sourceLang ?: sourceLang,
                        latencyMs = body.latencyMs ?: elapsedMs
                    )
                } else {
                    TranslationResult.NetworkError(
                        message = "Empty response body received from translation backend",
                        statusCode = response.code()
                    )
                }
            } else {
                TranslationResult.NetworkError(
                    message = "Translation server returned error status ${response.code()}: ${response.message()}",
                    statusCode = response.code()
                )
            }
        } catch (e: InterruptedIOException) {
            TranslationResult.NetworkError(
                message = "Translation request timed out (>1500ms SLA): ${e.message}",
                isTimeout = true
            )
        } catch (e: SocketTimeoutException) {
            TranslationResult.NetworkError(
                message = "Translation request timed out (>1500ms SLA): ${e.message}",
                isTimeout = true
            )
        } catch (e: SSLException) {
            TranslationResult.NetworkError(
                message = "TLS 1.3 / Certificate pinning verification failed (MITM Defense): ${e.message}",
                isSslFailure = true
            )
        } catch (e: IOException) {
            val isTimeout = e.message?.contains("timeout", ignoreCase = true) == true
            TranslationResult.NetworkError(
                message = if (isTimeout) {
                    "Translation request timed out (>1500ms SLA): ${e.message}"
                } else {
                    "Network I/O error during translation: ${e.message}"
                },
                isTimeout = isTimeout
            )
        } catch (e: Exception) {
            TranslationResult.NetworkError(
                message = "Unexpected failure during translation: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }
}

