package com.twinpark.music.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.twinpark.music.audio.MusicEngine
import com.twinpark.music.domain.QuadrantControl
import com.twinpark.music.domain.VolumeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicViewModel(
    private val engine: MusicEngine,
    private val volumeStore: VolumeStore,
    private val saveDelayMs: Long = SAVE_DELAY_MS,
) : ViewModel() {
    val startVolume: Float = QuadrantControl.restoreVolume(volumeStore.load())

    private val _levels = MutableStateFlow(QuadrantControl.initialLevels(startVolume))
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    init {
        val volume = _levels.map(QuadrantControl::volumeOf).distinctUntilChanged()
        // 노브 값 0..1에 제곱을 취해 귀에 고르게 느껴지게 한다
        viewModelScope.launch { volume.collect { engine.setVolume(it * it) } }
        viewModelScope.launch { volume.collectLatest { delay(saveDelayMs); volumeStore.save(it) } }
    }

    fun onKnobMoved(x: Float, y: Float) {
        _levels.update { QuadrantControl.update(it, x, y) }
    }

    companion object {
        const val SAVE_DELAY_MS = 500L

        fun factory(engine: MusicEngine, volumeStore: VolumeStore) = viewModelFactory {
            initializer { MusicViewModel(engine, volumeStore) }
        }
    }
}
