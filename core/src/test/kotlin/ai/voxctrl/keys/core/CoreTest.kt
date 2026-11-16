package ai.voxctrl.keys.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JsonTest {
    @Test
    fun readsWhatTheServerSends() {
        val health = Json.parseObject(
            """{"status":"ok","version":"0.9.18","model":"parakeet/tdt-0.6b-v3","cleanup":false,"tts":true,"llm":false}""",
        )!!
        assertEquals("0.9.18", health["version"])
        assertEquals(true, health["tts"])
        assertEquals(false, health["cleanup"])
    }

    @Test
    fun readsNestingEscapesAndNumbers() {
        val value = Json.parseObject("""{"a":[1,2.5,-3e2,null],"b":{"c":"line\nbreak \"q\" é"}}""")!!
        assertEquals(listOf(1.0, 2.5, -300.0, null), value["a"])
        assertEquals("line\nbreak \"q\" é", (value["b"] as Map<*, *>)["c"])
    }

    @Test
    fun refusesWhatIsNotJson() {
        assertNull(Json.parseObject("<html>Bad gateway</html>"))
        assertNull(Json.parseObject("""{"text": "unfinished"""))
        assertNull(Json.parseObject("[1,2]"))
        assertNull(Json.parseObject("""{"a":1} trailing"""))
    }

    @Test
    fun quotesRoundTrip() {
        val text = "say \"hi\"\\\n\tdone"
        assertEquals(text, Json.parse(Json.quote(text)))
    }
}

class WavTest {
    @Test
    fun writesA16kMono16BitHeader() {
        val wav = Wav.encode(shortArrayOf(1, -2, 256))
        assertEquals(44 + 6, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVEfmt ", String(wav, 8, 8))
        assertEquals("data", String(wav, 36, 4))
        fun int32(at: Int) = (wav[at].toInt() and 0xFF) or ((wav[at + 1].toInt() and 0xFF) shl 8) or
            ((wav[at + 2].toInt() and 0xFF) shl 16) or ((wav[at + 3].toInt() and 0xFF) shl 24)
        assertEquals(36 + 6, int32(4))
        assertEquals(16_000, int32(24))
        assertEquals(32_000, int32(28))
        assertEquals(6, int32(40))
        // little-endian samples: 1, -2, 256
        assertEquals(listOf<Byte>(1, 0, -2, -1, 0, 1), wav.drop(44))
    }

    @Test
    fun encodesOnlyTheRecordedPartOfABuffer() {
        assertEquals(44 + 4, Wav.encode(ShortArray(100), length = 2).size)
        assertEquals(1.5, Wav.seconds(24_000), 0.0)
    }
}

class EndpointTest {
    @Test
    fun completesABareHostTheWayTheDesktopAppDoes() {
        assertEquals("http://192.168.1.50:8787/v1", Endpoint.normalize("192.168.1.50"))
        assertEquals("http://homeserver:8787/v1", Endpoint.normalize("  homeserver/ "))
        assertEquals("http://homeserver:9000/v1", Endpoint.normalize("homeserver:9000"))
        assertEquals("https://box.tail1234.ts.net:8787/v1", Endpoint.normalize("box.tail1234.ts.net", encrypted = true))
        assertEquals("", Endpoint.normalize("   "))
    }

    @Test
    fun leavesACompleteAddressAlone() {
        assertEquals("https://voxctrl.example.com/v1", Endpoint.normalize("https://voxctrl.example.com"))
        assertEquals("http://10.0.0.2:8787/v1", Endpoint.normalize("http://10.0.0.2:8787/v1/"))
        assertEquals("http://host/custom/api", Endpoint.normalize("http://host/custom/api"))
    }

