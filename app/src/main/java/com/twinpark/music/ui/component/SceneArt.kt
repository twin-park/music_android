package com.twinpark.music.ui.component

import androidx.annotation.DrawableRes
import com.twinpark.music.R

/**
 * Figma 장면 이미지. [base]는 배경 사진, [overlay]는 그 위에 [overlayAlpha]로 얹는 투명 레이어(비 오는 날의 창문 물방울)다.
 * 이미지가 없는 분위기는 절차적 구름(CloudKnob)을 쓴다.
 */
class SceneArt(
    @DrawableRes val base: Int,
    @DrawableRes val overlay: Int? = null,
    val overlayAlpha: Float = 0.5f,
) {
    companion object {
        fun forKey(key: String?): SceneArt? = when (key) {
            // Figma 18:578(배경 사진) + 18:588(물방울 레이어, 투명도 50%)
            "rain" -> SceneArt(R.drawable.scene_rain_base, R.drawable.scene_rain_drops, 0.5f)
            else -> null
        }
    }
}
