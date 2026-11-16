package ai.voxctrl.keys.core

/**
 * WAV encoding for what the keyboard records: 16 kHz, mono, 16-bit PCM. That
 * is the format the VoxCtrl desktop app sends and the one the server takes
 * without converting (see docs/protocol.md).
 */
object Wav {
    const val SAMPLE_RATE = 16_000
    private const val HEADER_BYTES = 44

    /** The first [length] samples of [pcm] as a complete WAV file. */
    fun encode(pcm: ShortArray, length: Int = pcm.size, sampleRate: Int = SAMPLE_RATE): ByteArray {
        require(length in 0..pcm.size) { "length is outside the buffer" }
        val dataBytes = length * 2
        val out = ByteArray(HEADER_BYTES + dataBytes)
        ascii(out, 0, "RIFF")
        int32(out, 4, 36 + dataBytes)
        ascii(out, 8, "WAVE")
        ascii(out, 12, "fmt ")
        int32(out, 16, 16) // fmt chunk size
        int16(out, 20, 1) // PCM
        int16(out, 22, 1) // mono
        int32(out, 24, sampleRate)
        int32(out, 28, sampleRate * 2) // byte rate
        int16(out, 32, 2) // block align
        int16(out, 34, 16) // bits per sample
        ascii(out, 36, "data")
        int32(out, 40, dataBytes)
        var at = HEADER_BYTES
        for (i in 0 until length) {
            val s = pcm[i].toInt()
            out[at++] = (s and 0xFF).toByte()
            out[at++] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** How long [samples] samples last, in seconds. */
    fun seconds(samples: Int, sampleRate: Int = SAMPLE_RATE): Double = samples.toDouble() / sampleRate

    private fun ascii(out: ByteArray, at: Int, text: String) {
        for (i in text.indices) out[at + i] = text[i].code.toByte()
    }

    private fun int32(out: ByteArray, at: Int, value: Int) {
        out[at] = (value and 0xFF).toByte()
        out[at + 1] = ((value shr 8) and 0xFF).toByte()
        out[at + 2] = ((value shr 16) and 0xFF).toByte()
        out[at + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun int16(out: ByteArray, at: Int, value: Int) {
        out[at] = (value and 0xFF).toByte()
        out[at + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
