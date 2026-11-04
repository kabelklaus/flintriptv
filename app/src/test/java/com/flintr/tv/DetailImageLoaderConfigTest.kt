package com.flintr.tv

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.transition.Transition
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Poster grids/detail rows must not saturate the 4-core box while scrolling (ANR 03.11.2026). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DetailImageLoaderConfigTest {

    private val loader = buildFlintrImageLoader(ApplicationProvider.getApplicationContext())

    @Test
    fun decodingLaeuftAufBegrenztemDispatcherStattAufGanzemIoPool() {
        assertEquals(2, IMAGE_DECODE_PARALLELISM)
        assertNotSame(Dispatchers.IO, loader.defaults.decoderDispatcher)
    }

    @Test
    fun keinCrossfadeUndRgb565Erlaubt() {
        assertEquals(Transition.Factory.NONE, loader.defaults.transitionFactory)
        assertTrue(loader.defaults.allowRgb565)
    }
}
