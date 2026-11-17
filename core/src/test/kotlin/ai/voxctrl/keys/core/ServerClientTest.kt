package ai.voxctrl.keys.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * The client against a stand-in server that answers the way `voxctrl-server`
 * does (its `http.rs`, and the "API reference" in VoxCtrl's docs/server.md).
 * If the real server changes what it sends or expects, these are the
 * expectations to revisit; see docs/sync-with-voxctrl.md.
 */
class ServerClientTest {
    private val key = "vx_test_key"
    private var server: HttpServer? = null

    /** What the stand-in server saw of the last transcription request. */
    private var seenAuthorization: String? = null
    private var seenContentType: String? = null
    private var seenBody: ByteArray = ByteArray(0)
    private var seenPurpose: String? = null

    @After
    fun stop() {
        server?.stop(0)
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpExchange.authorized(): Boolean {
        if (requestHeaders.getFirst("Authorization") == "Bearer $key") return true
        reply(
            401,
            """{"error":{"message":"The API key was not accepted. Check it against the api_key in the server's server.toml.","type":"authentication_error"}}""",
        )
        return false
    }

    private fun install(http: HttpServer, transcript: String = "Hello from the server.") {
        http.createContext("/health") {
            it.reply(200, """{"status":"ok","version":"0.9.18","model":"parakeet/tdt-0.6b-v3","cleanup":false,"tts":true,"llm":false}""")
        }
        http.createContext("/v1/models") {
            if (it.authorized()) {
                it.reply(200, """{"object":"list","data":[{"id":"parakeet/tdt-0.6b-v3","object":"model","owned_by":"voxctrl"}]}""")
            }
        }
        http.createContext("/v1/audio/transcriptions") {
            seenAuthorization = it.requestHeaders.getFirst("Authorization")
            seenContentType = it.requestHeaders.getFirst("Content-Type")
            seenPurpose = it.requestHeaders.getFirst("X-VoxCtrl-Purpose")
            seenBody = it.requestBody.readBytes()
            if (it.authorized()) {
                if (String(seenBody, Charsets.ISO_8859_1).contains("RIFF")) {
                    it.reply(200, """{"text":" $transcript ","language":"en","duration":2.4}""")
                } else {
                    it.reply(400, """{"error":{"message":"The upload is not a WAV file.","type":"invalid_request_error"}}""")
                }
            }
        }
        http.start()
        server = http
    }

    private fun plainServer(): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        install(http)
        return "http://127.0.0.1:${http.address.port}/v1"
    }

    private fun wav() = Wav.encode(ShortArray(1600) { (it % 50).toShort() })

    private fun failureOf(block: () -> Unit): ServerException {
        try {
            block()
        } catch (e: ServerException) {
            return e
        }
        fail("expected a ServerException")
        throw AssertionError()
    }

    @Test
    fun probeReportsWhatTheServerOffers() {
        val info = ServerClient(ServerConfig(plainServer(), key, null)).probe()
        assertEquals("0.9.18", info.version)
        assertEquals("parakeet/tdt-0.6b-v3", info.model)
        assertTrue(info.tts)
        assertFalse(info.cleanup)
        assertTrue(Compatibility.supports(info.version))
    }

    @Test
    fun probeWithAWrongKeyShowsTheServersOwnMessage() {
        val error = failureOf { ServerClient(ServerConfig(plainServer(), "vx_wrong", null)).probe() }
        assertEquals(Failure.UNAUTHORIZED, error.failure)
        assertTrue(error.message!!.startsWith("The API key was not accepted."))
    }

    @Test
    fun transcribeUploadsAWavAsMultipartAndReturnsTheTrimmedText() {
        val client = ServerClient(ServerConfig(plainServer(), key, null))
        assertEquals("Hello from the server.", client.transcribe(wav(), prompt = "VoxCtrl, Tailscale", language = "en"))
        assertEquals("Bearer $key", seenAuthorization)
        assertTrue(seenContentType!!.startsWith("multipart/form-data; boundary="))
        val body = String(seenBody, Charsets.ISO_8859_1)
        assertTrue(body.contains("name=\"file\"; filename=\"dictation.wav\""))
        assertTrue(body.contains("Content-Type: audio/wav"))
        assertTrue(body.contains("name=\"response_format\"\r\n\r\njson"))
        assertTrue(body.contains("name=\"prompt\"\r\n\r\nVoxCtrl, Tailscale"))
        assertTrue(body.contains("name=\"language\"\r\n\r\nen"))
        val boundary = seenContentType!!.substringAfter("boundary=")
        assertTrue(body.endsWith("\r\n--$boundary--\r\n"))
    }

    @Test
    fun aLiveClipIsMarkedAndADictationIsNot() {
        val client = ServerClient(ServerConfig(plainServer(), key, null))
        client.transcribe(wav(), live = true)
        assertEquals("live", seenPurpose)
        client.transcribe(wav())
        assertNull(seenPurpose)
    }

    @Test
    fun aBusyServerIsNotAnErrorInTheSettings() {
        // What VoxCtrl 0.9.20 answers a dictation when 8 are already waiting,
        // and a live clip whenever the model is busy.
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/v1/audio/transcriptions") {
            it.requestBody.readBytes()
            it.responseHeaders.add("Retry-After", "1")
            it.reply(
                503,
                """{"error":{"message":"The server is busy: 8 dictations are already waiting. Try again in a moment.","type":"server_busy"}}""",
            )
        }
        http.start()
        server = http
        val client = ServerClient(ServerConfig("http://127.0.0.1:${http.address.port}/v1", key, null))
        val error = failureOf { client.transcribe(wav()) }
        assertEquals(Failure.BUSY, error.failure)
        assertTrue(error.message!!.startsWith("The server is busy"))
    }

    @Test
    fun probeReadsTheContractRevisionWhenTheServerSendsOne() {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/health") {
            it.reply(
                200,
                """{"status":"ok","version":"2.0.0","contract":2,"min_client":"2.0.0","model":"parakeet/tdt-0.6b-v3","cleanup":false,"tts":false,"llm":false}""",
            )
        }
        http.createContext("/v1/models") {
            it.reply(200, """{"object":"list","data":[{"id":"parakeet/tdt-0.6b-v3"}]}""")
        }
        http.start()
        server = http
        val info = ServerClient(ServerConfig("http://127.0.0.1:${http.address.port}/v1", key, null)).probe()
        assertEquals(2, info.contract)
        assertFalse(Compatibility.speaks(info.contract))
        http.stop(0)
        // A server from before 0.9.19 sends no contract at all.
        assertNull(ServerClient(ServerConfig(plainServer(), key, null)).probe().contract)
    }

    @Test
    fun transcribeLeavesOutEmptyOptionalFields() {
        ServerClient(ServerConfig(plainServer(), key, null)).transcribe(wav())
        val body = String(seenBody, Charsets.ISO_8859_1)
        assertFalse(body.contains("name=\"prompt\""))
        assertFalse(body.contains("name=\"language\""))
    }

    @Test
    fun aServerErrorCarriesTheServersMessage() {
        val client = ServerClient(ServerConfig(plainServer(), key, null))
        val error = failureOf { client.transcribe("not audio".toByteArray()) }
        assertEquals(Failure.SERVER_ERROR, error.failure)
        assertEquals("The upload is not a WAV file.", error.message)
    }

    @Test
    fun aServerThatIsNotThereIsUnreachable() {
        val port = ServerSocket(0).use { it.localPort }
        val client = ServerClient(ServerConfig("http://127.0.0.1:$port/v1", key, null), connectTimeoutMs = 1000)
        assertEquals(Failure.UNREACHABLE, failureOf { client.probe() }.failure)
        assertEquals(Failure.UNREACHABLE, failureOf { client.transcribe(wav()) }.failure)
    }

    @Test
    fun nothingIsSentBeforeAnAddressIsEntered() {
        val error = failureOf { ServerClient(ServerConfig("", null, null)).transcribe(wav()) }
        assertEquals(Failure.NOT_CONFIGURED, error.failure)
    }

    // ── Encrypted servers ─────────────────────────────────────────────────────

    private fun testKeyStore(): KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            ServerClientTest::class.java.getResourceAsStream("/test-server.p12").use { load(it, "changeit".toCharArray()) }
        }

    /** An HTTPS stand-in with a self-made certificate; returns its endpoint and fingerprint. */
    private fun encryptedServer(): Pair<String, String> {
        val store = testKeyStore()
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(store, "changeit".toCharArray())
        }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val https = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        https.httpsConfigurator = HttpsConfigurator(context)
        install(https)
        val fingerprint = ServerClient.fingerprintOf(store.getCertificate("server").encoded)
        return "https://127.0.0.1:${https.address.port}/v1" to fingerprint
    }

    @Test
    fun aPairedClientReachesItsServerOverHttps() {
        val (endpoint, fingerprint) = encryptedServer()
        val client = ServerClient(ServerConfig(endpoint, key, fingerprint.uppercase()))
        assertEquals("0.9.18", client.probe().version)
        assertEquals("Hello from the server.", client.transcribe(wav()))
    }

    @Test
    fun aDifferentCertificateIsRefused() {
        val (endpoint, _) = encryptedServer()
        val client = ServerClient(ServerConfig(endpoint, key, "00".repeat(32)))
        val error = failureOf { client.transcribe(wav()) }
        assertEquals(Failure.CERTIFICATE_MISMATCH, error.failure)
        assertNull("the audio must not have been sent", seenAuthorization)
    }

    @Test
    fun anUnpairedClientDoesNotTrustASelfMadeCertificate() {
        val (endpoint, _) = encryptedServer()
        val error = failureOf { ServerClient(ServerConfig(endpoint, key, null)).probe() }
        assertEquals(Failure.UNREACHABLE, error.failure)
    }

    @Test
    fun aPairedClientNeverFallsBackToPlainHttp() {
        val endpoint = plainServer()
        val client = ServerClient(ServerConfig(endpoint, key, "ab".repeat(32)))
        assertEquals(Failure.REFUSED_DOWNGRADE, failureOf { client.transcribe(wav()) }.failure)
        assertNull(seenAuthorization)
    }
}
