package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer

object CustomTrackPlayer {

    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")
    private const val MAX_DECODE_BYTES = 150L * 1024 * 1024

    fun play(audioData: ByteArray): Int {
        if (audioData.size < 4) return -1

        val magic = String(audioData, 0, 4, Charsets.US_ASCII)
        return when {
            magic == "RIFF" -> playWav(audioData)
            magic == "OggS" -> playOgg(audioData)
            else -> {
                logger.warn("Unknown audio format (magic: $magic)")
                -1
            }
        }
    }

    private fun playOgg(oggData: ByteArray): Int {
        var inputBuffer: ByteBuffer? = null
        try {
            inputBuffer = MemoryUtil.memAlloc(oggData.size)
            inputBuffer.put(oggData)
            inputBuffer.flip()

            MemoryStack.stackPush().use { stack ->
                val error = stack.mallocInt(1)
                val handle = STBVorbis.stb_vorbis_open_memory(inputBuffer, error, null)
                if (handle == 0L) {
                    logger.error("STBVorbis error ${error[0]}")
                    return -1
                }

                val info = STBVorbisInfo.malloc(stack)
                STBVorbis.stb_vorbis_get_info(handle, info)
                val channels = info.channels()
                val sampleRate = info.sample_rate()
                val totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle)

                val decodedSize = totalSamples.toLong() * channels.toLong() * 2L
                if (decodedSize > MAX_DECODE_BYTES) {
                    logger.error("Track too large to decode: ${decodedSize / (1024 * 1024)}MB exceeds ${MAX_DECODE_BYTES / (1024 * 1024)}MB limit")
                    STBVorbis.stb_vorbis_close(handle)
                    return -1
                }

                val pcmBuffer: ShortBuffer = MemoryUtil.memAllocShort(totalSamples * channels)
                STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcmBuffer)
                STBVorbis.stb_vorbis_close(handle)

                val alBuffer = AL10.alGenBuffers()
                val format = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
                AL10.alBufferData(alBuffer, format, pcmBuffer, sampleRate)
                MemoryUtil.memFree(pcmBuffer)

                val source = AL10.alGenSources()
                AL10.alSourcei(source, AL10.AL_BUFFER, alBuffer)
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
                AL10.alSourcef(source, AL10.AL_GAIN, 1.0f)
                AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE)
                AL10.alSourcePlay(source)

