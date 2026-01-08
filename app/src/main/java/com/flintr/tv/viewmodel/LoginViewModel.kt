package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.api.XtreamApiService
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.di.AppModule
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val credentialsRepository: CredentialsRepository,
    private val repository: XtreamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Bitte füllen Sie alle Felder aus"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Create temporary Retrofit instance for validation
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val moshi = Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .build()

                val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()

                val apiService = retrofit.create(XtreamApiService::class.java)

                // Validate credentials by calling getServerInfo
                val response = apiService.getServerInfo(username, password)
                
                if (response.isSuccessful && response.body() != null) {
                    // Save credentials
                    credentialsRepository.saveCredentials(
                        Credentials(serverUrl, username, password)
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loginSuccess = true
                    )
                    Log.d("LoginViewModel", "Login successful, credentials saved")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Ungültige Anmeldedaten (Code: ${response.code()})"
                    )
                    Log.e("LoginViewModel", "Login failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Verbindungsfehler: ${e.message}"
                )
                Log.e("LoginViewModel", "Login error", e)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetLoginSuccess() {
        _uiState.value = _uiState.value.copy(loginSuccess = false)
    }
}

