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
            // Figma 18:443(낮 하늘과 태양), 18:420(밤하늘과 초승달), 18:338(달과 구름 일러스트), 18:551(눈 오는 밤 가로등)
            "day" -> SceneArt(R.drawable.scene_day)
            "night" -> SceneArt(R.drawable.scene_night)
            "dawn" -> SceneArt(R.drawable.scene_dawn)
            "snow" -> SceneArt(R.drawable.scene_snow)
            else -> null
        }
    }
}
