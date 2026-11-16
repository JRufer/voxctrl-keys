package ai.voxctrl.keys.core

/**
 * When to send the server a clip for live text.
 *
 * Live text works the way the desktop app's does with a remote engine: while
 * the user is speaking, the client sends what has been recorded so far to the
 * ordinary transcription endpoint, waits for the answer, shows it, and sends
 * the next. One request is in flight at a time, so the load follows how fast
 * the server answers.
 */
object LiveClips {
    /** New audio needed since the last clip before another is worth sending. */
    const val MIN_NEW_SAMPLES = Wav.SAMPLE_RATE * 6 / 10 // 0.6 s

    /** Nothing is sent until there is this much audio: shorter clips transcribe to noise. */
    const val MIN_FIRST_SAMPLES = Wav.SAMPLE_RATE * 8 / 10 // 0.8 s

    /**
     * Past this much audio live text stops updating and the words appear when
     * the user stops: re-sending a long recording several times a second would
     * load the server for little gain.
     */
    const val MAX_LIVE_SAMPLES = Wav.SAMPLE_RATE * 45 // 45 s

    fun shouldSend(recordedSamples: Int, lastSentSamples: Int, requestInFlight: Boolean): Boolean {
        if (requestInFlight) return false
        if (recordedSamples < MIN_FIRST_SAMPLES || recordedSamples > MAX_LIVE_SAMPLES) return false
        return recordedSamples - lastSentSamples >= MIN_NEW_SAMPLES
    }
}
