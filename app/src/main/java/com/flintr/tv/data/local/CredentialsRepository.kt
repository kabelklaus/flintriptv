package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class Credentials(
    val serverUrl: String,
    val username: String,
    val password: String
)

data class LoginPrefill(
    val username: String = "",
    val password: String = ""
)

@Singleton
class CredentialsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Lazy: MasterKey generation and EncryptedSharedPreferences creation are file I/O +
    // Keystore operations. Deferring them until first access keeps Hilt's synchronous
    // constructor call (which happens on the main thread) from ANR-ing.
    private val sharedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "credentials_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { prefs ->
            prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                _credentialsFlow.value = getCredentials()
            }
        }
    }

    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _credentialsFlow = MutableStateFlow<Credentials?>(null)
    val credentialsFlow: StateFlow<Credentials?> = _credentialsFlow.asStateFlow()

    init {
        // Touches the lazy sharedPreferences (I/O + Keystore) off the main thread and
        // fills the flow once ready; synchronous getCredentials() callers still work,
        // they just pay the init cost on their own thread on first access.
        warmupScope.launch {
            _credentialsFlow.value = getCredentials()
        }
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PREFILL_USERNAME = "prefill_username"
        private const val KEY_PREFILL_PASSWORD = "prefill_password"
    }

    fun saveCredentials(credentials: Credentials) {
        sharedPreferences.edit {
            putString(KEY_SERVER_URL, credentials.serverUrl)
            putString(KEY_USERNAME, credentials.username)
            putString(KEY_PASSWORD, credentials.password)
            putString(KEY_PREFILL_USERNAME, credentials.username)
            putString(KEY_PREFILL_PASSWORD, credentials.password)
        }
    }

    fun getCredentials(): Credentials? {
        val serverUrl = sharedPreferences.getString(KEY_SERVER_URL, null)
        val username = sharedPreferences.getString(KEY_USERNAME, null)
        val password = sharedPreferences.getString(KEY_PASSWORD, null)

        return if (serverUrl != null && username != null && password != null) {
            Credentials(serverUrl, username, password)
        } else {
            null
        }
    }

    fun hasCredentials(): Boolean {
        val credentials = _credentialsFlow.value
        return credentials != null &&
            credentials.serverUrl.isNotBlank() &&
            credentials.username.isNotBlank() &&
            credentials.password.isNotBlank()
    }

    fun getLoginPrefill(): LoginPrefill {
        val username = sharedPreferences.getString(KEY_PREFILL_USERNAME, null)
            ?: sharedPreferences.getString(KEY_USERNAME, null) ?: ""
        val password = sharedPreferences.getString(KEY_PREFILL_PASSWORD, null)
            ?: sharedPreferences.getString(KEY_PASSWORD, null) ?: ""

        return LoginPrefill(
            username = username,
            password = password
        )
    }

    fun clearActiveCredentials() {
        sharedPreferences.edit {
            remove(KEY_SERVER_URL)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
        }
    }

    fun clearCredentials() {
        sharedPreferences.edit { clear() }
    }
}
