package com.twinpark.music.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 차량이 알려 주는 주행 규제. 주행 중이라 주의 분산을 줄여야 하면 [required]가 true다. */
interface UxRestrictions {
    val required: StateFlow<Boolean>

    fun close() = Unit
}

/** 차량이 아니거나 규제 정보를 받을 수 없을 때: 제한이 없는 것으로 본다. */
object NoUxRestrictions : UxRestrictions {
    override val required: StateFlow<Boolean> = MutableStateFlow(false)
}
