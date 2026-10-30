package com.keyguard.ime.translation

import com.keyguard.ime.security.DlpSanitizer
import com.keyguard.ime.security.DlpViolationType
import kotlinx.coroutines.test.runTest
import okhttp3.CipherSuite
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * TranslationApiClientTest
 *
 * Comprehensive unit and security test suite verifying:
 *  1. Invariant 1 (Explicit Intent & Local DLP Pre-flight): Sensitive data NEVER touches the network socket.
 *  2. Invariant 2 (MITM Defense): Enforces TLS 1.3, SHA-256 certificate pinning, and disallows cleartext.
 *  3. Invariant 3 (Zero Local Telemetry): Device IDs, IMEI, and keystroke metrics are strictly excluded.
 *  4. SLA SLA Resiliency: Bounded by 1500ms timeout.
 */
class TranslationApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var testClient: TranslationApiClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .writeTimeout(1500, TimeUnit.MILLISECONDS)
            .callTimeout(1500, TimeUnit.MILLISECONDS)
            .addInterceptor(ZeroTelemetryInterceptor())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(TranslationApiService::class.java)
        testClient = TranslationApiClient(service, DlpSanitizer)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // INVARIANT 1: LOCAL PRE-FLIGHT DLP INTERCEPTION (ZERO SOCKET TRAFFIC)
    // =========================================================================

    @Test
    fun `translate blocks credit card and opens ZERO network sockets`() = runTest {
        // Valid Luhn credit card payload (Standard Visa test card)
        val ccPayload = "Payment due: 4111-1111-1111-1111 for order"

        val result = testClient.translate(ccPayload, "es")

        assertTrue("Expected DlpBlocked for credit card", result is TranslationResult.DlpBlocked)
        val blocked = result as TranslationResult.DlpBlocked
        assertEquals(DlpViolationType.CREDIT_CARD, blocked.violationType)
        assertTrue(blocked.reason.contains("Credit card", ignoreCase = true))

        // DOUBLE VERIFICATION: Confirm zero network requests reached MockWebServer
        assertEquals("No network socket should be opened for DLP violations", 0, mockWebServer.requestCount)
    }

    @Test
    fun `translate blocks AWS secret key locally without network egress`() = runTest {
        val awsKeyPayload = "Deploy key AKIAIOSFODNN7EXAMPLE for server setup"

        val result = testClient.translate(awsKeyPayload, "fr")

        assertTrue(result is TranslationResult.DlpBlocked)
        val blocked = result as TranslationResult.DlpBlocked
        assertEquals(DlpViolationType.API_KEY, blocked.violationType)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `translate blocks GitHub personal access token locally`() = runTest {
        val githubTokenPayload = "Token: ghp_1234567890abcdefghijklmnopqrstuvwxyz"

        val result = testClient.translate(githubTokenPayload, "de")

        assertTrue(result is TranslationResult.DlpBlocked)
        val blocked = result as TranslationResult.DlpBlocked
        assertEquals(DlpViolationType.API_KEY, blocked.violationType)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `translate blocks JSON Web Token locally`() = runTest {
        val jwtPayload = "Authorization: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisSignature12345"

        val result = testClient.translate(jwtPayload, "hi")

        assertTrue(result is TranslationResult.DlpBlocked)
        val blocked = result as TranslationResult.DlpBlocked
        assertEquals(DlpViolationType.JWT_CREDENTIAL, blocked.violationType)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `translate blocks PEM private key locally`() = runTest {
        val pemPayload = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0\n-----END RSA PRIVATE KEY-----"

        val result = testClient.translate(pemPayload, "ja")

        assertTrue(result is TranslationResult.DlpBlocked)
        val blocked = result as TranslationResult.DlpBlocked
        assertEquals(DlpViolationType.PRIVATE_KEY, blocked.violationType)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `translate blocks US Social Security Number locally`() = runTest {
        val ssnPayload = "Patient identifier SSN: 123-45-6789"

        val result = testClient.translate(ssnPayload, "zh")

        assertTrue(result is TranslationResult.DlpBlocked)
        val blocked = result as TranslationResult.DlpBlocked
        assertEquals(DlpViolationType.GOVERNMENT_ID_SSN, blocked.violationType)
        assertEquals(0, mockWebServer.requestCount)
    }

    // =========================================================================
    // VALID PAYLOAD TRANSLATION & ZERO TELEMETRY VERIFICATION
    // =========================================================================

    @Test
    fun `translate succeeds for valid text and strips all telemetry headers`() = runTest {
        val mockJsonResponse = """
            {
                "translated_text": "Protegiendo los datos en el borde",
                "target_lang": "es",
                "source_lang": "en",
                "latency_ms": 110
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockJsonResponse)
        )

        val cleanText = "Protecting data at the edge"
        val result = testClient.translate(cleanText, "es")

        assertTrue("Expected TranslationResult.Success", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("Protegiendo los datos en el borde", success.translatedText)
        assertEquals("es", success.targetLang)
        assertEquals("en", success.sourceLang)
        assertEquals(110L, success.latencyMs)

        // Verify request payload and path
        val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("/api/v1/translate", recordedRequest!!.path)
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.body.readUtf8().contains("Protecting data at the edge"))

        // INVARIANT 3: Verify Zero Local Telemetry
        val headers = recordedRequest.headers
        assertNull("X-Device-ID must never be sent", headers["x-device-id"])
        assertNull("X-Android-ID must never be sent", headers["x-android-id"])
        assertNull("X-IMEI must never be sent", headers["x-imei"])
        assertNull("X-Advertising-ID must never be sent", headers["x-advertising-id"])
        assertNull("X-User-ID must never be sent", headers["x-user-id"])
        assertNull("X-Keystroke-Metrics must never be sent", headers["x-keystroke-metrics"])

        assertEquals(
            ZeroTelemetryInterceptor.SANITIZED_USER_AGENT,
            headers["User-Agent"]
        )
    }

    // =========================================================================
    // TIMEOUT SLA (1500ms BOUNDARY)
    // =========================================================================

    @Test
    fun `translate aborts with timeout error when server latency exceeds 1500ms`() = runTest {
        // Enqueue delayed response exceeding the 1500ms SLA
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeadersDelay(2500, TimeUnit.MILLISECONDS)
                .setBody("""{"translated_text": "demorado", "target_lang": "es"}""")
        )

        val result = testClient.translate("Latency test", "es")

        assertTrue("Expected NetworkError with isTimeout = true", result is TranslationResult.NetworkError)
        val error = result as TranslationResult.NetworkError
        assertTrue("Error should be flagged as timeout", error.isTimeout)
        assertTrue(error.message.contains("timed out", ignoreCase = true))
    }

    // =========================================================================
    // SERVER ERROR & RESILIENCE
    // =========================================================================

    @Test
    fun `translate handles 500 server error gracefully`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = testClient.translate("Server error test", "es")

        assertTrue(result is TranslationResult.NetworkError)
        val error = result as TranslationResult.NetworkError
        assertEquals(500, error.statusCode)
        assertFalse(error.isTimeout)
    }

    @Test
    fun `translate returns empty text immediately for blank input`() = runTest {
        val result = testClient.translate("   ", "es")

        assertTrue(result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("", success.translatedText)
        assertEquals(0, mockWebServer.requestCount)
    }

    // =========================================================================
    // INVARIANT 2: TLS 1.3 & CERTIFICATE PINNING CONFIGURATION
    // =========================================================================

    @Test
    fun `buildOkHttpClient enforces TLS 1_3 only and disallows cleartext`() {
        val client = TranslationApiClient.buildOkHttpClient(
            hostname = TranslationApiClient.DEFAULT_HOSTNAME,
            enforceTls13 = true,
            enforcePinning = true
        )

        // Verify connection specifications
        val connectionSpecs = client.connectionSpecs
        assertNotNull(connectionSpecs)
        assertEquals(1, connectionSpecs.size)

        val spec = connectionSpecs[0]
        assertTrue("ConnectionSpec must be TLS (not cleartext)", spec.isTls)

        val tlsVersions = spec.tlsVersions
        assertNotNull(tlsVersions)
        assertEquals("Strictly TLS 1.3 only", listOf(TlsVersion.TLS_1_3), tlsVersions)

        // Verify cipher suites
        val cipherSuites = spec.cipherSuites
        assertNotNull(cipherSuites)
        assertTrue(cipherSuites!!.contains(CipherSuite.TLS_AES_128_GCM_SHA256))
        assertTrue(cipherSuites.contains(CipherSuite.TLS_AES_256_GCM_SHA384))
        assertTrue(cipherSuites.contains(CipherSuite.TLS_CHACHA20_POLY1305_SHA256))

        // Verify 1500ms timeout
        assertEquals(1500, client.connectTimeoutMillis)
        assertEquals(1500, client.readTimeoutMillis)
        assertEquals(1500, client.writeTimeoutMillis)
        assertEquals(1500, client.callTimeoutMillis)

        // Verify certificate pinner contains primary & backup pins
        val pinner = client.certificatePinner
        val pins = pinner.pins
        assertTrue("Certificate pinner should have configured pins", pins.isNotEmpty())
        assertTrue(pins.any { it.pattern == TranslationApiClient.DEFAULT_HOSTNAME })
    }
}

