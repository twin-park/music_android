package com.twinpark.music.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 소리 파일(WAV, mp3, m4a, ogg, flac 등 기기가 읽는 것)을 16kHz 모노 16비트 소리 값으로 읽는다. 시험용 소리 넣기에 쓴다. */
object AudioFileDecoder {
    /** 못 읽으면 예외(파일이 없거나 소리가 아님). */
    fun decode(file: File): ShortArray {
        require(file.isFile) { "파일이 없음: $file" }
        parseWav(file.readBytes())?.let { return PcmConvert.toMono16k(it.samples, it.channels, it.rate) }
        val raw = decodeWithCodec(file)
        return PcmConvert.toMono16k(raw.samples, raw.channels, raw.rate)
    }

    private class Pcm(val samples: ShortArray, val channels: Int, val rate: Int)

    /** 16비트 PCM WAV만 직접 읽는다. 그 밖의 WAV(float 등)는 null을 돌려 기기 디코더에 맡긴다. */
    private fun parseWav(b: ByteArray): Pcm? {
        if (b.size < 44 || String(b, 0, 4, Charsets.US_ASCII) != "RIFF" || String(b, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var channels = 0
        var rate = 0
        var bits = 0
        var format = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt " && body + 16 <= b.size) {
                format = buf.getShort(body).toInt()
                channels = buf.getShort(body + 2).toInt()
                rate = buf.getInt(body + 4)
                bits = buf.getShort(body + 14).toInt()
            } else if (id == "data") {
                if (format != 1 || bits != 16 || channels < 1 || rate <= 0) return null
                val len = if (size < 0 || body + size > b.size) b.size - body else size       // 길이를 모르는 파일은 끝까지
                val out = ShortArray(len / 2)
                buf.position(body)
                buf.asShortBuffer().get(out)
                return Pcm(out, channels, rate)
            }
            if (size < 0) return null
            pos = body + size + (size and 1)
        }
        return null
    }

    private fun decodeWithCodec(file: File): Pcm {
        val ex = MediaExtractor()
        ex.setDataSource(file.absolutePath)
        try {
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IllegalArgumentException("소리 트랙이 없음: $file")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val out = java.io.ByteArrayOutputStream()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o >= 0 -> {
                            val chunk = codec.getOutputBuffer(o)!!
                            val bytes = ByteArray(info.size)
                            chunk.position(info.offset)
                            chunk.get(bytes)
                            out.write(bytes)
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            val bytes = out.toByteArray()
            val samples = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(samples)
            return Pcm(samples, channels, rate)
        } finally {
            ex.release()
        }
    }
}
