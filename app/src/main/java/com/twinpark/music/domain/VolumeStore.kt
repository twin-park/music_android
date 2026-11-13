package com.twinpark.music.domain

interface VolumeStore {
    fun load(): Float?
    fun save(volume: Float)
}
