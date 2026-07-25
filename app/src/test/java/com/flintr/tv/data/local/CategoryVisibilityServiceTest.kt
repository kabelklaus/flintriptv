package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import com.flintr.tv.testutil.silentCredentialsFlow
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryVisibilityServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var service: CategoryVisibilityService

    @Before
    fun setUp() {
        context.getSharedPreferences("category_visibility_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("category_visibility_migrated", true)
        }

        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { userRepository.loadHiddenCategories("chris") } coAnswers { Result.success(emptyMap()) }
        coEvery { userRepository.saveHiddenCategories("chris", any()) } returns Result.success(Unit)

        service = CategoryVisibilityService(
            context = context,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            syncManager = FirestoreSyncManager(syncCooldownMs = 0L)
        )
    }

    @Test
    fun `hideCategory versteckt neue Kategorie und speichert`() = runBlocking {
        assertTrue(service.hideCategory("42", CategoryType.LIVE_TV))

        assertTrue(service.isCategoryHidden("42", CategoryType.LIVE_TV))
        assertEquals(setOf("42"), service.getHiddenCategories(CategoryType.LIVE_TV))
        coVerify(timeout = 2_000) { userRepository.saveHiddenCategories("chris", any()) }
    }

    @Test
    fun `hideCategory liefert false wenn bereits versteckt`() = runBlocking {
        coEvery { userRepository.loadHiddenCategories("chris") } coAnswers {
            Result.success(mapOf(CategoryType.LIVE_TV to setOf("42")))
        }

        assertFalse(service.hideCategory("42", CategoryType.LIVE_TV))
    }

    @Test
    fun `showCategory blendet versteckte Kategorie wieder ein`() = runBlocking {
        coEvery { userRepository.loadHiddenCategories("chris") } coAnswers {
            Result.success(mapOf(CategoryType.VOD to setOf("7")))
        }

        assertTrue(service.showCategory("7", CategoryType.VOD))
        assertFalse(service.isCategoryHidden("7", CategoryType.VOD))
    }

    @Test
    fun `showCategory liefert false wenn nicht versteckt`() = runBlocking {
        assertFalse(service.showCategory("7", CategoryType.VOD))
    }

    @Test
    fun `getAllHiddenCategories liefert kompletten Zustand`() = runBlocking {
        coEvery { userRepository.loadHiddenCategories("chris") } coAnswers {
            Result.success(
                mapOf(
                    CategoryType.LIVE_TV to setOf("1"),
                    CategoryType.SERIES to setOf("2", "3")
                )
            )
        }

        assertTrue(service.syncIfNeeded())

        assertEquals(setOf("1"), service.getAllHiddenCategories()[CategoryType.LIVE_TV])
        assertEquals(setOf("2", "3"), service.getHiddenCategories(CategoryType.SERIES))
        assertEquals(emptySet<String>(), service.getHiddenCategories(CategoryType.VOD))
    }

    @Test
    fun `fehlgeschlagenes Laden laesst Zustand leer`() = runBlocking {
        coEvery { userRepository.loadHiddenCategories("chris") } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(service.syncIfNeeded())
        assertTrue(service.getAllHiddenCategories().isEmpty())
    }

    @Test
    fun `Migration uebertraegt lokale Daten aus Legacy-JSON und setzt Flag`() = runBlocking {
        val prefs = context.getSharedPreferences("category_visibility_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("category_visibility_migrated", false)
            putString("iptv_hidden_live_categories", """["10", "11"]""")
            putString("iptv_hidden_vod_categories", "")
            putString("iptv_hidden_series_categories", """["30"]""")
        }
        coEvery { userRepository.migrateHiddenCategoriesFromLocal("chris", any()) } coAnswers { Result.success(Unit) }

        assertTrue(service.syncIfNeeded())

        coVerify {
            userRepository.migrateHiddenCategoriesFromLocal(
                "chris",
                match {
                    it[CategoryType.LIVE_TV] == setOf("10", "11") &&
                        it[CategoryType.VOD] == emptySet<String>() &&
                        it[CategoryType.SERIES] == setOf("30")
                }
            )
        }
        assertTrue(prefs.getBoolean("category_visibility_migrated", false))
        assertNull(prefs.getString("iptv_hidden_live_categories", null))
    }

    @Test
    fun `Migration ohne lokale Daten setzt Flag ohne Firestore-Aufruf`() = runBlocking {
        val prefs = context.getSharedPreferences("category_visibility_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("category_visibility_migrated", false) }

        assertTrue(service.syncIfNeeded())

        coVerify(exactly = 0) { userRepository.migrateHiddenCategoriesFromLocal(any(), any()) }
        assertTrue(prefs.getBoolean("category_visibility_migrated", false))
    }

    @Test
    fun `fehlgeschlagene Migration setzt Flag nicht`() = runBlocking {
        val prefs = context.getSharedPreferences("category_visibility_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("category_visibility_migrated", false)
            putString("iptv_hidden_live_categories", """["10"]""")
        }
        coEvery { userRepository.migrateHiddenCategoriesFromLocal("chris", any()) } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(service.syncIfNeeded())

        assertFalse(prefs.getBoolean("category_visibility_migrated", true))
    }
}
