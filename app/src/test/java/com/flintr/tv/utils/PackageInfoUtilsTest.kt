package com.flintr.tv.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PackageInfoUtilsTest {

    @Test
    fun `loadPackageInfo liefert Paketdaten der App`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()

        val info = loadPackageInfo(context, Dispatchers.Unconfined)

        assertEquals(context.packageName, info?.packageName)
    }

    @Test
    fun `loadPackageInfo liefert null bei PackageManager-Fehler`() = runBlocking {
        val context = mockk<Context>()
        every { context.packageManager } throws RuntimeException("kaputt")

        assertNull(loadPackageInfo(context, Dispatchers.Unconfined))
    }
}
