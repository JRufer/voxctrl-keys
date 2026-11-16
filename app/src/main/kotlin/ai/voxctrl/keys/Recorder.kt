package ai.voxctrl.keys

import ai.voxctrl.keys.core.Wav
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * Records the microphone into memory as 16 kHz mono 16-bit PCM, the format
 * the server takes without converting.
 *
 * The audio exists only in this object's buffer. It is never written to
 * storage, and [stop] followed by dropping the object is all it takes to
 * forget it.
 *
 * [onChunk] is called on the recording thread about ten times a second with
 * the number of samples so far and the loudness of the latest chunk (0 to 1).
 * [onLimit] is called once, on the same thread, if [MAX_SAMPLES] is reached.
 */
class Recorder(
    private val onChunk: (totalSamples: Int, level: Float) -> Unit,
    private val onLimit: () -> Unit,
) {
    private val lock = Any()
    private var buffer = ShortArray(Wav.SAMPLE_RATE * 15)
    private var size = 0

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    val samples: Int get() = synchronized(lock) { size }

    /** Starts recording. False when the microphone could not be opened. */
    @SuppressLint("MissingPermission") // the caller checks RECORD_AUDIO before starting
    fun start(): Boolean {
        val minimum = AudioRecord.getMinBufferSize(
            Wav.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimum <= 0) return false
        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                Wav.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, Wav.SAMPLE_RATE), // room for half a second
            )
        } catch (_: Exception) {
            return false
        }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            return false
        }
        try {
            audio.startRecording()
        } catch (_: Exception) {
            audio.release()
            return false
        }
        if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            audio.release()
            return false
        }
        record = audio
        running = true
        thread = Thread({ loop(audio) }, "voxctrl-recorder").also { it.start() }
        return true
    }

    /** Stops recording and waits for the last chunk. Safe to call twice. */
    fun stop() {
        running = false
        thread?.let {
            if (it !== Thread.currentThread()) {
                try {
                    it.join(1000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        record = null
    }

    /** Everything recorded so far, as a WAV file. */
    fun snapshotWav(): ByteArray = synchronized(lock) { Wav.encode(buffer, size) }

    private fun loop(audio: AudioRecord) {
        val chunk = ShortArray(Wav.SAMPLE_RATE / 10)
        while (running) {
            val read = audio.read(chunk, 0, chunk.size)
            if (read <= 0) {
                if (read < 0) break
                continue
            }
            var total = 0
            var full = false
            synchronized(lock) {
                val take = minOf(read, MAX_SAMPLES - size)
                if (size + take > buffer.size) {
                    buffer = buffer.copyOf(minOf(MAX_SAMPLES, maxOf(buffer.size * 2, size + take)))
                }
                System.arraycopy(chunk, 0, buffer, size, take)
                size += take
                total = size
                full = size >= MAX_SAMPLES
            }
            onChunk(total, level(chunk, read))
            if (full) {
                running = false
                onLimit()
            }
        }
    }

    private fun level(chunk: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val s = chunk[i].toDouble()
            sum += s * s
        }
        val rms = sqrt(sum / length) / Short.MAX_VALUE
        // Speech sits low on a linear scale; a square root spreads it out.
        return sqrt(rms * 6.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        /** Five minutes: about 10 MB as a WAV, well inside the server's default upload limit. */
        const val MAX_SAMPLES = Wav.SAMPLE_RATE * 60 * 5
    }
}
