package com.flintr.tv.ui.settings

import com.flintr.tv.data.model.FriendEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendsSupportTest {

    @Test
    fun `normalize friend fields trims username and clears blank alias`() {
        assertEquals("thomas_8472", normalizeFriendUsername("  thomas_8472  "))
        assertNull(normalizeFriendAlias("   "))
        assertEquals("Thomas", normalizeFriendAlias("  Thomas "))
    }

    @Test
    fun `validate friend candidate rejects empty self and duplicate`() {
        val existingFriends = listOf(
            FriendEntry(
                username = "thomas_8472",
                alias = "Thomas",
                addedAt = "2026-04-12T10:30:00Z"
            )
        )

        assertEquals(
            FriendValidationError.EMPTY_USERNAME,
            validateFriendCandidate("", "christopher", existingFriends)
        )
        assertEquals(
            FriendValidationError.OWN_USERNAME,
            validateFriendCandidate("christopher", "christopher", existingFriends)
        )
        assertEquals(
            FriendValidationError.DUPLICATE,
            validateFriendCandidate("thomas_8472", "christopher", existingFriends)
        )
    }

    @Test
    fun `validate friend candidate accepts new username`() {
        val result = validateFriendCandidate(
            candidateUsername = "anna_24",
            currentUsername = "christopher",
            existingFriends = emptyList()
        )

        assertNull(result)
    }
}
