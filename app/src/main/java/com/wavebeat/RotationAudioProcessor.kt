package com.wavebeat

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Continuous stereo rotation ("8D") applied to the playback pipeline.
 * Pans the left/right channels around the head once every ROTATION_PERIOD_SECONDS
 * using equal-power crossfade, which sounds like the music is circling you.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class RotationAudioProcessor : AudioProcessor {

    @Volatile
    var enabled = false
        set(value) {
            field = value
            if (value) framesProcessed = 0
        }

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var bytesPerFrame = 0

    private var output: ByteBuffer? = null
    private var outputPending = false
    private var inputEnded = false
    private var framesProcessed = 0L

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
        if (channelCount == 2) {
            var f = framesProcessed
            for (n in 0 until frames) {
                val gL = gainLeft(f)
                val gR = gainRight(f)
                val sL = src.short
                val sR = src.short
                dst.putShort((sL * gL).roundToInt().toShort())
                dst.putShort((sR * gR).roundToInt().toShort())
                f++
            }
        } else {
            var f = framesProcessed
            for (n in 0 until frames) {
                val g = (gainLeft(f) + gainRight(f)) * 0.5f
                val s = src.short
                dst.putShort((s * g).roundToInt().toShort())
                f++
            }
        }
        framesProcessed += frames
    }

    private fun processFloat(src: ByteBuffer, dst: ByteBuffer, frames: Int) {
        if (channelCount == 2) {
            var f = framesProcessed
            for (n in 0 until frames) {
                val gL = gainLeft(f)
                val gR = gainRight(f)
                val sL = src.float
                val sR = src.float
                dst.putFloat(sL * gL)
                dst.putFloat(sR * gR)
                f++
            }
        } else {
            var f = framesProcessed
            for (n in 0 until frames) {
                val g = (gainLeft(f) + gainRight(f)) * 0.5f
                dst.putFloat(src.float * g)
                f++
            }
        }
        framesProcessed += frames
    }

    private fun gainLeft(frame: Long): Float {
        val a = fraction(frame) * 90.0
        val x = (1.0 - cosDeg((a * 4.0).roundToInt())) * 0.5
        return cosDeg((x * 90.0).roundToInt())
    }

    private fun gainRight(frame: Long): Float {
        val a = fraction(frame) * 90.0
        val x = (1.0 - cosDeg((a * 4.0).roundToInt())) * 0.5
        return sinDeg((x * 90.0).roundToInt())
    }

    private fun fraction(frame: Long): Float {
        val total = (frame.toFloat() / (sampleRate * ROTATION_PERIOD_SECONDS)) % 1.0f
        return if (total < 0f) total + 1.0f else total
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
        framesProcessed = 0
    }

    private fun ensureBuffer(size: Int): ByteBuffer {
        val b = output
        if (b != null && b.capacity() >= size) return b
        val nb = ByteBuffer.allocateDirect(size)
        output = nb
        return nb
    }

    companion object {
        const val ROTATION_PERIOD_SECONDS = 8f

        private val SIN = FloatArray(360) { (sin(it * (PI / 180.0))).toFloat() }

        private fun sinDeg(deg: Int): Float = SIN[((deg % 360) + 360) % 360]

        private fun cosDeg(deg: Int): Float = SIN[(((deg + 90) % 360) + 360) % 360]
    }
}