package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer

object CustomTrackPlayer {

    data class PreparedAudio(
        val format: Int,
        val sampleRate: Int,
        val pcmData: ByteArray,
        val durationMs: Long
    )

    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")
    private const val MAX_DECODE_BYTES = 256L * 1024 * 1024

    fun loadResourceAudio(soundPath: String, namespace: String = "minecraft"): PreparedAudio? {
        return try {
            //? if >=1.21 {
            /*val fileLoc = ResourceLocation.fromNamespaceAndPath(namespace, "sounds/$soundPath.ogg")*/
            //?} else {
            val fileLoc = ResourceLocation(namespace, "sounds/$soundPath.ogg")
            //?}
            val resource = Minecraft.getInstance().resourceManager.getResource(fileLoc).orElse(null)
            if (resource == null) {
                logger.warn("Could not find resource: $fileLoc")
                return null
            }
            val oggBytes = resource.open().use { it.readBytes() }
            decode(oggBytes)
        } catch (e: Exception) {
            logger.error("Error loading resource audio: ${e.message}")
            null
        }
    }

    fun decode(audioData: ByteArray): PreparedAudio? {
        if (audioData.size < 4) return null

        val magic = String(audioData, 0, 4, Charsets.US_ASCII)
        return when {
            magic == "RIFF" -> decodeWav(audioData)
            magic == "OggS" -> decodeOgg(audioData)
            else -> {
                logger.warn("Unknown audio format (magic: $magic)")
                null
            }
        }
    }

    fun playPrepared(prepared: PreparedAudio, seekMs: Long = 0, gain: Float? = null): Int {
        var alDataBuf: ByteBuffer? = null
        return try {
            alDataBuf = MemoryUtil.memAlloc(prepared.pcmData.size)
            alDataBuf.put(prepared.pcmData)
            alDataBuf.flip()

            val alBuffer = AL10.alGenBuffers()
            AL10.alBufferData(alBuffer, prepared.format, alDataBuf, prepared.sampleRate)

            val source = AL10.alGenSources()
            AL10.alSourcei(source, AL10.AL_BUFFER, alBuffer)
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
            AL10.alSourcef(source, AL10.AL_GAIN, gain ?: 1.0f)
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE)
            AL10.alSourcePlay(source)

            if (seekMs > 0) {
                AL10.alSourcef(source, AL11.AL_SEC_OFFSET, seekMs / 1000f)
            }

            source
        } catch (e: Exception) {
            logger.error("Error starting prepared audio: ${e.message}")
            -1
        } finally {
            alDataBuf?.let { MemoryUtil.memFree(it) }
        }
    }

    private fun decodeOgg(oggData: ByteArray): PreparedAudio? {
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
                    return null
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
                    return null
                }

                val pcmBuffer: ShortBuffer = MemoryUtil.memAllocShort(totalSamples * channels)
                val framesDecoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcmBuffer)
                STBVorbis.stb_vorbis_close(handle)

                val totalDecodedSamples = framesDecoded * channels
                pcmBuffer.limit(totalDecodedSamples)
                val pcmData = ByteArray(totalDecodedSamples * 2)
                var byteIndex = 0
                for (i in 0 until totalDecodedSamples) {
                    val sample = pcmBuffer.get(i).toInt()
                    pcmData[byteIndex++] = (sample and 0xFF).toByte()
                    pcmData[byteIndex++] = ((sample ushr 8) and 0xFF).toByte()
                }
                MemoryUtil.memFree(pcmBuffer)

                val durationMs = if (sampleRate > 0) {
                    (framesDecoded.toLong() * 1000L) / sampleRate.toLong()
                } else {
                    -1L
                }

                return PreparedAudio(
                    format = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,
                    sampleRate = sampleRate,
                    pcmData = pcmData,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            logger.error("CustomTrackPlayer error: ${e.message}")
            return null
        } finally {
            inputBuffer?.let { MemoryUtil.memFree(it) }
        }
    }

    private fun decodeWav(wavData: ByteArray): PreparedAudio? {
        try {
            if (wavData.size < 44) {
                logger.warn("WAV too small: ${wavData.size} bytes")
                return null
            }

            val buf = java.nio.ByteBuffer.wrap(wavData).order(java.nio.ByteOrder.LITTLE_ENDIAN)

            buf.position(0)
            val riff = ByteArray(4); buf.get(riff)
            if (String(riff) != "RIFF") return null
            buf.int
            val wave = ByteArray(4); buf.get(wave)
            if (String(wave) != "WAVE") return null

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
                            return null
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
                return null
            }

            val alFormat = when {
                channels == 1 && bitsPerSample == 8 -> AL10.AL_FORMAT_MONO8
                channels == 1 && bitsPerSample == 16 -> AL10.AL_FORMAT_MONO16
                channels == 2 && bitsPerSample == 8 -> AL10.AL_FORMAT_STEREO8
                channels == 2 && bitsPerSample == 16 -> AL10.AL_FORMAT_STEREO16
                else -> {
                    logger.warn("WAV: unsupported format: ${channels}ch ${bitsPerSample}bit")
                    return null
                }
            }

            val bytesPerSample = bitsPerSample / 8
            val totalSamples = dataBytes.size / (channels * bytesPerSample)
            val durationMs = (totalSamples.toLong() * 1000L) / sampleRate.toLong()

            return PreparedAudio(
                format = alFormat,
                sampleRate = sampleRate,
                pcmData = dataBytes,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            logger.error("WAV playback error: ${e.message}")
            return null
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

}
