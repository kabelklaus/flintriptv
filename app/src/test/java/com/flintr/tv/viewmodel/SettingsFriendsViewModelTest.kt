package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.model.FriendEntry
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.di.ResourcesProvider
import com.flintr.tv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsFriendsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository

    private val resourcesProvider = ResourcesProvider { resId, args ->
        "res:$resId${if (args.isEmpty()) "" else ":" + args.joinToString(",")}"
    }

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.success(emptyList()) }
    }

    private fun viewModel() = SettingsFriendsViewModel(userRepository, credentialsRepository, resourcesProvider)

    private fun friend(username: String = "thomas", alias: String? = null) =
        FriendEntry(username = username, alias = alias, addedAt = "2026-07-16T10:00:00Z")

    @Test
    fun `init laedt Freunde des aktiven Nutzers`() = runTest {
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.success(listOf(friend())) }

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("chris", vm.uiState.value.currentUsername)
        assertEquals(listOf("thomas"), vm.uiState.value.friends.map { it.username })
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `ohne aktiven Nutzer wird Fehler gemeldet`() = runTest {
        every { credentialsRepository.getCredentials() } returns null

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
    }

    @Test
    fun `Ladefehler wird gemeldet`() = runTest {
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.failure(Exception("offline")) }

        val vm = viewModel()

        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
    }

    @Test
    fun `addFriend validiert leeren Nutzernamen lokal`() = runTest {
        val vm = viewModel()

        vm.addFriend("   ", "")

        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
        assertTrue(vm.uiState.value.friends.isEmpty())
    }

    @Test
    fun `addFriend lehnt eigenen Nutzernamen ab`() = runTest {
        val vm = viewModel()

        vm.addFriend("chris", "")

        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
    }

    @Test
    fun `addFriend lehnt Duplikate ab`() = runTest {
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.success(listOf(friend("thomas"))) }
        val vm = viewModel()

        vm.addFriend("thomas", "")

        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
        assertEquals(1, vm.uiState.value.friends.size)
    }

    @Test
    fun `addFriend meldet unbekannten Nutzernamen`() = runTest {
        coEvery { userRepository.usernameExists("thomas") } coAnswers { Result.success(false) }
        val vm = viewModel()

        vm.addFriend("thomas", "")

        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
        assertTrue(vm.uiState.value.friends.isEmpty())
    }

    @Test
    fun `addFriend meldet Fehler bei Existenzpruefung`() = runTest {
        coEvery { userRepository.usernameExists("thomas") } coAnswers { Result.failure(Exception("offline")) }
        val vm = viewModel()

        vm.addFriend("thomas", "")

        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `addFriend fuegt gueltigen Freund hinzu`() = runTest {
        coEvery { userRepository.usernameExists("thomas") } coAnswers { Result.success(true) }
        coEvery { userRepository.addFriendAtomically("chris", any()) } coAnswers { Result.success(Unit) }
        val vm = viewModel()

        vm.addFriend("  thomas ", "Tom")

        assertEquals(listOf("thomas"), vm.uiState.value.friends.map { it.username })
        assertEquals("Tom", vm.uiState.value.friends.first().alias)
        assertTrue(vm.uiState.value.successMessage!!.startsWith("res:"))
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `addFriend meldet Speicherfehler`() = runTest {
        coEvery { userRepository.usernameExists("thomas") } coAnswers { Result.success(true) }
        coEvery { userRepository.addFriendAtomically("chris", any()) } coAnswers {
            Result.failure(Exception("Friend already exists"))
        }
        val vm = viewModel()

        vm.addFriend("thomas", "")

        assertTrue(vm.uiState.value.errorMessage!!.startsWith("res:"))
        assertTrue(vm.uiState.value.friends.isEmpty())
    }

    @Test
    fun `clearMessages entfernt Meldungen`() = runTest {
        val vm = viewModel()
        vm.addFriend("", "")

        vm.clearMessages()

        assertNull(vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.successMessage)
    }
}
