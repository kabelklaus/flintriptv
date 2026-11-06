package com.flintr.tv.ui.settings

import com.flintr.tv.data.model.FriendEntry

internal enum class FriendValidationError {
    EMPTY_USERNAME,
    OWN_USERNAME,
    DUPLICATE
}

internal fun normalizeFriendUsername(input: String): String {
    return input.trim()
}

internal fun normalizeFriendAlias(input: String): String? {
    return input.trim().takeIf { it.isNotBlank() }
}

internal fun validateFriendCandidate(
    candidateUsername: String,
    currentUsername: String,
    existingFriends: List<FriendEntry>
): FriendValidationError? {
    if (candidateUsername.isBlank()) {
        return FriendValidationError.EMPTY_USERNAME
    }

    if (candidateUsername == currentUsername) {
        return FriendValidationError.OWN_USERNAME
    }

    if (existingFriends.any { it.username == candidateUsername }) {
        return FriendValidationError.DUPLICATE
    }

    return null
}
