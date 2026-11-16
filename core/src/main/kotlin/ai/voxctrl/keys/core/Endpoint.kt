package ai.voxctrl.keys.core

/**
 * Server addresses: tidying what a person types, building the URLs the
 * keyboard calls, and telling what kind of network an address is on.
 *
 * The tidying rules are the VoxCtrl desktop app's (`normalizeServerAddress` in
 * `src/lib/Settings/server-setup.ts`, and `normalize_transcription_url` in
 * `voxctrl-inference`), so an address means the same thing in both apps.
 */
object Endpoint {
    /** The port a VoxCtrl server listens on by default. */
    const val DEFAULT_PORT = 8787

    private val SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val ENDS_WITH_PORT = Regex(":\\d+$")
    private val SCHEME_AND_AUTHORITY = Regex("^https?://[^/]*", RegexOption.IGNORE_CASE)

    /**
     * A bare host or host:port becomes a full `/v1` address. A complete
     * address is left alone, and an empty box stays empty.
     */
    fun normalize(typed: String, encrypted: Boolean = false): String {
        var address = typed.trim().trimEnd('/')
        if (address.isEmpty()) return ""
        if (!SCHEME.containsMatchIn(address)) {
            val bare = !address.contains('/') && !ENDS_WITH_PORT.containsMatchIn(address) && !address.startsWith("[")
            val scheme = if (encrypted) "https" else "http"
            address = "$scheme://$address" + if (bare) ":$DEFAULT_PORT" else ""
        }
        val path = address.replace(SCHEME_AND_AUTHORITY, "")
        if (path.isEmpty()) address = "$address/v1"
        return address
    }

    fun transcriptionUrl(endpoint: String): String {
        val trimmed = endpoint.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/audio/transcriptions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/audio/transcriptions"
            else -> "$trimmed/v1/audio/transcriptions"
        }
    }

    fun modelsUrl(endpoint: String): String {
        val trimmed = endpoint.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/audio/transcriptions") -> trimmed.removeSuffix("/audio/transcriptions") + "/models"
            trimmed.endsWith("/v1") -> "$trimmed/models"
            else -> "$trimmed/v1/models"
        }
    }

    /** `/health` sits at the root of the server, beside `/v1`. */
    fun healthUrl(endpoint: String): String? {
        val root = SCHEME_AND_AUTHORITY.find(endpoint.trim())?.value ?: return null
        return "$root/health"
    }

    fun isHttps(endpoint: String): Boolean = endpoint.trim().startsWith("https://", ignoreCase = true)

    /**
     * The host part of an address as a person types it: with or without a
     * scheme, port or path. Mirrors `host_of` in the `voxctrl-pairing` crate.
     */
    fun hostOf(address: String): String? {
        var rest = address.trim()
        val scheme = rest.indexOf("://")
        if (scheme >= 0) rest = rest.substring(scheme + 3)
        val authority = rest.substringBefore('/')
        val host = if (authority.startsWith("[")) {
            authority.substring(1).substringBefore(']')
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0 && authority.substring(colon + 1).all { it.isDigit() }) authority.substring(0, colon) else authority
        }
        return host.ifEmpty { null }
    }

    /** A short name for the status label on the bar: the host, without port or path. */
    fun displayName(endpoint: String): String = hostOf(endpoint) ?: endpoint.trim()

    enum class Network {
        /** A Tailscale address or MagicDNS name: WireGuard encrypts the traffic. */
        TAILNET,

        /** A private or link-local address, or a name with no dots: the local network. */
        LOCAL,

        /** Anything else. Plain HTTP to one of these crosses networks in the clear. */
        PUBLIC,
    }

    fun networkOf(endpoint: String): Network {
        val host = (hostOf(endpoint) ?: return Network.PUBLIC).lowercase().trimEnd('.')
        if (host.endsWith(".ts.net")) return Network.TAILNET
        ipv4(host)?.let { (a, b) ->
            return when {
                a == 100 && b in 64..127 -> Network.TAILNET // 100.64.0.0/10, Tailscale's range
                a == 10 || a == 127 -> Network.LOCAL
                a == 172 && b in 16..31 -> Network.LOCAL
                a == 192 && b == 168 -> Network.LOCAL
                a == 169 && b == 254 -> Network.LOCAL
                else -> Network.PUBLIC
            }
        }
        if (host.contains(':')) {
            return when {
                host.startsWith("fd7a:115c:a1e0:") -> Network.TAILNET // Tailscale's IPv6 range
                host == "::1" -> Network.LOCAL
                host.startsWith("fc") || host.startsWith("fd") -> Network.LOCAL // fc00::/7
                host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb") ->
                    Network.LOCAL // fe80::/10
                else -> Network.PUBLIC
            }
        }
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) {
            return Network.LOCAL
        }
        // A single-label name ("homeserver") can only be resolved on the local
        // network or by MagicDNS, never on the public internet.
        return if (!host.contains('.')) Network.LOCAL else Network.PUBLIC
    }

    /**
     * True when sending audio to [endpoint] would cross a network unencrypted:
     * plain HTTP to an address that is neither on a tailnet nor local.
     */
    fun sendsInTheClear(endpoint: String): Boolean = !isHttps(endpoint) && networkOf(endpoint) == Network.PUBLIC

    private fun ipv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val numbers = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 && it.length <= 3 } ?: return null }
        return numbers[0] to numbers[1]
    }
}
