package com.twinpark.music.ui.component.orb

/**
 * 오브 개수. 오브마다 값은 [OrbStyles]가 가진다(크기, 색, 속도 등). Figma 시안 6장(0~4는 20/30/50/70/90%, 5는 눈 오는 밤 구리빛 구)이고
 * 포즈 번호는 [com.twinpark.music.domain.Mood.orbPose]와 데이터 파일(orb_poses.bin)의 포즈 순서와 같다.
 */
object OrbPoses {
    /** 오브 개수. 값은 [OrbStyles.ALL]이 오브별로 가진다. */
    val COUNT: Int get() = OrbStyles.ALL.size
}
