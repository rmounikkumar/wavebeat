package com.wavebeat

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Algorithmic (Schroeder/Freeverb-style) room reverb applied in our own audio
 * pipeline. Unlike the platform EnvironmentalReverb (a no-op on most devices),
 * this reliably adds audible room ambience on any hardware.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ReverbAudioProcessor : AudioProcessor {

    @Volatile
    var enabled = false

    @Volatile
    var wet = 0.45f

    private var roomL: Room? = null
    private var roomR: Room? = null
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
        roomL = Room(input.sampleRate)
        roomR = Room(input.sampleRate)
        output = null
        outputPending = false
        return input
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.remaining() == 0) return
        if (!enabled) {
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
        val rl = roomL ?: return
        val rr = roomR ?: return
        val w = wet
        for (n in 0 until frames) {
            val l = src.short.toFloat()
            val r = if (channelCount == 2) src.short.toFloat() else l
            var ol = l + rl.process(l) * w
            var or = r + rr.process(r) * w
            val peak = maxOf(Math.abs(ol), Math.abs(or))
            if (peak > 32767f) {
                val k = 32767f / peak
                ol *= k
                or *= k
            }
            dst.putShort(ol.toInt().toShort())
            if (channelCount == 2) dst.putShort(or.toInt().toShort())
        }
    }

    private fun processFloat(src: ByteBuffer, dst: ByteBuffer, frames: Int) {
        val rl = roomL ?: return
        val rr = roomR ?: return
        val w = wet
        for (n in 0 until frames) {
            val l = src.float
            val r = if (channelCount == 2) src.float else l
            var ol = l + rl.process(l) * w
            var or = r + rr.process(r) * w
            val peak = maxOf(Math.abs(ol), Math.abs(or))
            if (peak > 1f) {
                val k = 1f / peak
                ol *= k
                or *= k
            }
            dst.putFloat(ol)
            if (channelCount == 2) dst.putFloat(or)
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
        roomL = null
        roomR = null
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

    private class Room(sampleRate: Int) {
        private val combs: Array<Comb>
        private val allpasses: Array<Allpass>

        init {
            val scale = sampleRate / 44100.0
            combs = COMB_TUNING.map { Comb((it * scale).toInt().coerceAtLeast(1), 0.63f) }.toTypedArray()
            allpasses = ALLPASS_TUNING.map { Allpass((it * scale).toInt().coerceAtLeast(1), 0.5f) }.toTypedArray()
        }

        fun process(input: Float): Float {
            var s = 0f
            for (c in combs) s += c.process(input)
            for (a in allpasses) s = a.process(s)
            return s * 0.25f
        }
    }

    private class Comb(delay: Int, private val fb: Float) {
        private val buf = FloatArray(delay.coerceAtLeast(1))
        private var idx = 0
        fun process(input: Float): Float {
            val out = buf[idx]
            buf[idx] = input + out * fb
            idx = (idx + 1) % buf.size
            return out
        }
    }

    private class Allpass(delay: Int, private val g: Float) {
        private val buf = FloatArray(delay.coerceAtLeast(1))
        private var idx = 0
        fun process(input: Float): Float {
            val bufOut = buf[idx]
            val out = -input + bufOut
            buf[idx] = input + bufOut * g
            idx = (idx + 1) % buf.size
            return out
        }
    }

    private companion object {
        val COMB_TUNING = intArrayOf(1116, 1188, 1277, 1356)
        val ALLPASS_TUNING = intArrayOf(556, 441)
    }
}