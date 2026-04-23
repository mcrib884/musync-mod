package dev.mcrib884.musync.client

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import org.lwjgl.openal.AL10
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

object Mp3AudioSupport {
    fun openFile(file: File, shouldCancel: () -> Boolean = { false }): CustomTrackPlayer.AudioStream {
        file.inputStream().use { input ->
            return createStream(input, file.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), shouldCancel)
        }
    }

    fun openBytes(audioData: ByteArray, sourceName: String? = null, shouldCancel: () -> Boolean = { false }): CustomTrackPlayer.AudioStream {
        ByteArrayInputStream(audioData).use { input ->
            return createStream(input, audioData.size, shouldCancel)
        }
    }

    private fun createStream(input: InputStream, sourceSizeHint: Int, shouldCancel: () -> Boolean): CustomTrackPlayer.AudioStream {
        val bitstream = Bitstream(input)
        try {
            val decoder = Decoder()
            val output = ByteArrayOutputStream(maxOf(32768, sourceSizeHint * 4))
            var sampleRate = 0
            var channelCount = 0
            var totalFrames = 0L

            while (true) {
                if (shouldCancel()) throw CancellationException()

                val header = bitstream.readFrame() ?: break
                try {
                    val decoded = decoder.decodeFrame(header, bitstream) as? SampleBuffer
                        ?: throw IllegalStateException("MP3: decoder returned unsupported output buffer")

                    if (sampleRate == 0) {
                        sampleRate = decoded.sampleFrequency
                        channelCount = decoded.channelCount
                    } else if (sampleRate != decoded.sampleFrequency || channelCount != decoded.channelCount) {
                        throw IllegalStateException(
                            "MP3: variable output format not supported ($sampleRate/$channelCount -> ${decoded.sampleFrequency}/${decoded.channelCount})"
                        )
                    }

                    val samples = decoded.buffer
                    val sampleCount = decoded.bufferLength
                    for (index in 0 until sampleCount) {
                        val sample = samples[index].toInt()
                        output.write(sample and 0xFF)
                        output.write((sample ushr 8) and 0xFF)
                    }

                    totalFrames += sampleCount.toLong() / channelCount.coerceAtLeast(1)
                } finally {
                    bitstream.closeFrame()
                }
            }

            if (sampleRate <= 0 || channelCount <= 0 || output.size() == 0) {
                throw IllegalStateException("MP3: no audio frames decoded")
            }

            return PcmBackedAudioStream(
                pcmData = output.toByteArray(),
                channels = channelCount,
                sampleRate = sampleRate,
                durationMs = (totalFrames * 1000L) / sampleRate.toLong()
            )
        } finally {
            try {
                bitstream.close()
            } catch (_: Exception) {}
        }
    }

    private class PcmBackedAudioStream(
        private val pcmData: ByteArray,
        channels: Int,
        override val sampleRate: Int,
        override val durationMs: Long
    ) : CustomTrackPlayer.AudioStream {
        override val format: Int = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
        private val frameSize = channels.coerceAtLeast(1) * 2
        private var currentPos = 0

        override fun readPcm(buffer: ByteBuffer): Int {
            val remaining = pcmData.size - currentPos
            if (remaining <= 0) return 0

            val requested = minOf(buffer.remaining(), remaining)
            val aligned = requested - (requested % frameSize)
            if (aligned <= 0) return 0

            buffer.put(pcmData, currentPos, aligned)
            currentPos += aligned
            return aligned
        }

        override fun seekMs(ms: Long) {
            val targetFrame = (ms.coerceAtLeast(0L) * sampleRate) / 1000L
            val targetPos = targetFrame * frameSize.toLong()
            currentPos = targetPos.coerceIn(0L, pcmData.size.toLong()).toInt()
            currentPos -= currentPos % frameSize
        }

        override fun close() {}
    }
}