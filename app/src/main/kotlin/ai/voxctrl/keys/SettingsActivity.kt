package ai.voxctrl.keys

import ai.voxctrl.keys.core.BarLayout
import ai.voxctrl.keys.core.Compatibility
import ai.voxctrl.keys.core.Endpoint
import ai.voxctrl.keys.core.KeyboardPlacement
import ai.voxctrl.keys.core.PairingCode
import ai.voxctrl.keys.core.PairingException
import ai.voxctrl.keys.core.ServerClient
import ai.voxctrl.keys.core.ServerException
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * The app's one screen: turn the keyboard on, allow the microphone, point it
 * at a VoxCtrl server, and try it. It is also what the keyboard opens when
 * something needs fixing.
 */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private val network = Executors.newSingleThreadExecutor()

    private lateinit var keyboardStatus: TextView
    private lateinit var micStatus: TextView
    private lateinit var micButton: Button
    private lateinit var address: EditText
    private lateinit var pairing: EditText
    private lateinit var apiKey: EditText
    private lateinit var unpairButton: Button
    private lateinit var serverStatus: TextView
    private lateinit var networkNote: TextView

    /** True once the user has refused the microphone in a way the system will not ask again about. */
    private var micRefused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)
        fitToWindow()

        keyboardStatus = findViewById(R.id.keyboard_status)
        micStatus = findViewById(R.id.mic_status)
        micButton = findViewById(R.id.btn_mic)
        address = findViewById(R.id.edit_address)
        pairing = findViewById(R.id.edit_pairing)
        apiKey = findViewById(R.id.edit_key)
        unpairButton = findViewById(R.id.btn_unpair)
        serverStatus = findViewById(R.id.server_status)
        networkNote = findViewById(R.id.network_note)

        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_choose).setOnClickListener {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        micButton.setOnClickListener { askForMicrophone() }
        findViewById<Button>(R.id.btn_pair).setOnClickListener { pair() }
        findViewById<Button>(R.id.btn_test).setOnClickListener { saveAndTest() }
        unpairButton.setOnClickListener { removePairing() }

        address.setText(prefs.endpoint)
        apiKey.setText(prefs.apiKey.orEmpty())
        serverStatus.setText(if (prefs.endpoint.isEmpty()) R.string.not_set_up else R.string.saved_not_tested)

        val live = findViewById<Switch>(R.id.switch_live)
        live.isChecked = prefs.liveText
        live.setOnCheckedChangeListener { _, checked -> prefs.liveText = checked }

        val language = findViewById<EditText>(R.id.edit_language)
        language.setText(prefs.language)
        language.setOnFocusChangeListener { _, focused -> if (!focused) prefs.language = language.text.toString() }

        val vocabulary = findViewById<EditText>(R.id.edit_vocabulary)
        vocabulary.setText(prefs.vocabulary)
        vocabulary.setOnFocusChangeListener { _, focused -> if (!focused) prefs.vocabulary = vocabulary.text.toString() }

        val position = findViewById<Spinner>(R.id.spinner_position)
        position.setSelection(prefs.pillPosition.ordinal)
        position.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, index: Int, id: Long) {
                prefs.pillPosition = BarLayout.Position.values()[index]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val hand = findViewById<Spinner>(R.id.spinner_hand)
        hand.setSelection(prefs.hand.ordinal)
        hand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, index: Int, id: Long) {
                prefs.hand = KeyboardPlacement.Hand.values()[index]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<TextView>(R.id.version_line).text = getString(
            R.string.version_line,
            packageManager.getPackageInfo(packageName, 0).versionName ?: "",
            Compatibility.PROTOCOL_REVISION,
        )

        showNetworkNote()
        if (intent?.getBooleanExtra(EXTRA_REQUEST_MIC, false) == true && !hasMicrophone()) askForMicrophone()
    }

    override fun onResume() {
        super.onResume()
        showKeyboardStatus()
        showMicStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Coming back from the keyboard picker changes focus but does not resume.
        if (hasFocus) showKeyboardStatus()
    }

    override fun onPause() {
        // Fields that save on losing focus may still have it when the app is left.
        prefs.language = findViewById<EditText>(R.id.edit_language).text.toString()
        prefs.vocabulary = findViewById<EditText>(R.id.edit_vocabulary).text.toString()
        super.onPause()
    }

    override fun onDestroy() {
        network.shutdownNow()
        super.onDestroy()
    }

    // ── Keyboard and microphone ───────────────────────────────────────────────

    private fun showKeyboardStatus() {
        val manager = getSystemService(InputMethodManager::class.java)
        val enabled = manager?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val selected = current?.startsWith("$packageName/") == true
        keyboardStatus.setText(
            when {
                selected -> R.string.keyboard_ready
                enabled -> R.string.keyboard_enabled_not_selected
                else -> R.string.keyboard_not_enabled
            },
        )
    }

    private fun hasMicrophone() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun showMicStatus() {
        val granted = hasMicrophone()
        micStatus.setText(
            when {
                granted -> R.string.mic_granted
                micRefused -> R.string.mic_denied
                else -> R.string.mic_missing
            },
        )
        micButton.visibility = if (granted) View.GONE else View.VISIBLE
        micButton.setText(if (micRefused) R.string.app_settings else R.string.allow)
    }

    private fun askForMicrophone() {
        if (micRefused) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MIC) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        // Refused with no dialog left to show: only the system settings can change it now.
        micRefused = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        showMicStatus()
    }

    // ── Server ────────────────────────────────────────────────────────────────

    /** Reads a pairing code into the address, key and certificate, then tests the result. */
    private fun pair() {
        val code = try {
            PairingCode.decode(pairing.text.toString())
        } catch (e: PairingException) {
            serverStatus.text = e.message
            return
        }
        val endpoint = try {
            code.endpoint(address.text.toString(), preferTyped = true)
        } catch (e: PairingException) {
            serverStatus.text = e.message
            address.requestFocus()
            return
        }
        prefs.endpoint = endpoint
        prefs.fingerprint = code.fingerprint
        // A server that needs no key leaves it out of the code.
        prefs.apiKey = code.apiKey
        address.setText(endpoint)
        apiKey.setText(code.apiKey.orEmpty())
        pairing.setText("")
        serverStatus.setText(R.string.paired)
        showNetworkNote()
        test()
    }

    private fun saveAndTest() {
        // A pairing code pasted into the address box is still a pairing code.
        if (PairingCode.looksLikeOne(address.text.toString())) {
            pairing.setText(address.text.toString().trim())
            address.setText(prefs.endpoint)
            pair()
            return
        }
        val endpoint = Endpoint.normalize(address.text.toString(), encrypted = prefs.fingerprint != null)
        prefs.endpoint = endpoint
        prefs.apiKey = apiKey.text.toString()
        address.setText(endpoint)
        showNetworkNote()
        if (endpoint.isEmpty()) {
            serverStatus.setText(R.string.not_set_up)
            return
        }
        test()
    }

    private fun removePairing() {
        prefs.fingerprint = null
        serverStatus.setText(R.string.pairing_removed)
        showNetworkNote()
    }

    private fun test() {
        val config = prefs.serverConfig()
        serverStatus.setText(R.string.testing)
        network.execute {
            val message = try {
                val info = ServerClient(config).probe()
                val unknown = getString(R.string.unknown)
                if (!Compatibility.speaks(info.contract)) {
                    getString(R.string.keyboard_too_old, info.version ?: unknown)
                } else if (!Compatibility.supports(info.version)) {
                    getString(R.string.server_too_old, info.version ?: unknown, Compatibility.MIN_SERVER_VERSION)
                } else {
                    getString(
                        R.string.connected,
                        Endpoint.displayName(config.endpoint),
                        info.model ?: unknown,
                        info.version ?: unknown,
                    )
                }
            } catch (e: ServerException) {
                e.message ?: getString(R.string.cant_reach_server)
            } catch (e: Exception) {
                getString(R.string.cant_reach_server)
            }
            runOnUiThread { if (!isDestroyed) serverStatus.text = message }
        }
    }

    /** Says, for the saved address, whether the audio is protected on its way to the server. */
    private fun showNetworkNote() {
        val endpoint = prefs.endpoint
        val fingerprint = prefs.fingerprint
        unpairButton.visibility = if (fingerprint != null) View.VISIBLE else View.GONE
        networkNote.visibility = if (endpoint.isEmpty()) View.GONE else View.VISIBLE
        if (endpoint.isEmpty()) return
        val warn = Endpoint.sendsInTheClear(endpoint)
        networkNote.text = when {
            fingerprint != null -> getString(R.string.net_encrypted, PairingCode.displayFingerprint(fingerprint).take(23) + "…")
            Endpoint.isHttps(endpoint) -> getString(R.string.net_https)
            warn -> getString(R.string.net_public)
            Endpoint.networkOf(endpoint) == Endpoint.Network.TAILNET -> getString(R.string.net_tailnet)
            else -> getString(R.string.net_local)
        }
        networkNote.setTextColor(
            if (warn) getColor(R.color.warn_on_surface) else serverStatus.currentTextColor,
        )
    }

    /** Keeps the form clear of the system bars and the keyboard, and readable on a wide screen. */
    private fun fitToWindow() {
        val root = findViewById<View>(R.id.settings_root)
        val column = findViewById<View>(R.id.settings_column)
        val density = resources.displayMetrics.density
        val side = (20 * density).toInt()
        val maxWidth = (640 * density).toInt()
        root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val available = right - left - root.paddingLeft - root.paddingRight
            val pad = maxOf(side, (available - maxWidth) / 2)
            if (column.paddingLeft != pad) {
                column.post { column.setPadding(pad, column.paddingTop, pad, column.paddingBottom) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime(),
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        } else {
            root.fitsSystemWindows = true
        }
    }

    companion object {
        /** Set by the keyboard when it was tapped without microphone permission. */
        const val EXTRA_REQUEST_MIC = "request_mic"
        private const val REQUEST_MIC = 1
    }
}
