package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric hat keinen echten AndroidKeyStore, daher wuerde
 * EncryptedSharedPreferences.create(...) mit KeyStoreException scheitern. Statt den KeyStore
 * zu faken, wird die (Java-)static-Factory-Methode direkt gemockt und liefert ein
 * In-Memory-Fake von SharedPreferences, das Change-Listener synchron feuert - genau das
 * Verhalten, auf dem CredentialsRepository (ueber den lazy SharedPreferences-Getter) aufbaut.
 */
@RunWith(RobolectricTestRunner::class)
class CredentialsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private fun repository() = CredentialsRepository(context)

    @Before
    fun setUp() {
        mockkConstructor(MasterKey.Builder::class)
        every { anyConstructed<MasterKey.Builder>().setKeyScheme(any()) } answers { self as MasterKey.Builder }
        every { anyConstructed<MasterKey.Builder>().build() } returns mockk<MasterKey>(relaxed = true)

        mockkStatic(EncryptedSharedPreferences::class)
        every {
            EncryptedSharedPreferences.create(
                any<Context>(),
                any<String>(),
                any<MasterKey>(),
                any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<EncryptedSharedPreferences.PrefValueEncryptionScheme>()
            )
        } returns FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getCredentials liefert null wenn nichts gespeichert ist`() {
        assertNull(repository().getCredentials())
    }

    @Test
    fun `saveCredentials speichert Daten die getCredentials unveraendert zurueckliefert`() {
        val repo = repository()

        repo.saveCredentials(credentials)

        assertEquals(credentials, repo.getCredentials())
    }

    @Test
    fun `getCredentials liefert null nach clearActiveCredentials obwohl Prefill fortbesteht`() {
        val repo = repository()
        repo.saveCredentials(credentials)

        repo.clearActiveCredentials()

        assertNull(repo.getCredentials())
        assertEquals(LoginPrefill(username = "chris", password = "pw"), repo.getLoginPrefill())
    }

    @Test
    fun `getLoginPrefill liefert leere Werte wenn nichts gespeichert wurde`() {
        assertEquals(LoginPrefill(), repository().getLoginPrefill())
    }

    @Test
    fun `clearCredentials entfernt auch die Prefill-Werte`() {
        val repo = repository()
        repo.saveCredentials(credentials)

        repo.clearCredentials()

        assertNull(repo.getCredentials())
        assertEquals(LoginPrefill(), repo.getLoginPrefill())
    }

    @Test
    fun `hasCredentials ist false ohne gespeicherte Zugangsdaten`() {
        assertFalse(repository().hasCredentials())
    }

    @Test
    fun `hasCredentials wird nach saveCredentials ueber den Change-Listener synchron true`() {
        val repo = repository()

        repo.saveCredentials(credentials)

        assertTrue(repo.hasCredentials())
    }

    @Test
    fun `hasCredentials ist false wenn ein Feld nach dem Speichern leer ist`() {
        val repo = repository()

        repo.saveCredentials(credentials.copy(password = ""))

        assertFalse(repo.hasCredentials())
    }
}

/**
 * Minimaler In-Memory-Ersatz fuer SharedPreferences: haelt Werte in einer Map und feuert
 * registrierte Change-Listener synchron in apply()/commit(), damit CredentialsRepository
 * (das genau darauf angewiesen ist, um seinen StateFlow zu aktualisieren) sich wie mit
 * echten (Encrypted)SharedPreferences verhaelt.
 */
private class FakeSharedPreferences : SharedPreferences {

    private val lock = Any()
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { values.toMutableMap() }

    override fun getString(key: String?, defValue: String?): String? =
        synchronized(lock) { values[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) { values[key] as? MutableSet<String> ?: defValues }

    override fun getInt(key: String?, defValue: Int): Int =
        synchronized(lock) { values[key] as? Int ?: defValue }

    override fun getLong(key: String?, defValue: Long): Long =
        synchronized(lock) { values[key] as? Long ?: defValue }

    override fun getFloat(key: String?, defValue: Float): Float =
        synchronized(lock) { values[key] as? Float ?: defValue }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        synchronized(lock) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String?): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener ?: return
        synchronized(lock) { listeners += listener }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener ?: return
        synchronized(lock) { listeners -= listener }
    }

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var doClear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            key?.let { pending[it] = values }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let { removed += it }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            doClear = true
            return this
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            val listenersSnapshot = synchronized(lock) {
                if (doClear) values.clear()
                removed.forEach { values.remove(it) }
                values.putAll(pending)
                pending.clear()
                removed.clear()
                doClear = false
                listeners.toList()
            }
            listenersSnapshot.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, null) }
        }
    }
}
