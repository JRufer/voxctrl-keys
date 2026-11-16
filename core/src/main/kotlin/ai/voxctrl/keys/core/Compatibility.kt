package ai.voxctrl.keys.core

/**
 * Which VoxCtrl servers this keyboard works with.
 *
 * The contract itself is written down in docs/protocol.md, and in the VoxCtrl
 * repository under docs/clients.md. When either side changes what it sends or
 * expects, [PROTOCOL_REVISION] and those two documents change together.
 */
object Compatibility {
    /** The revision of the client contract this build was written against. */
    const val PROTOCOL_REVISION = 1

    /** The first server release that has everything the keyboard uses. */
    const val MIN_SERVER_VERSION = "0.9.0"

    /** True when [serverVersion] (as `/health` reports it) is new enough. An unreadable version passes. */
    fun supports(serverVersion: String?): Boolean {
        val server = parse(serverVersion ?: return true) ?: return true
        val minimum = parse(MIN_SERVER_VERSION) ?: return true
        for (i in 0 until maxOf(server.size, minimum.size)) {
            val a = server.getOrElse(i) { 0 }
            val b = minimum.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return true
    }

    private fun parse(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() }
    }
}
