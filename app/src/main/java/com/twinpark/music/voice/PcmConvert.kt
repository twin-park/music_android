package com.twinpark.music.voice

/** 소리 값을 음성 인식이 받는 꼴(16kHz 모노 16비트)로 바꾼다. */
object PcmConvert {
    const val TARGET_RATE = 16_000

    /**
     * [interleaved]는 채널이 번갈아 들어 있는 16비트 소리. 채널은 평균으로 하나로 합치고, 표본 수를 선형으로 줄이거나 늘려 [TARGET_RATE]에 맞춘다.
     */
    fun toMono16k(interleaved: ShortArray, channels: Int, rate: Int): ShortArray {
        require(channels >= 1 && rate > 0) { "채널 $channels, 표본 수 $rate" }
        val frames = interleaved.size / channels
        val mono = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += interleaved[i * channels + c]
            mono[i] = (sum / channels).toShort()
        }
        if (rate == TARGET_RATE || frames == 0) return mono
        val outFrames = (frames.toLong() * TARGET_RATE / rate).toInt()
        val out = ShortArray(outFrames)
        val step = rate.toDouble() / TARGET_RATE
        for (i in 0 until outFrames) {
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(frames - 1)
            val i1 = (i0 + 1).coerceAtMost(frames - 1)
            val frac = pos - i0
            out[i] = (mono[i0] * (1 - frac) + mono[i1] * frac).toInt().toShort()
        }
        return out
    }
}