    @Test
    fun buildsTheUrlsItCalls() {
        val endpoint = "http://homeserver:8787/v1"
        assertEquals("http://homeserver:8787/v1/audio/transcriptions", Endpoint.transcriptionUrl(endpoint))
        assertEquals("http://homeserver:8787/v1/models", Endpoint.modelsUrl(endpoint))
        assertEquals("http://homeserver:8787/health", Endpoint.healthUrl(endpoint))
        assertEquals("http://h:1/v1/audio/transcriptions", Endpoint.transcriptionUrl("http://h:1"))
        assertEquals("http://h:1/v1/models", Endpoint.modelsUrl("http://h:1/v1/audio/transcriptions"))
    }

    @Test
    fun findsTheHost() {
        assertEquals("homeserver", Endpoint.hostOf("http://homeserver:8787/v1"))
        assertEquals("fd7a:115c:a1e0::1", Endpoint.hostOf("http://[fd7a:115c:a1e0::1]:8787/v1"))
        assertEquals("192.168.1.50", Endpoint.hostOf("192.168.1.50"))
        assertNull(Endpoint.hostOf(""))
        assertEquals("homeserver", Endpoint.displayName("http://homeserver:8787/v1"))
    }

    @Test
    fun knowsATailnetFromTheLocalNetworkFromTheInternet() {
        assertEquals(Endpoint.Network.TAILNET, Endpoint.networkOf("http://100.101.102.103:8787/v1"))
        assertEquals(Endpoint.Network.TAILNET, Endpoint.networkOf("http://box.tail1234.ts.net:8787/v1"))
        assertEquals(Endpoint.Network.TAILNET, Endpoint.networkOf("http://[fd7a:115c:a1e0::1]:8787/v1"))
        assertEquals(Endpoint.Network.LOCAL, Endpoint.networkOf("http://192.168.1.50:8787/v1"))
        assertEquals(Endpoint.Network.LOCAL, Endpoint.networkOf("http://10.1.2.3:8787/v1"))
        assertEquals(Endpoint.Network.LOCAL, Endpoint.networkOf("http://172.20.0.4:8787/v1"))
        assertEquals(Endpoint.Network.LOCAL, Endpoint.networkOf("http://homeserver:8787/v1"))
        assertEquals(Endpoint.Network.LOCAL, Endpoint.networkOf("http://nas.local:8787/v1"))
        assertEquals(Endpoint.Network.PUBLIC, Endpoint.networkOf("http://100.63.0.1:8787/v1"))
        assertEquals(Endpoint.Network.PUBLIC, Endpoint.networkOf("http://172.32.0.1:8787/v1"))
        assertEquals(Endpoint.Network.PUBLIC, Endpoint.networkOf("http://voxctrl.example.com:8787/v1"))
    }

    @Test
    fun warnsOnlyWhenAudioWouldCrossTheInternetUnencrypted() {
        assertTrue(Endpoint.sendsInTheClear("http://voxctrl.example.com:8787/v1"))
        assertFalse(Endpoint.sendsInTheClear("https://voxctrl.example.com/v1"))
        assertFalse(Endpoint.sendsInTheClear("http://100.101.102.103:8787/v1"))
        assertFalse(Endpoint.sendsInTheClear("http://192.168.1.50:8787/v1"))
    }
}

class PairingCodeTest {
    private fun code(json: String) =
        PairingCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private val fingerprint = "ab".repeat(32)

    @Test
    fun readsACodeFromAnEncryptedServer() {
        val pairing = PairingCode.decode(
            "  " + code("""{"h":"homeserver","p":8787,"k":"vx_secret","f":"${fingerprint.uppercase()}"}""") + "\n",
        )
        assertEquals(PairingCode("homeserver", 8787, "vx_secret", fingerprint), pairing)
        assertTrue(pairing.encrypted)
        assertEquals("https://homeserver:8787/v1", pairing.endpoint(""))
    }

    @Test
    fun aCodeWithoutAnAddressUsesTheOneTyped() {
        val pairing = PairingCode.decode(code("""{"p":9000,"k":"vx_secret"}"""))
        assertFalse(pairing.encrypted)
        assertEquals("http://100.101.102.103:9000/v1", pairing.endpoint("http://100.101.102.103:8787/v1"))
        assertEquals("http://[fd7a:115c:a1e0::1]:9000/v1", pairing.endpoint("[fd7a:115c:a1e0::1]"))
        assertThrows(PairingException::class.java) { pairing.endpoint("") }
    }

