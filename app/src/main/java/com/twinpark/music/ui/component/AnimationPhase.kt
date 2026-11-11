package com.twinpark.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * 프레임마다 흐르는 애니메이션 시간(위상).
 *
 * - [rate]가 시간당 증가량이다. 속도가 바뀌어도 위상이 이어지도록 속도를 적분한다.
 * - 앱이 백그라운드로 가면(STARTED 아래) 루프가 멈춘다. (Pleos: 백그라운드에서 애니메이션 중단)
 * - [period]에 도달하면 되돌려서 오래 켜 두어도 부동소수점 정밀도가 떨어지지 않는다.
 *   사용하는 사인 주파수가 모두 period에서 정수 바퀴를 돌도록 호출하는 쪽이 맞춰야 이음매가 안 보인다.
 *
 * 반환된 State는 draw 블록 안에서 읽는다. 그러면 매 프레임 recomposition 없이 다시 그리기만 일어난다.
 */
@Composable
fun rememberPhase(period: Double, rate: () -> Float): DoubleState {
    val phase = remember { mutableDoubleStateOf(0.0) }
    val currentRate by rememberUpdatedState(rate)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000.0).coerceAtMost(0.1)
                last = now
                var next = phase.doubleValue + dt * currentRate()
                if (next > period) next -= period
                phase.doubleValue = next
            }
        }
    }
    return phase
}
