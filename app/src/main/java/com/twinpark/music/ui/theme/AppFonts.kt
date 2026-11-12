package com.twinpark.music.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.twinpark.music.R

/**
 * 디자이너 노트의 글꼴.
 * - 끝내기, 분위기 바꾸기, "AI가 연주중": Pretendard Variable (끝내기와 분위기 바꾸기는 bold)
 * - 나머지: Inter (regular)
 *
 * Pretendard는 가변 글꼴이라 굵기마다 파일을 따로 두지 않고 굵기 축(`wght`)을 바꿔 쓴다. 라이선스는 SIL OFL 1.1.
 */
@OptIn(ExperimentalTextApi::class)
object AppFonts {
    private fun pretendard(weight: FontWeight) = Font(
        resId = R.font.pretendard_variable,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    val Pretendard = FontFamily(
        pretendard(FontWeight.Normal),
        pretendard(FontWeight.Medium),
        pretendard(FontWeight.Bold),
        pretendard(FontWeight.Black),
    )

    private fun inter(weight: FontWeight) = Font(
        resId = R.font.inter_variable,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    /** Inter Variable (Google Fonts, SIL OFL 1.1). 굵기 축(`wght`)만 쓰고 광학 크기 축(`opsz`)은 기본값이다. */
    val Inter = FontFamily(
        inter(FontWeight.Normal),
        inter(FontWeight.Medium),
        inter(FontWeight.Bold),
        inter(FontWeight.Black),
    )
}
