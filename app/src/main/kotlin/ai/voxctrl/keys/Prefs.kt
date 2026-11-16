package ai.voxctrl.keys

import ai.voxctrl.keys.core.BarLayout
import ai.voxctrl.keys.core.ServerConfig
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The keyboard's settings. Everything is in this app's private preferences;
 * the API key is additionally encrypted with a key held in the Android
 * Keystore, so a copy of the preferences file alone does not reveal it.
 * Nothing here is backed up (see the manifest and docs/privacy.md).
 */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voxctrl_keys", Context.MODE_PRIVATE)

    /** The server's `/v1` address, or empty before setup. */
    var endpoint: String
        get() = prefs.getString(ENDPOINT, "") ?: ""
        set(value) = prefs.edit().putString(ENDPOINT, value.trim()).apply()

    /** The paired server's certificate fingerprint; null when not paired with an encrypted server. */
    var fingerprint: String?
        get() = prefs.getString(FINGERPRINT, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(FINGERPRINT, value).apply()

    var apiKey: String?
        get() = prefs.getString(API_KEY, null)?.let { decrypt(it) }
        set(value) {
            val stored = value?.trim()?.takeIf { it.isNotEmpty() }?.let { encrypt(it) }
            prefs.edit().putString(API_KEY, stored).apply()
        }

    var liveText: Boolean
        get() = prefs.getBoolean(LIVE_TEXT, true)
        set(value) = prefs.edit().putBoolean(LIVE_TEXT, value).apply()

    /** A language code for the server, or empty for the server's own setting. */
    var language: String
        get() = prefs.getString(LANGUAGE, "") ?: ""
        set(value) = prefs.edit().putString(LANGUAGE, value.trim()).apply()

    /** Names and jargon sent as the recognition prompt with each request. */
    var vocabulary: String
        get() = prefs.getString(VOCABULARY, "") ?: ""
        set(value) = prefs.edit().putString(VOCABULARY, value.trim()).apply()

    var pillPosition: BarLayout.Position
        get() = when (prefs.getString(PILL_POSITION, null)) {
            "left" -> BarLayout.Position.LEFT
            "right" -> BarLayout.Position.RIGHT
            else -> BarLayout.Position.CENTER
        }
        set(value) = prefs.edit().putString(PILL_POSITION, value.name.lowercase()).apply()

    fun serverConfig() = ServerConfig(endpoint, apiKey, fingerprint)

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String? =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(sealed, Base64.NO_WRAP)
        } catch (_: Exception) {
            // A device whose keystore is unusable gets no saved key rather
            // than one stored in the clear; the user is asked to pair again.
            null
        }

    private fun decrypt(stored: String): String? =
        try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val ENDPOINT = "endpoint"
        const val FINGERPRINT = "fingerprint"
        const val API_KEY = "api_key_sealed"
        const val LIVE_TEXT = "live_text"
        const val LANGUAGE = "language"
        const val VOCABULARY = "vocabulary"
        const val PILL_POSITION = "pill_position"

        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "voxctrl_keys_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
