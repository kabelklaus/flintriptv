package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.EpgProgram
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeason
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.testutil.MainDispatcherRule
import com.flintr.tv.ui.screens.SeriesContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: XtreamRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        credentialsRepository = mockk()
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        viewModel = PlayerViewModel(
            repository = repository,
            credentialsRepository = credentialsRepository,
            watchProgressService = mockk<WatchProgressService>(relaxed = true)
        )
    }

    private fun seriesFixture() = testSeries(
        seriesId = 10,
        seasons = listOf(
            testSeason(
                seasonNumber = 1,
                episodes = listOf(
                    testEpisode(id = 101, title = "Anfang", episodeNum = 1, season = 1),
                    testEpisode(id = 102, title = "Mitte", episodeNum = 2, season = 1)
                )
            ),
            testSeason(
                seasonNumber = 2,
                episodes = listOf(testEpisode(id = 201, title = "Neustart", episodeNum = 1, season = 2))
            )
        )
    )

    private fun contextFor(episodeId: Int): SeriesContext {
        val series = seriesFixture()
        val season = series.seasons!!.first { s -> s.episodes!!.any { it.id == episodeId } }
        return SeriesContext(series, season, season.episodes!!.first { it.id == episodeId })
    }

    @Test
    fun `setStreamUrl aktualisiert Zustand`() {
        viewModel.setStreamUrl("http://stream/1.m3u8")
        assertEquals("http://stream/1.m3u8", viewModel.currentStreamUrl.value)
    }

    @Test
    fun `loadEpg setzt Programme bei Erfolg`() = runTest {
        val programs = listOf(
            EpgProgram(
                id = "1", epgId = null, title = "Sendung", lang = null,
                start = "", end = "", description = null, channelId = null,
                startTimestamp = null, stopTimestamp = null, nowPlaying = null, hasArchive = null
            )
        )
        coEvery { repository.getEpg("chris", "pw", 100) } coAnswers { Result.success(programs) }

        viewModel.loadEpg(100)

        assertEquals(listOf("Sendung"), viewModel.epgPrograms.value.map { it.title })

        viewModel.clearEpg()
        assertTrue(viewModel.epgPrograms.value.isEmpty())
    }

    @Test
    fun `loadSeriesContext mit vorhandenen Daten setzt Zustand direkt`() {
        viewModel.loadSeriesContext(10, 101, contextFor(101))

        assertTrue(viewModel.seriesContextLoaded.value)
        assertTrue(viewModel.hasNextEpisodeAvailable.value)
        assertEquals(101, viewModel.getSeriesContext()?.currentEpisode?.id)
    }

    @Test
    fun `loadSeriesContext laedt Kontext vom Repository`() = runTest {
        coEvery { repository.getSeriesInfo("chris", "pw", 10) } coAnswers { Result.success(seriesFixture()) }

        viewModel.loadSeriesContext(10, 102, null)

        assertTrue(viewModel.seriesContextLoaded.value)
        assertEquals(102, viewModel.getSeriesContext()?.currentEpisode?.id)
    }

    @Test
    fun `findNextEpisode findet Folge in derselben Staffel`() {
        viewModel.loadSeriesContext(10, 101, contextFor(101))

        val next = viewModel.findNextEpisode()

        assertEquals(102, next?.episode?.id)
        assertEquals("S1E2 - Mitte", next?.displayTitle)
        assertTrue(viewModel.hasNextEpisode())
    }

    @Test
    fun `findNextEpisode springt ueber Staffelgrenze`() {
        viewModel.loadSeriesContext(10, 102, contextFor(102))

        val next = viewModel.findNextEpisode()

        assertEquals(201, next?.episode?.id)
        assertEquals("S2E1 - Neustart", next?.displayTitle)
    }

    @Test
    fun `findNextEpisode liefert null am Serienende`() {
        viewModel.loadSeriesContext(10, 201, contextFor(201))

        assertNull(viewModel.findNextEpisode())
        assertFalse(viewModel.hasNextEpisode())
    }

    @Test
    fun `showNextEpisodeButton zeigt Button mit Countdown`() = runTest {
        viewModel.loadSeriesContext(10, 101, contextFor(101))

        viewModel.showNextEpisodeButton()

        val state = viewModel.nextEpisodeState.value
        assertTrue(state is NextEpisodeState.Visible)
        assertEquals(102, (state as NextEpisodeState.Visible).info.episode.id)

        viewModel.hideNextEpisodeButton()
        assertEquals(NextEpisodeState.Hidden, viewModel.nextEpisodeState.value)
    }

    @Test
    fun `Countdown-Ende loest AutoPlay aus`() = runTest {
        viewModel.loadSeriesContext(10, 101, contextFor(101))
        viewModel.showNextEpisodeButton()

        advanceUntilIdle()

        assertEquals(NextEpisodeState.Loading, viewModel.nextEpisodeState.value)
        assertEquals(102, viewModel.consumePendingNextEpisodeInfo()?.episode?.id)
        // Zweiter Abruf liefert nichts mehr
        assertNull(viewModel.consumePendingNextEpisodeInfo())
    }

    @Test
    fun `watchCreditsMode unterdrueckt Button`() = runTest {
        viewModel.loadSeriesContext(10, 101, contextFor(101))

        viewModel.enableWatchCreditsMode()
        viewModel.showNextEpisodeButton()

        assertTrue(viewModel.watchCreditsMode.value)
        assertEquals(NextEpisodeState.Hidden, viewModel.nextEpisodeState.value)
    }

    @Test
    fun `updateAutoPlayProgress begrenzt Fortschritt`() = runTest {
        viewModel.loadSeriesContext(10, 101, contextFor(101))
        viewModel.showNextEpisodeButton()

        viewModel.updateAutoPlayProgress(1.5f)

        val state = viewModel.nextEpisodeState.value as NextEpisodeState.Visible
        assertEquals(1f, state.autoPlayProgress)
    }

    @Test
    fun `triggerAutoPlayNext ohne sichtbaren Button nutzt findNextEpisode`() = runTest {
        viewModel.loadSeriesContext(10, 101, contextFor(101))

        viewModel.triggerAutoPlayNext()

        assertEquals(NextEpisodeState.Loading, viewModel.nextEpisodeState.value)
        assertEquals(102, viewModel.consumePendingNextEpisodeInfo()?.episode?.id)
    }

    @Test
    fun `clearSeriesContext setzt alles zurueck`() = runTest {
        viewModel.loadSeriesContext(10, 101, contextFor(101))
        viewModel.showNextEpisodeButton()

        viewModel.clearSeriesContext()

        assertFalse(viewModel.seriesContextLoaded.value)
        assertFalse(viewModel.hasNextEpisodeAvailable.value)
        assertNull(viewModel.getSeriesContext())
        assertEquals(NextEpisodeState.Hidden, viewModel.nextEpisodeState.value)
        assertNull(viewModel.consumePendingNextEpisodeInfo())
    }

    @Test
    fun `Kanalnavigation laedt Kategorie und blaettert ohne Wrap-Around`() = runTest {
        val channels = listOf(
            testLiveStream(name = "Eins", streamId = 1),
            testLiveStream(name = "Zwei", streamId = 2),
            testLiveStream(name = "Drei", streamId = 3)
        )
        coEvery { repository.getLiveStreams("chris", "pw", "7") } coAnswers { Result.success(channels) }

        viewModel.loadChannelsForCategory("7")
        viewModel.findCurrentChannelIndex(2)

        assertEquals(1, viewModel.currentChannelIndex.value)
        assertEquals("Drei", viewModel.getNextChannel()?.name)
        assertNull(viewModel.getNextChannel()) // Ende, kein Wrap-Around
        assertEquals("Zwei", viewModel.getPreviousChannel()?.name)
        assertEquals("Eins", viewModel.getPreviousChannel()?.name)
        assertNull(viewModel.getPreviousChannel()) // Anfang, kein Wrap-Around
    }

    @Test
    fun `leere Kategorie setzt Kanalnavigation zurueck`() = runTest {
        viewModel.loadChannelsForCategory("")

        assertTrue(viewModel.currentCategoryChannels.value.isEmpty())
        assertEquals(-1, viewModel.currentChannelIndex.value)
        assertNull(viewModel.getNextChannel())
    }

    @Test
    fun `unbekannte streamId setzt Index auf -1`() = runTest {
        coEvery { repository.getLiveStreams("chris", "pw", "7") } coAnswers {
            Result.success(listOf(testLiveStream(streamId = 1)))
        }
        viewModel.loadChannelsForCategory("7")

        viewModel.findCurrentChannelIndex(999)

        assertEquals(-1, viewModel.currentChannelIndex.value)
    }

    @Test
    fun `clearChannelNavigation leert Kanalliste`() = runTest {
        coEvery { repository.getLiveStreams("chris", "pw", "7") } coAnswers {
            Result.success(listOf(testLiveStream(streamId = 1)))
        }
        viewModel.loadChannelsForCategory("7")

        viewModel.clearChannelNavigation()

        assertTrue(viewModel.currentCategoryChannels.value.isEmpty())
        assertEquals(-1, viewModel.currentChannelIndex.value)
    }
}
