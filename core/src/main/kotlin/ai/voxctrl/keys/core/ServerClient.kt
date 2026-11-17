package ai.voxctrl.keys.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

/** Where the server is and how to prove who we are to it. */
data class ServerConfig(
    /** The `/v1` address, already tidied by [Endpoint.normalize]. */
    val endpoint: String,
    val apiKey: String?,
    /** The certificate fingerprint from a pairing code, when the server encrypts. */
    val fingerprint: String?,
) {
    val configured: Boolean get() = endpoint.isNotBlank()
}

/** What went wrong, sorted by what the user can do about it. */
enum class Failure {
    /** No address has been entered yet. */
    NOT_CONFIGURED,

    /** The server did not answer: off, asleep, wrong address, or the tailnet is down. */
    UNREACHABLE,

    /** The server answered and refused the API key. */
    UNAUTHORIZED,

    /** An encrypted server showed a certificate other than the paired one. */
    CERTIFICATE_MISMATCH,

    /** The settings would send audio unencrypted to a server paired with encryption on. */
    REFUSED_DOWNGRADE,

    /** The server answered with an error of its own; the message is the server's. */
    SERVER_ERROR,

    /**
     * The server is up but has too much queued (HTTP 503): nothing was run, so
     * the same recording can be sent again in a moment. VoxCtrl 0.9.20 and
     * newer answer a dictation this way when 8 are already waiting.
     */
    BUSY,

    /** The server speaks a newer client contract than this keyboard: update the keyboard. */
    INCOMPATIBLE,
}

