package com.flintr.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.model.FriendEntry
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.ui.settings.FriendValidationError
import com.flintr.tv.ui.settings.normalizeFriendAlias
import com.flintr.tv.ui.settings.normalizeFriendUsername
import com.flintr.tv.ui.settings.validateFriendCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsFriendsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val currentUsername: String? = null,
    val friends: List<FriendEntry> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SettingsFriendsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsFriendsUiState(isLoading = true))
    val uiState: StateFlow<SettingsFriendsUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
    }

    fun loadFriends() {
        val currentUsername = credentialsRepository.getCredentials()?.username
        if (currentUsername.isNullOrBlank()) {
            _uiState.value = SettingsFriendsUiState(
                isLoading = false,
                errorMessage = "Kein aktiver Benutzer gefunden"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                currentUsername = currentUsername,
                errorMessage = null,
                successMessage = null
            )

            val result = userRepository.loadFriends(currentUsername)
            result.fold(
                onSuccess = { friends ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentUsername = currentUsername,
                        friends = friends,
                        errorMessage = null
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentUsername = currentUsername,
                        errorMessage = "Freundesliste konnte nicht geladen werden"
                    )
                }
            )
        }
    }

    fun addFriend(
        usernameInput: String,
        aliasInput: String
    ) {
        val currentUsername = credentialsRepository.getCredentials()?.username
        if (currentUsername.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Kein aktiver Benutzer gefunden")
            return
        }

        val normalizedUsername = normalizeFriendUsername(usernameInput)
        val normalizedAlias = normalizeFriendAlias(aliasInput)

        val validationError = validateFriendCandidate(
            candidateUsername = normalizedUsername,
            currentUsername = currentUsername,
            existingFriends = _uiState.value.friends
        )
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError.toMessage(), successMessage = null)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, successMessage = null)

            val usernameExists = userRepository.usernameExists(normalizedUsername)
            val exists = usernameExists.getOrElse {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Benutzername konnte nicht geprüft werden"
                )
                return@launch
            }

            if (!exists) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Dieser Benutzername existiert nicht"
                )
                return@launch
            }

            val result = userRepository.addFriendAtomically(
                username = currentUsername,
                friendEntry = FriendEntry.create(
                    username = normalizedUsername,
                    alias = normalizedAlias
                )
            )

            result.fold(
                onSuccess = {
                    val updatedFriends = userRepository.loadFriends(currentUsername).getOrDefault(_uiState.value.friends)
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        friends = updatedFriends,
                        successMessage = "Freund wurde hinzugefügt",
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = mapAddFriendError(error),
                        successMessage = null
                    )
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun FriendValidationError.toMessage(): String {
        return when (this) {
            FriendValidationError.EMPTY_USERNAME -> "Bitte einen Benutzernamen eingeben"
            FriendValidationError.OWN_USERNAME -> "Der eigene Benutzername kann nicht hinzugefügt werden"
            FriendValidationError.DUPLICATE -> "Dieser Freund wurde bereits hinzugefügt"
        }
    }

    private fun mapAddFriendError(error: Throwable): String {
        return when (error.message) {
            "Friend already exists" -> "Dieser Freund wurde bereits hinzugefügt"
            "Cannot add yourself as friend" -> "Der eigene Benutzername kann nicht hinzugefügt werden"
            else -> "Freund konnte nicht gespeichert werden"
        }
    }
}
