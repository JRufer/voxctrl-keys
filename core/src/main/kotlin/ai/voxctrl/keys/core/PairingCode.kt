package ai.voxctrl.keys.core

import java.util.Base64

/**
 * A pairing code as a VoxCtrl server prints it (`voxctrl-server
 * --pairing-code`): the text `vxpair_` followed by unpadded URL-safe base64 of
 * a small JSON object. The format is defined by the `voxctrl-pairing` crate in
 * the VoxCtrl repository; this class must keep reading what that crate writes.
 *
 * | JSON key | Meaning |
 * |---|---|
 * | `h` | The server's address, when it knows one clients can use. Optional |
 * | `p` | The port |
 * | `k` | The API key, when the server requires one. Optional |
 * | `f` | SHA-256 fingerprint of the server's certificate, 64 hex digits. Present exactly when the server encrypts |
 */
data class PairingCode(
    val host: String?,
    val port: Int,
    val apiKey: String?,
    val fingerprint: String?,
) {
    val encrypted: Boolean get() = fingerprint != null

    /**
     * The endpoint for the settings. [typed] is the address already entered.
     *
     * The desktop app uses the code's own address when it has one and falls
     * back to the typed one. The keyboard can do the opposite
     * ([preferTyped]), because a phone usually reaches the server over a
     * tailnet while the code carries the server's guess at its LAN address:
     * an address the user took the trouble to enter is the one to keep.
     */
    fun endpoint(typed: String, preferTyped: Boolean = false): String {
        val fromCode = host?.takeIf { it.isNotEmpty() }
        val fromUser = Endpoint.hostOf(typed)
        val host = (if (preferTyped) fromUser ?: fromCode else fromCode ?: fromUser)
            ?: throw PairingException(
                "This pairing code does not say where the server is. Enter the server's name or " +
                    "address first, then paste the code again.",
            )
        val scheme = if (encrypted) "https" else "http"
        val bracketed = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "$scheme://$bracketed:$port/v1"
    }

    companion object {
        const val PREFIX = "vxpair_"

        private const val DAMAGED =
            "The pairing code is incomplete or damaged. Copy the whole code from the server again."

        fun looksLikeOne(text: String): Boolean = text.trim().startsWith(PREFIX)

        fun decode(text: String): PairingCode {
            val body = text.trim().removePrefixOrNull(PREFIX)
                ?: throw PairingException("That is not a pairing code. A pairing code starts with \"$PREFIX\".")
            val json = try {
                String(Base64.getUrlDecoder().decode(body.trim()), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                throw PairingException(DAMAGED)
            }
            val fields = Json.parseObject(json) ?: throw PairingException(DAMAGED)
            val port = (fields["p"] as? Double)?.toInt() ?: throw PairingException(DAMAGED)
            val fingerprint = fields["f"] as? String
            if (port !in 1..65535 || (fingerprint != null && !validFingerprint(fingerprint))) {
                throw PairingException(DAMAGED)
            }
            return PairingCode(
                host = fields["h"] as? String,
                port = port,
                apiKey = fields["k"] as? String,
                fingerprint = fingerprint?.lowercase(),
            )
        }

        fun validFingerprint(text: String): Boolean =
            text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        /** A fingerprint as shown to a person: `AB:CD:…`, easier to compare by eye. */
        fun displayFingerprint(fingerprint: String): String = fingerprint.uppercase().chunked(2).joinToString(":")

        private fun String.removePrefixOrNull(prefix: String): String? =
            if (startsWith(prefix)) substring(prefix.length) else null
    }
}

class PairingException(message: String) : Exception(message)
