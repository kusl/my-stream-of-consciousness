package com.github.kusl.msoc.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.github.kusl.msoc.core.SettingsSource
import com.github.kusl.msoc.git.Remote
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Config(
    val url: String = "",
    val username: String = "",
    val token: String = "",
    val branch: String = "",
    val readOnly: Boolean = false,
    val authorName: String = "",
    val authorEmail: String = "",
)

class Settings(private val prefs: SharedPreferences, private val cipher: TokenCipher) : SettingsSource {
    fun load() = Config(
        url = str(URL),
        username = str(USER),
        token = prefs.getString(TOKEN, null)?.let(cipher::decrypt).orEmpty(),
        branch = str(BRANCH),
        readOnly = readOnly(),
        authorName = str(NAME),
        authorEmail = str(EMAIL),
    )

    fun save(config: Config) {
        prefs.edit()
            .putString(URL, config.url.trim())
            .putString(USER, config.username.trim())
            .putString(TOKEN, config.token.trim().takeIf { it.isNotEmpty() }?.let(cipher::encrypt))
            .putString(BRANCH, config.branch.trim())
            .putBoolean(READ_ONLY, config.readOnly)
            .putString(NAME, config.authorName.trim())
            .putString(EMAIL, config.authorEmail.trim())
            .apply()
    }

    var lastError: String?
        get() = prefs.getString(ERROR, null)
        set(value) {
            if (value != lastError) prefs.edit().putString(ERROR, value).apply()
        }

    override fun remote(): Remote? = load().takeIf { it.url.isNotEmpty() }?.let { Remote(it.url, it.branch, it.username, it.token) }

    override fun readOnly() = prefs.getBoolean(READ_ONLY, false)

    override fun author() = str(NAME).ifEmpty { "Stream of Consciousness" } to str(EMAIL).ifEmpty { "msoc@localhost" }

    private fun str(key: String) = prefs.getString(key, null).orEmpty()

    private companion object {
        const val URL = "url"
        const val USER = "username"
        const val TOKEN = "token"
        const val BRANCH = "branch"
        const val READ_ONLY = "read_only"
        const val NAME = "author_name"
        const val EMAIL = "author_email"
        const val ERROR = "last_error"
    }
}

class TokenCipher {
    private val store by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV)) }
        String(cipher.doFinal(bytes, IV, bytes.size - IV))
    }.getOrNull()

    private fun key(): SecretKey = store.getKey(ALIAS, null) as? SecretKey
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "remote-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = 12
    }
}