class ServerException(val failure: Failure, message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a reachable server says about itself. */
data class ServerInfo(
    val version: String?,
    val model: String?,
    val cleanup: Boolean,
    val tts: Boolean,
    /**
     * The client contract revision the server speaks (`contract` in
     * `/health`, from VoxCtrl 0.9.19). Null from an older server, which
     * speaks revision 1. See [Compatibility.speaks].
     */
    val contract: Int? = null,
)

/**
 * The keyboard's side of the VoxCtrl client contract (docs/protocol.md).
 *
 * It talks to exactly one host, the one in [config], and to nothing else. It
 * keeps no audio and no text: each call holds its data for the length of the
 * request. Calls block, so run them off the main thread.
 */
class ServerClient(
    private val config: ServerConfig,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    /**
     * Checks that the server is there and takes our key: `GET /health`, which
     * needs no key, then `GET /v1/models`, which does.
     */
    fun probe(): ServerInfo {
        requireUsable()
        val healthUrl = Endpoint.healthUrl(config.endpoint)
            ?: throw ServerException(Failure.NOT_CONFIGURED, "The server address is not a valid address.")
        val health = get(healthUrl, withKey = false)
        val fields = if (health.status == 200) Json.parseObject(health.body) else null
        val models = get(Endpoint.modelsUrl(config.endpoint), withKey = true)
        if (models.status != 200) throw errorFor(models)
        val model = fields?.get("model") as? String ?: firstModel(models.body)
        return ServerInfo(
            version = fields?.get("version") as? String,
            model = model,
            cleanup = fields?.get("cleanup") == true,
            tts = fields?.get("tts") == true,
            contract = (fields?.get("contract") as? Double)?.toInt(),
        )
    }

    /**
     * Transcribes a WAV file (16 kHz mono 16-bit PCM) and returns the text.
     * [prompt] biases recognition toward the words in it; [language] is a
     * language code, honoured by whisper.cpp models only.
     *
     * [live] marks a live-text clip of a recording still in progress. It is
     * sent as `X-VoxCtrl-Purpose: live`, so a VoxCtrl server (0.9.20 and
     * newer) runs it only when it has nothing else to do and otherwise
     * answers 503 at once: a live clip never makes anyone's dictation wait.
     * Older servers ignore the header.
     */
    fun transcribe(wav: ByteArray, prompt: String? = null, language: String? = null, live: Boolean = false): String {
        requireUsable()
        val boundary = "voxctrlkeys" + java.lang.Long.toHexString(System.nanoTime())
        val body = ByteArrayOutputStream(wav.size + 512)
        fun field(name: String, value: String) {
            body.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }
        field("response_format", "json")
        if (!prompt.isNullOrBlank()) field("prompt", prompt)
        if (!language.isNullOrBlank()) field("language", language)
        body.write(
            ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"dictation.wav\"\r\n" +
                "Content-Type: audio/wav\r\n\r\n").toByteArray(),
        )
        body.write(wav)
        body.write("\r\n--$boundary--\r\n".toByteArray())

        val response = send(
            Endpoint.transcriptionUrl(config.endpoint),
            method = "POST",
            withKey = true,
            contentType = "multipart/form-data; boundary=$boundary",
            payload = body.toByteArray(),
            headers = if (live) mapOf(PURPOSE_HEADER to PURPOSE_LIVE) else emptyMap(),
        )
        if (response.status != 200) throw errorFor(response)
        val text = Json.parseObject(response.body)?.get("text") as? String
            ?: throw ServerException(Failure.SERVER_ERROR, "The server's answer was not a transcript.")
        return text.trim()
    }

    private fun requireUsable() {
        if (!config.configured) {
            throw ServerException(Failure.NOT_CONFIGURED, "No server address has been entered.")
        }
        // A paired server is never spoken to unencrypted: an http:// address
        // with a fingerprint saved is an error, not a silent downgrade.
        if (!config.fingerprint.isNullOrBlank() && !Endpoint.isHttps(config.endpoint)) {
            throw ServerException(
                Failure.REFUSED_DOWNGRADE,
                "This server was paired with encryption on, so its address must start with https://. " +
                    "Paste the pairing code again, or remove the pairing.",
            )
        }
    }

    private class Response(val status: Int, val body: String)

    private fun get(url: String, withKey: Boolean): Response = send(url, "GET", withKey, null, null)

    private fun send(
        url: String,
        method: String,
        withKey: Boolean,
        contentType: String?,
        payload: ByteArray?,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val connection = try {
            URI(url).toURL().openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ServerException(Failure.NOT_CONFIGURED, "The server address is not a valid address.", e)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (withKey && !config.apiKey.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            val pin = config.fingerprint?.takeIf { it.isNotBlank() }
            if (pin != null && connection is HttpsURLConnection) {
                connection.sslSocketFactory = pinnedContext(pin).socketFactory
                // The pin replaces the name check: a home server is reached by
                // whatever address it has that day, and only the holder of the
                // pinned certificate's private key can complete the handshake.
                connection.setHostnameVerifier { _, _ -> true }
            }
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return Response(status, text)
        } catch (e: SSLException) {
            if (causedByMismatch(e)) {
                throw ServerException(Failure.CERTIFICATE_MISMATCH, CERTIFICATE_MISMATCH_HELP, e)
            }
            throw ServerException(Failure.UNREACHABLE, "Could not make a secure connection to the server.", e)
        } catch (e: IOException) {
            throw ServerException(Failure.UNREACHABLE, "The server could not be reached.", e)
        } finally {
            connection.disconnect()
        }
    }

    /** The server's own message when it sent one in OpenAI's error envelope. */
    private fun errorFor(response: Response): ServerException {
        val error = Json.parseObject(response.body)?.get("error") as? Map<*, *>
        val message = error?.get("message") as? String
        return when (response.status) {
            401, 403 -> ServerException(
                Failure.UNAUTHORIZED,
                message ?: "The API key was not accepted. Paste the server's pairing code again.",
            )
            503 -> ServerException(Failure.BUSY, message ?: "The server is busy. Try again in a moment.")
            else -> ServerException(Failure.SERVER_ERROR, message ?: "The server answered with an error (HTTP ${response.status}).")
        }
    }

    private fun firstModel(body: String): String? {
        val data = Json.parseObject(body)?.get("data") as? List<*> ?: return null
        return (data.firstOrNull() as? Map<*, *>)?.get("id") as? String
    }

    companion object {
        /**
         * Short, as in the desktop app: a server that is off or on another
         * network does not refuse the connection, it just never answers.
         */
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT = "voxctrl-keys"

        /** What a transcription request is for; see [transcribe]. */
        const val PURPOSE_HEADER = "X-VoxCtrl-Purpose"
        const val PURPOSE_LIVE = "live"

        const val CERTIFICATE_MISMATCH = "the server's certificate is not the one this app was paired with"
        const val CERTIFICATE_MISMATCH_HELP =
            "The server's certificate is not the one this keyboard was paired with. If the server was " +
                "reinstalled or its certificate replaced, paste its new pairing code. Otherwise something " +
                "else is answering at this address."

        /** SHA-256 of a certificate's DER bytes, in lowercase hex: what a pairing code carries. */
        fun fingerprintOf(certificateDer: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(certificateDer).joinToString("") { "%02x".format(it) }

        /** TLS settings that trust only the certificate with this fingerprint. */
        fun pinnedContext(fingerprint: String): SSLContext {
            val expected = fingerprint.trim().lowercase()
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    throw CertificateException("client certificates are not used")
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    val leaf = chain?.firstOrNull() ?: throw CertificateException(CERTIFICATE_MISMATCH)
                    if (fingerprintOf(leaf.encoded) != expected) throw CertificateException(CERTIFICATE_MISMATCH)
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        }

        private fun causedByMismatch(error: Throwable): Boolean {
            var e: Throwable? = error
            var depth = 0
            while (e != null && depth++ < 8) {
                if (e.message?.contains(CERTIFICATE_MISMATCH) == true) return true
                e = e.cause
            }
            return false
        }
    }
}
