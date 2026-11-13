package com.twinpark.music.settings

import android.content.Context
import com.twinpark.music.domain.VolumeStore

class PreferencesVolumeStore(context: Context) : VolumeStore {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun load(): Float? = if (prefs.contains(KEY)) prefs.getFloat(KEY, 0f) else null

    override fun save(volume: Float) {
        prefs.edit().putFloat(KEY, volume).apply()
    }

    private companion object {
        const val KEY = "volume"
    }
}
