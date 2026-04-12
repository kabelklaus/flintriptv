package com.flintr.tv.data.model

import java.time.Instant

data class FriendEntry(
    val username: String,
    val alias: String? = null,
    val addedAt: String
) {
    companion object {
        fun create(
            username: String,
            alias: String? = null
        ): FriendEntry {
            return FriendEntry(
                username = username,
                alias = alias,
                addedAt = Instant.now().toString()
            )
        }
    }
}

fun FriendEntry.toFirestoreMap(): Map<String, Any?> {
    return hashMapOf(
        "username" to username,
        "alias" to alias,
        "addedAt" to addedAt
    )
}