                return source
            }
        } catch (e: Exception) {
            logger.error("CustomTrackPlayer error: ${e.message}")
            return -1
        } finally {
            inputBuffer?.let { MemoryUtil.memFree(it) }
        }
    }

    private fun playWav(wavData: ByteArray): Int {
        try {
            if (wavData.size < 44) {
                logger.warn("WAV too small: ${wavData.size} bytes")
                return -1
            }

            val buf = java.nio.ByteBuffer.wrap(wavData).order(java.nio.ByteOrder.LITTLE_ENDIAN)

            buf.position(0)
            val riff = ByteArray(4); buf.get(riff)
            if (String(riff) != "RIFF") return -1
            buf.int
            val wave = ByteArray(4); buf.get(wave)
            if (String(wave) != "WAVE") return -1

            var channels = 0
            var sampleRate = 0
            var bitsPerSample = 0
            var dataBytes: ByteArray? = null

            while (buf.remaining() >= 8) {
                val chunkId = ByteArray(4); buf.get(chunkId)
                val chunkSize = buf.int
                val id = String(chunkId)

                when (id) {
                    "fmt " -> {
                        val audioFormat = buf.short.toInt() and 0xFFFF
                        if (audioFormat != 1) {
                            logger.warn("WAV: unsupported format $audioFormat (only PCM/1 supported)")
                            return -1
                        }
                        channels = buf.short.toInt() and 0xFFFF
                        sampleRate = buf.int
                        buf.int
                        buf.short
                        bitsPerSample = buf.short.toInt() and 0xFFFF

                        val fmtRead = 16
                        if (chunkSize > fmtRead) {
                            buf.position(buf.position() + (chunkSize - fmtRead))
                        }
                    }
                    "data" -> {
                        dataBytes = ByteArray(chunkSize)
                        buf.get(dataBytes)
                    }
                    else -> {

                        if (chunkSize > 0 && buf.remaining() >= chunkSize) {
                            buf.position(buf.position() + chunkSize)
                        }
                    }
                }
            }

            if (dataBytes == null || channels == 0 || sampleRate == 0 || bitsPerSample == 0) {
                logger.warn("WAV: missing required chunks")
                return -1
            }

            val alFormat = when {
                channels == 1 && bitsPerSample == 8 -> AL10.AL_FORMAT_MONO8
                channels == 1 && bitsPerSample == 16 -> AL10.AL_FORMAT_MONO16
                channels == 2 && bitsPerSample == 8 -> AL10.AL_FORMAT_STEREO8
                channels == 2 && bitsPerSample == 16 -> AL10.AL_FORMAT_STEREO16
                else -> {
                    logger.warn("WAV: unsupported format: ${channels}ch ${bitsPerSample}bit")
                    return -1
                }
            }

            val alDataBuf = MemoryUtil.memAlloc(dataBytes.size)
            alDataBuf.put(dataBytes)
            alDataBuf.flip()

            val alBuffer = AL10.alGenBuffers()
            AL10.alBufferData(alBuffer, alFormat, alDataBuf, sampleRate)
            MemoryUtil.memFree(alDataBuf)

            val source = AL10.alGenSources()
            AL10.alSourcei(source, AL10.AL_BUFFER, alBuffer)
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
            AL10.alSourcef(source, AL10.AL_GAIN, 1.0f)
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE)
            AL10.alSourcePlay(source)

            return source
        } catch (e: Exception) {
            logger.error("WAV playback error: ${e.message}")
            return -1
        }
    }

    fun isPlaying(source: Int): Boolean {
        if (source == -1) return false
        return try {
            AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING
        } catch (_: Exception) {
            false
        }
    }

    fun stop(source: Int) {
        if (source == -1) return
        try {
            AL10.alSourceStop(source)
            val buffer = AL10.alGetSourcei(source, AL10.AL_BUFFER)
            AL10.alDeleteSources(source)
            if (buffer != 0) AL10.alDeleteBuffers(buffer)
        } catch (e: Exception) {
            logger.error("Error stopping custom track: ${e.message}")
        }
    }

    fun playFromResource(soundPath: String, seekMs: Long = 0, namespace: String = "minecraft"): Int {
        try {
            //? if >=1.21 {
            /*val fileLoc = ResourceLocation.fromNamespaceAndPath(namespace, "sounds/$soundPath.ogg")*/
            //?} else {
            val fileLoc = ResourceLocation(namespace, "sounds/$soundPath.ogg")
            //?}
            val resource = Minecraft.getInstance().resourceManager.getResource(fileLoc).orElse(null)
            if (resource == null) {
                logger.warn("Could not find resource: $fileLoc")
                return -1
            }

            val oggBytes = resource.open().use { it.readBytes() }
            val source = play(oggBytes)
            if (source == -1) return -1

            val mc = Minecraft.getInstance()
            val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
            val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
            AL10.alSourcef(source, AL10.AL_GAIN, musicVol * masterVol)

            if (seekMs > 0) {
                AL10.alSourcef(source, AL11.AL_SEC_OFFSET, seekMs / 1000f)
            }

            return source
        } catch (e: Exception) {
            logger.error("Error playing from resource: ${e.message}")
            return -1
        }
    }

    fun getDurationMs(audioData: ByteArray): Long {
        if (audioData.size < 4) return -1
        val magic = String(audioData, 0, 4, Charsets.US_ASCII)
        return when {
            magic == "RIFF" -> getDurationMsWav(audioData)
            magic == "OggS" -> getDurationMsOgg(audioData)
            else -> -1
        }
    }

    private fun getDurationMsOgg(oggData: ByteArray): Long {
        var buf: ByteBuffer? = null
        try {
            buf = MemoryUtil.memAlloc(oggData.size)
            buf.put(oggData)
            buf.flip()

            MemoryStack.stackPush().use { stack ->
                val error = stack.mallocInt(1)
                val handle = STBVorbis.stb_vorbis_open_memory(buf, error, null)
                if (handle == 0L) return -1

                val totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle)
                val info = STBVorbisInfo.malloc(stack)
                STBVorbis.stb_vorbis_get_info(handle, info)
                val sampleRate = info.sample_rate()
                STBVorbis.stb_vorbis_close(handle)

                if (sampleRate <= 0) return -1
                return (totalSamples.toLong() * 1000L) / sampleRate.toLong()
            }
        } catch (_: Exception) {
            return -1
        } finally {
            buf?.let { MemoryUtil.memFree(it) }
        }
    }

    private fun getDurationMsWav(wavData: ByteArray): Long {
        try {
            if (wavData.size < 44) return -1
            val buf = java.nio.ByteBuffer.wrap(wavData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.position(0)
            buf.position(12)

            var channels = 0
            var sampleRate = 0
            var bitsPerSample = 0
            var dataSize = 0

            while (buf.remaining() >= 8) {
                val chunkId = ByteArray(4); buf.get(chunkId)
                val chunkSize = buf.int
                when (String(chunkId)) {
                    "fmt " -> {
                        buf.short
                        channels = buf.short.toInt() and 0xFFFF
                        sampleRate = buf.int
                        buf.int
                        buf.short
                        bitsPerSample = buf.short.toInt() and 0xFFFF
                        val fmtRead = 16
                        if (chunkSize > fmtRead) buf.position(buf.position() + (chunkSize - fmtRead))
                    }
                    "data" -> {
                        dataSize = chunkSize
                        break
                    }
                    else -> {
                        if (chunkSize > 0 && buf.remaining() >= chunkSize) buf.position(buf.position() + chunkSize)
                    }
                }
            }

            if (channels == 0 || sampleRate == 0 || bitsPerSample == 0 || dataSize == 0) return -1
            val bytesPerSample = bitsPerSample / 8
            val totalSamples = dataSize / (channels * bytesPerSample)
            return (totalSamples.toLong() * 1000L) / sampleRate.toLong()
        } catch (_: Exception) {
            return -1
        }
    }
}