    @Test
    fun anAddressTheUserEnteredCanWinOverTheCodesOwn() {
        val pairing = PairingCode.decode(code("""{"h":"192.168.1.50","p":8787,"k":"vx_secret","f":"$fingerprint"}"""))
        // The desktop app's rule: the code's address.
        assertEquals("https://192.168.1.50:8787/v1", pairing.endpoint("box.tail1234.ts.net"))
        // The keyboard's rule: the tailnet address that was typed, with the code's port and scheme.
        assertEquals("https://box.tail1234.ts.net:8787/v1", pairing.endpoint("box.tail1234.ts.net", preferTyped = true))
        assertEquals("https://box.tail1234.ts.net:8787/v1", pairing.endpoint("http://box.tail1234.ts.net:9999/v1", preferTyped = true))
        assertEquals("https://192.168.1.50:8787/v1", pairing.endpoint("", preferTyped = true))
    }

    @Test
    fun refusesWhatIsNotAPairingCode() {
        assertThrows(PairingException::class.java) { PairingCode.decode("vx_3f9a") }
        assertThrows(PairingException::class.java) { PairingCode.decode(PairingCode.PREFIX + "!!not base64!!") }
        assertThrows(PairingException::class.java) { PairingCode.decode(code("""{"k":"no port"}""")) }
        assertThrows(PairingException::class.java) { PairingCode.decode(code("""{"p":0}""")) }
        assertThrows(PairingException::class.java) { PairingCode.decode(code("""{"p":8787,"f":"tooshort"}""")) }
        assertTrue(PairingCode.looksLikeOne(" vxpair_abc"))
        assertFalse(PairingCode.looksLikeOne("homeserver"))
    }

    @Test
    fun showsAFingerprintInPairs() {
        assertEquals("AB:AB:AB", PairingCode.displayFingerprint("ababab"))
    }
}

class CompatibilityTest {
    @Test
    fun comparesServerVersions() {
        assertTrue(Compatibility.supports("0.9.0"))
        assertTrue(Compatibility.supports("0.9.18"))
        assertTrue(Compatibility.supports("0.10.0"))
        assertTrue(Compatibility.supports("1.0"))
        assertFalse(Compatibility.supports("0.8.4"))
        assertTrue(Compatibility.supports(null))
        assertTrue(Compatibility.supports("nightly"))
    }
}

class TextJoinerTest {
    @Test
    fun addsASpaceOnlyWhereOneIsNeeded() {
        assertEquals("Hello there.", TextJoiner.join(null, " Hello there. "))
        assertEquals("Hello", TextJoiner.join("", "Hello"))
        assertEquals(" and then", TextJoiner.join("First part", "and then"))
        assertEquals("and then", TextJoiner.join("First part ", "and then"))
        assertEquals("and then", TextJoiner.join("First line\n", "and then"))
        assertEquals("quoted", TextJoiner.join("He said (", "quoted"))
        assertEquals(", right", TextJoiner.join("Okay", ", right"))
        assertEquals("", TextJoiner.join("Something", "   "))
    }
}

class LiveClipsTest {
    private val second = Wav.SAMPLE_RATE

    @Test
    fun sendsOneClipAtATimeOnceThereIsEnoughNewAudio() {
        assertFalse(LiveClips.shouldSend(second / 2, 0, requestInFlight = false))
        assertTrue(LiveClips.shouldSend(second, 0, requestInFlight = false))
        assertFalse(LiveClips.shouldSend(second, 0, requestInFlight = true))
        assertFalse(LiveClips.shouldSend(second + second / 4, second, requestInFlight = false))
        assertTrue(LiveClips.shouldSend(second * 2, second, requestInFlight = false))
    }

    @Test
    fun stopsLiveTextOnAVeryLongRecording() {
        assertFalse(LiveClips.shouldSend(second * 60, second * 40, requestInFlight = false))
    }
}
