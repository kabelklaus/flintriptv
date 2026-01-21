package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Credentials(
    val serverUrl: String,
    val username: String,
    val password: String
)

@Singleton
class CredentialsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "credentials_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _credentialsFlow = MutableStateFlow<Credentials?>(null)
    val credentialsFlow: StateFlow<Credentials?> = _credentialsFlow.asStateFlow()

    init {
        _credentialsFlow.value = getCredentials()
        sharedPreferences.registerOnSharedPreferenceChangeListener { _, _ ->
            _credentialsFlow.value = getCredentials()
        }
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }

    fun saveCredentials(credentials: Credentials) {
        sharedPreferences.edit()
            .putString(KEY_SERVER_URL, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
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
        return sharedPreferences.contains(KEY_SERVER_URL)
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    fun getCredentialsFlow(): Flow<Credentials?> = credentialsFlow
}

