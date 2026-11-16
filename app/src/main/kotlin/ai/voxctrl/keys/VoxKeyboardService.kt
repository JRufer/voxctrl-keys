package ai.voxctrl.keys

import ai.voxctrl.keys.core.Endpoint
import ai.voxctrl.keys.core.Failure
import ai.voxctrl.keys.core.LiveClips
import ai.voxctrl.keys.core.ServerClient
import ai.voxctrl.keys.core.ServerException
import ai.voxctrl.keys.core.TextJoiner
import ai.voxctrl.keys.core.Wav
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The keyboard: a short bar with a microphone button.
 *
 * A dictation goes: tap, [Recorder] fills a buffer in memory, tap again, the
 * buffer is sent to the user's VoxCtrl server as a WAV file, and the answer is
 * typed into the app. With live text on, what has been recorded so far is also
 * sent while the user speaks and shown as composing (underlined) text, which
 * the final answer replaces.
 *
 * The microphone is only ever open between those two taps, and it is closed
 * at once if the keyboard is hidden. See docs/architecture.md and
 * docs/privacy.md.
 */
class VoxKeyboardService : InputMethodService() {
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())

    /** One thread: requests to the server run one at a time, as the server handles them. */
    private val network: ExecutorService = Executors.newSingleThreadExecutor()

    private var bar: KeyboardBar? = null
    private var foldWatcher: FoldWatcher? = null
    private var state: BarState = BarState.Setup

    private var recorder: Recorder? = null

    /**
     * Goes up whenever a dictation starts, is discarded or is abandoned. An
     * answer from the server is used only if this still has the value it had
     * when the request was made, so a late answer can never type into a
     * different field.
     */
    private var generation = 0
    private var liveInFlight = false
    private var lastLiveSamples = 0

    /** The character before the cursor when the dictation began, for spacing. */
    private var textBefore: CharSequence? = null

    /** A finished recording the server did not answer for, kept in memory for Retry. */
    private var heldAudio: ByteArray? = null

    private var online: Boolean? = null
    private var lastProbeAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_bar, null)
        val created = KeyboardBar(
            root = view,
            prefs = prefs,
            onPill = ::onPillTapped,
            onSwitch = ::switchKeyboard,
            onSwitchLong = ::showKeyboardPicker,
            onRight = ::discard,
            onBackspace = ::backspace,
        )
        bar = created
        foldWatcher?.stop()
        foldWatcher = FoldWatcher(this) { created.setFold(it) }.also { it.start() }
        created.show(state)
        return view
    }

    /** The bar is never full-screen: in landscape it stays one row under the app. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        bar?.refreshLayout()
        if (state !is BarState.Listening && state !is BarState.Finishing) {
            show(restingState())
            probe(force = false)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        abandon()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        abandon()
        foldWatcher?.stop()
        network.shutdownNow()
        super.onDestroy()
    }

    // ── Taps ──────────────────────────────────────────────────────────────────

    private fun onPillTapped() {
        haptic()
        when (val current = state) {
            is BarState.Setup, is BarState.Problem -> openSettings(requestMic = false)
            is BarState.NeedsMic -> openSettings(requestMic = true)
            is BarState.Idle -> startListening()
            is BarState.Listening -> finishListening()
            is BarState.Unreachable -> {
                val audio = heldAudio
                if (current.holdsAudio && audio != null) {
                    show(BarState.Finishing)
                    upload(audio)
                } else {
                    show(BarState.Idle(serverName(), null))
                    probe(force = true)
                }
            }
            is BarState.Finishing, is BarState.Notice -> Unit
        }
    }

    private fun switchKeyboard() {
        haptic()
        abandon()
        if (!switchToPreviousInputMethod()) showKeyboardPicker()
    }

    private fun showKeyboardPicker() {
        haptic()
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    private fun backspace() {
        haptic()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    // ── A dictation ───────────────────────────────────────────────────────────

    private fun startListening() {
        if (!prefs.serverConfig().configured) {
            show(BarState.Setup)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            show(BarState.NeedsMic)
            return
        }
        val session = ++generation
        val started = Recorder(
            onChunk = { total, level -> main.post { onChunk(session, total, level) } },
            onLimit = { main.post { if (session == generation && state is BarState.Listening) finishListening() } },
        )
        if (!started.start()) {
            show(BarState.Notice(getString(R.string.mic_unavailable)))
            main.postDelayed({ if (state is BarState.Notice) show(restingState()) }, NOTICE_MS)
            return
        }
        recorder = started
        heldAudio = null
        liveInFlight = false
        lastLiveSamples = 0
        textBefore = currentInputConnection?.getTextBeforeCursor(1, 0)
        show(BarState.Listening)
    }

    private fun onChunk(session: Int, totalSamples: Int, level: Float) {
        if (session != generation || state !is BarState.Listening) return
        bar?.pushLevel(level)
        bar?.setSeconds(totalSamples / Wav.SAMPLE_RATE)
        if (prefs.liveText && LiveClips.shouldSend(totalSamples, lastLiveSamples, liveInFlight)) {
            sendLiveClip(session, totalSamples)
        }
    }

    /** Sends what has been said so far and shows the answer as composing text. */
    private fun sendLiveClip(session: Int, totalSamples: Int) {
        val wav = recorder?.snapshotWav() ?: return
        liveInFlight = true
        lastLiveSamples = totalSamples
        val client = ServerClient(prefs.serverConfig())
        val prompt = prefs.vocabulary
        val language = prefs.language
        network.execute {
            val text = try {
                client.transcribe(wav, prompt, language)
            } catch (_: Exception) {
                null // live text is a preview; the final upload reports problems
            }
            main.post {
                if (session != generation) return@post
                liveInFlight = false
                if (state is BarState.Listening && !text.isNullOrEmpty()) {
                    currentInputConnection?.setComposingText(TextJoiner.join(textBefore, text), 1)
                }
            }
        }
    }

    private fun finishListening() {
        val recording = recorder ?: return
        recording.stop()
        recorder = null
        if (recording.samples < MIN_SAMPLES) {
            // A slip of the thumb, not a dictation.
            clearComposing()
            show(restingState())
            return
        }
        show(BarState.Finishing)
        upload(recording.snapshotWav())
    }

    /** Sends a finished recording and types the answer. */
    private fun upload(wav: ByteArray) {
        val session = generation
        val client = ServerClient(prefs.serverConfig())
        val prompt = prefs.vocabulary
        val language = prefs.language
        network.execute {
            val outcome: Any = try {
                client.transcribe(wav, prompt, language)
            } catch (e: ServerException) {
                e
            } catch (e: Exception) {
                ServerException(Failure.SERVER_ERROR, e.message ?: "", e)
            }
            main.post {
                if (session != generation) return@post
                when {
                    outcome is String -> onTranscribed(outcome)
                    outcome is ServerException && outcome.failure == Failure.UNREACHABLE -> {
                        // Keep the recording, and any live text on screen, for Retry.
                        heldAudio = wav
                        online = false
                        show(BarState.Unreachable(serverName(), holdsAudio = true))
                    }
                    outcome is ServerException -> {
                        heldAudio = null
                        // Whatever live text is showing stays, as ordinary text.
                        currentInputConnection?.finishComposingText()
                        show(problemFor(outcome))
                    }
                }
            }
        }
    }

    private fun onTranscribed(text: String) {
        heldAudio = null
        online = true
        lastProbeAt = SystemClock.elapsedRealtime()
        val connection = currentInputConnection
        if (text.isEmpty()) {
            clearComposing()
            show(BarState.Notice(getString(R.string.nothing_heard)))
            main.postDelayed({ if (state is BarState.Notice) show(restingState()) }, NOTICE_MS)
            return
        }
        // Replaces the composing (live) text, if any, with the final words.
        connection?.commitText(TextJoiner.join(textBefore, text), 1)
        show(restingState())
    }

    /** The right button while listening, or with a recording waiting: throw it away. */
    private fun discard() {
        haptic()
        generation++
        recorder?.stop()
        recorder = null
        heldAudio = null
        liveInFlight = false
        clearComposing()
        show(restingState())
    }

    /**
     * The keyboard is going away. The microphone closes now, and nothing that
     * is still on its way back from the server will be typed, because the
     * field it belonged to may be gone. Live text already on screen stays.
     */
    private fun abandon() {
        if (recorder == null && heldAudio == null && state !is BarState.Finishing) return
        generation++
        recorder?.stop()
        recorder = null
        heldAudio = null
        liveInFlight = false
        currentInputConnection?.finishComposingText()
        state = restingState()
        bar?.show(state)
    }

    private fun clearComposing() {
        currentInputConnection?.let {
            it.setComposingText("", 1)
            it.finishComposingText()
        }
    }

    // ── The server's status ───────────────────────────────────────────────────

    /** Asks the server whether it is there, at most every [PROBE_INTERVAL_MS] unless [force]d. */
    private fun probe(force: Boolean) {
        val config = prefs.serverConfig()
        if (!config.configured) return
        val now = SystemClock.elapsedRealtime()
        if (!force && online == true && now - lastProbeAt < PROBE_INTERVAL_MS) return
        lastProbeAt = now
        network.execute {
            val failure: ServerException? = try {
                ServerClient(config, readTimeoutMs = PROBE_READ_TIMEOUT_MS).probe()
                null
            } catch (e: ServerException) {
                e
            } catch (e: Exception) {
                ServerException(Failure.UNREACHABLE, e.message ?: "", e)
            }
            main.post {
                online = failure == null
                // Only a bar at rest is updated; a dictation in progress finds out for itself.
                val atRest = state is BarState.Idle || state is BarState.Problem ||
                    (state as? BarState.Unreachable)?.holdsAudio == false
                if (atRest) {
                    show(
                        when {
                            failure == null -> restingState()
                            failure.failure == Failure.UNREACHABLE -> BarState.Unreachable(serverName(), holdsAudio = false)
                            else -> problemFor(failure)
                        },
                    )
                }
            }
        }
    }

    private fun problemFor(failure: ServerException): BarState = when (failure.failure) {
        Failure.NOT_CONFIGURED -> BarState.Setup
        Failure.UNREACHABLE -> BarState.Unreachable(serverName(), holdsAudio = false)
        else -> BarState.Problem(getString(R.string.check_settings))
    }

    /** What the bar shows when nothing is happening. */
    private fun restingState(): BarState = when {
        !prefs.serverConfig().configured -> BarState.Setup
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> BarState.NeedsMic
        else -> BarState.Idle(serverName(), online)
    }

    private fun serverName() = Endpoint.displayName(prefs.endpoint)

    private fun show(next: BarState) {
        state = next
        bar?.show(next)
    }

    private fun openSettings(requestMic: Boolean) {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SettingsActivity.EXTRA_REQUEST_MIC, requestMic),
        )
    }

    private fun haptic() {
        bar?.root?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private companion object {
        /** Shorter than this is a mis-tap: a third of a second. */
        const val MIN_SAMPLES = Wav.SAMPLE_RATE / 3
        const val PROBE_INTERVAL_MS = 15_000L
        const val PROBE_READ_TIMEOUT_MS = 6_000
        const val NOTICE_MS = 1_600L
    }
}
