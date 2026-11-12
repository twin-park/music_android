package com.twinpark.music.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 맑은 유리(iOS의 Liquid Glass 같은 느낌)의 공통 재료. 어두운 틴트로 탁하게 만들지 않고, 뒤 장면이 비치게 두면서
 * 왼쪽 위에서 오는 빛이 가장자리에 모이고 반대쪽(오른쪽 아래)에 옅게 되비치는 것으로 두께를 보여 준다.
 */
object Glass {
    /** 테두리 빛: 왼쪽 위가 가장 밝고 가운데는 거의 투명하며 오른쪽 아래에 옅게 한 번 더 모인다. */
    val Edge: Brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.90f),
        0.42f to Color.White.copy(alpha = 0.12f),
        0.78f to Color.White.copy(alpha = 0.10f),
        1f to Color.White.copy(alpha = 0.50f),
    )

    /** 면: 위에서 아래로 옅어지는 흰빛. 뒤가 비쳐 보이도록 아주 옅다. */
    val Fill: Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.06f)),
    )

    val EdgeStroke = BorderStroke(1.2.dp, Edge)
}
