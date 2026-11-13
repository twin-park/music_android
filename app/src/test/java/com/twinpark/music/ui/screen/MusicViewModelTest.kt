package com.twinpark.music.ui.screen

import com.twinpark.music.audio.BgmState
import com.twinpark.music.audio.BgmStatus
import com.twinpark.music.audio.MusicEngine
import com.twinpark.music.domain.EngineParams
import com.twinpark.music.domain.QuadrantControl
import com.twinpark.music.domain.VolumeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModelTest {
    private class FakeEngine : MusicEngine {
        val volumes = mutableListOf<Float>()
        override val state: StateFlow<BgmState> = MutableStateFlow(BgmState.Idle)
        override val paused: StateFlow<Boolean> = MutableStateFlow(false)
        override val moodId: StateFlow<String> = MutableStateFlow("")
        override fun requestMood(engineId: String) = Unit
        override fun setVolume(v: Float) { volumes += v }
        override fun setKnobParams(p: EngineParams) = Unit
        override fun status(): BgmStatus? = null
        override fun pulse() = 0f
        override fun beat() = 0f
        override fun drainNotes() = LongArray(0)
        override fun setPianoLead(on: Boolean) = Unit
        override fun playedFrame() = 0L
    }

    private class FakeStore(private val stored: Float?) : VolumeStore {
        val saved = mutableListOf<Float>()
        override fun load() = stored
        override fun save(volume: Float) { saved += volume }
    }

    private val eps = 1e-4f

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(engine: FakeEngine, store: FakeStore) = MusicViewModel(engine, store, saveDelayMs = 100)

    @Test
    fun startsFromTheStoredVolume() = runTest {
        val engine = FakeEngine()
        val vm = viewModel(engine, FakeStore(0.3f))
        runCurrent()
        assertEquals(0.3f, vm.startVolume, eps)
        assertEquals(0.3f, QuadrantControl.volumeOf(vm.levels.value), eps)
        assertEquals(0.09f, engine.volumes.last(), eps)
    }

    @Test
    fun startsFromTheDefaultWhenNothingIsStored() = runTest {
        val vm = viewModel(FakeEngine(), FakeStore(null))
        assertEquals(QuadrantControl.INITIAL_VOLUME, vm.startVolume, eps)
    }

    @Test
    fun knobMoveChangesVolumeAndEngineGetsTheSquare() = runTest {
        val engine = FakeEngine()
        val vm = viewModel(engine, FakeStore(null))
        vm.onKnobMoved(0.3f, 0.4f)
        runCurrent()
        assertEquals(0.5f, QuadrantControl.volumeOf(vm.levels.value), eps)
        assertEquals(0.25f, engine.volumes.last(), eps)
    }

    @Test
    fun savesOnlyTheLastValueAfterTheDelay() = runTest {
        val store = FakeStore(null)
        val vm = viewModel(FakeEngine(), store)
        vm.onKnobMoved(0.3f, 0f)
        runCurrent()
        advanceTimeBy(50)
        vm.onKnobMoved(0.5f, 0f)
        runCurrent()
        advanceTimeBy(99)
        assertTrue(store.saved.isEmpty())
        advanceTimeBy(2)
        assertEquals(listOf(0.5f), store.saved)
    }

    @Test
    fun savesTheStartVolumeWhenNothingChanges() = runTest {
        val store = FakeStore(0.4f)
        viewModel(FakeEngine(), store)
        runCurrent()
        advanceTimeBy(101)
        assertEquals(listOf(0.4f), store.saved)
    }
}
