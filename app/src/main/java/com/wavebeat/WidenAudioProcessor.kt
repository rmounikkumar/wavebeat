package com.wavebeat

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mid/Side stereo widener. Boosts the side (difference) channel to widen the
 * soundstage; device-independent (runs in our own audio pipeline) unlike the
 * platform Virtualizer, which does nothing on many devices.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class WidenAudioProcessor : AudioProcessor {

    @Volatile
    var enabled = false

    @Volatile
    var width = 1f

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var bytesPerFrame = 0

    private var output: ByteBuffer? = null
    private var outputPending = false
    private var inputEnded = false

    override fun isActive(): Boolean = true

    override fun configure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(input)
        }
        if (input.channelCount != 1 && input.channelCount != 2) {
            throw AudioProcessor.UnhandledAudioFormatException(input)
        }
        sampleRate = input.sampleRate
        channelCount = input.channelCount
        encoding = input.encoding
        bytesPerFrame = input.bytesPerFrame
        output = null
        outputPending = false
        return input
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.remaining() == 0) return
        if (!enabled || channelCount < 2 || width <= 1f) {
            val pass = ensureBuffer(inputBuffer.remaining())
            pass.clear()
            pass.put(inputBuffer)
            pass.flip()
            outputPending = true
            return
        }
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val out = ensureBuffer(inputBuffer.remaining())
        out.clear()
        val src = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val dst = out.order(ByteOrder.nativeOrder())
        inputBuffer.position(inputBuffer.limit())

        if (encoding == C.ENCODING_PCM_FLOAT) {
            processFloat(src, dst, frames)
        } else {
            processShort(src, dst, frames)
        }
        out.flip()
        outputPending = true
    }

    private fun processShort(src: ByteBuffer, dst: ByteBuffer, frames: Int) {
        val w = width
        for (n in 0 until frames) {
            val l = src.short.toInt()
            val r = src.short.toInt()
            val m = (l + r) / 2.0
            val s = (l - r) * w * 0.5
            var ol = m + s
            var or = m - s
            val peak = maxOf(Math.abs(ol), Math.abs(or))
            if (peak > 32767.0) {
                val k = 32767.0 / peak
                ol *= k
                or *= k
            }
            dst.putShort(ol.toInt().toShort())
            dst.putShort(or.toInt().toShort())
        }
    }

    private fun processFloat(src: ByteBuffer, dst: ByteBuffer, frames: Int) {
        val w = width
        for (n in 0 until frames) {
            val l = src.float
            val r = src.float
            val m = (l + r) * 0.5
            val s = (l - r) * w * 0.5
            var ol = m + s
            var or = m - s
            val peak = maxOf(Math.abs(ol), Math.abs(or))
            if (peak > 1.0) {
                val k = 1.0 / peak
                ol *= k
                or *= k
            }
            dst.putFloat(ol.toFloat())
            dst.putFloat(or.toFloat())
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val o = output ?: return AudioProcessor.EMPTY_BUFFER
        if (!outputPending) return AudioProcessor.EMPTY_BUFFER
        outputPending = false
        return o
    }

    override fun isEnded(): Boolean = inputEnded && !outputPending

    override fun flush() {
        output = null
        outputPending = false
        inputEnded = false
    }

    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        bytesPerFrame = 0
    }

    private fun ensureBuffer(size: Int): ByteBuffer {
        val b = output
        if (b != null && b.capacity() >= size) return b
        val nb = ByteBuffer.allocateDirect(size)
        output = nb
        return nb
    }
}