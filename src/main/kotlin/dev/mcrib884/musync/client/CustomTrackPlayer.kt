package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

object CustomTrackPlayer {
        private val activeStreams = ConcurrentHashMap<Int, StreamThread>()

    interface AudioStream : AutoCloseable {
        val format: Int
        val sampleRate: Int
        val durationMs: Long
        fun readPcm(buffer: ByteBuffer): Int
        fun seekMs(ms: Long)
    }

    class OggStream(private val oggData: ByteArray) : AudioStream {
        private var inputBuffer: ByteBuffer? = null
        private var handle: Long = 0
        override var format: Int = 0
        override var sampleRate: Int = 0
        override var durationMs: Long = 0
        private var channels: Int = 0

        init {
            inputBuffer = MemoryUtil.memAlloc(oggData.size)
            inputBuffer!!.put(oggData)
            inputBuffer!!.flip()

            MemoryStack.stackPush().use { stack ->
                val error = stack.mallocInt(1)
                handle = STBVorbis.stb_vorbis_open_memory(inputBuffer, error, null)
                if (handle == 0L) {
                    throw Exception("STBVorbis error ${error[0]}")
                }
                val info = STBVorbisInfo.malloc(stack)
                STBVorbis.stb_vorbis_get_info(handle, info)
                channels = info.channels()
                sampleRate = info.sample_rate()
                val totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle)
                durationMs = if (sampleRate > 0) (totalSamples.toLong() * 1000L) / sampleRate.toLong() else -1L
                format = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
            }
        }

        override fun readPcm(buffer: ByteBuffer): Int {
            if (handle == 0L) return 0
            val maxFrames = buffer.remaining() / (channels * 2)
            if (maxFrames <= 0) return 0

            val shortBuffer = buffer.asShortBuffer()
            val framesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, shortBuffer)
            val bytesRead = framesRead * channels * 2
            buffer.position(buffer.position() + bytesRead)
            return bytesRead
        }

        override fun seekMs(ms: Long) {
            if (handle == 0L) return
            val sample = (ms * sampleRate) / 1000L
            STBVorbis.stb_vorbis_seek(handle, sample.toInt())
        }

        override fun close() {
            if (handle != 0L) {
                STBVorbis.stb_vorbis_close(handle)
                handle = 0L
            }
            inputBuffer?.let {
                MemoryUtil.memFree(it)
                inputBuffer = null
            }
        }
    }

    class WavStream(private val wavData: ByteArray) : AudioStream {
        override var format: Int = 0
        override var sampleRate: Int = 0
        override var durationMs: Long = 0
        private var dataOffset: Int = 0
        private var dataSize: Int = 0
        private var currentPos: Int = 0
        private var frameSize: Int = 0

        init {
            val buf = ByteBuffer.wrap(wavData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.position(12) 

            var channels = 0
            var bitsPerSample = 0
            
            while (buf.remaining() >= 8) {
                val chunkId = ByteArray(4); buf.get(chunkId)
                val chunkSize = buf.int
                if (chunkSize < 0 || chunkSize > buf.remaining()) {
                    throw Exception("WAV: invalid chunk size $chunkSize")
                }
                val id = String(chunkId, Charsets.US_ASCII)
                val chunkEnd = buf.position() + chunkSize

                when (id) {
                    "fmt " -> {
                        val audioFormat = buf.short.toInt() and 0xFFFF
                        if (audioFormat != 1) throw Exception("WAV: unsupported format")
                        channels = buf.short.toInt() and 0xFFFF
                        sampleRate = buf.int
                        buf.int
                        buf.short
                        bitsPerSample = buf.short.toInt() and 0xFFFF
                        buf.position(chunkEnd)
                    }
                    "data" -> {
                        dataOffset = buf.position()
                        dataSize = chunkSize
                        buf.position(chunkEnd)
                    }
                    else -> buf.position(chunkEnd)
                }
                if ((chunkSize and 1) == 1 && buf.hasRemaining()) {
                    buf.position(buf.position() + 1)
                }
            }

            if (channels == 0 || sampleRate == 0 || bitsPerSample == 0 || dataSize == 0) {
                throw Exception("WAV: missing required chunks")
            }

            format = when {
                channels == 1 && bitsPerSample == 8 -> AL10.AL_FORMAT_MONO8
                channels == 1 && bitsPerSample == 16 -> AL10.AL_FORMAT_MONO16
                channels == 2 && bitsPerSample == 8 -> AL10.AL_FORMAT_STEREO8
                channels == 2 && bitsPerSample == 16 -> AL10.AL_FORMAT_STEREO16
                else -> throw Exception("WAV: unsupported format")
            }

            val bytesPerSample = bitsPerSample / 8
            frameSize = channels * bytesPerSample
            val totalSamples = dataSize / frameSize
            durationMs = (totalSamples.toLong() * 1000L) / sampleRate.toLong()
        }

        override fun readPcm(buffer: ByteBuffer): Int {
            val remainingInStream = dataSize - currentPos
            if (remainingInStream <= 0) return 0
            val toRead = minOf(buffer.remaining(), remainingInStream)
            val alignedRead = toRead - (toRead % frameSize)
            if (alignedRead <= 0) return 0

            buffer.put(wavData, dataOffset + currentPos, alignedRead)
            currentPos += alignedRead
            return alignedRead
        }

        override fun seekMs(ms: Long) {
            val targetSample = (ms * sampleRate) / 1000L
            val targetPos = (targetSample * frameSize).toInt()
            currentPos = targetPos.coerceIn(0, dataSize)
            currentPos -= currentPos % frameSize
        }

        override fun close() {}
    }

    fun loadResourceAudio(soundPath: String, namespace: String = "minecraft"): AudioStream? {
        return try {
            val fileLoc = dev.mcrib884.musync.resLoc(namespace, "sounds/$soundPath.ogg")
            val resource = Minecraft.getInstance().resourceManager.getResource(fileLoc).orElse(null)
            if (resource == null) {
                dev.mcrib884.musync.MuSyncLog.warn("Could not find resource: $fileLoc")
                return null
            }
            val oggBytes = resource.open().use { it.readBytes() }
            prepareStream(oggBytes)
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Error loading resource audio: ${e.message}")
            null
        }
    }

    fun prepareStream(audioData: ByteArray): AudioStream? {
        if (audioData.size < 4) return null
        val magic = String(audioData, 0, 4, Charsets.US_ASCII)
        return try {
            when {
                magic == "RIFF" -> WavStream(audioData)
                magic == "OggS" -> OggStream(audioData)
                else -> {
                    dev.mcrib884.musync.MuSyncLog.warn("Unknown audio format (magic: $magic)")
                    null
                }
            }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Error preparing audio stream: ${e.message}")
            null
        }
    }

    private const val BUFFER_SIZE = 65536
    private const val BUFFER_COUNT = 4

    class StreamThread(
        private val stream: AudioStream,
        private val source: Int,
        private val startSeekMs: Long,
        private val initialGain: Float,
        private val startPaused: Boolean
    ) : Thread("MuSync-StreamThread-$source") {
        @Volatile var stopRequested = false
        @Volatile var seekRequestedMs: Long = -1L
        @Volatile var seekResumeAfter: Boolean = false

        private val buffers = IntArray(BUFFER_COUNT)

        override fun run() {
            var alDataBuf: ByteBuffer? = null
            try {
                alDataBuf = MemoryUtil.memAlloc(BUFFER_SIZE)
                AL10.alGenBuffers(buffers)
                
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
                AL10.alSourcef(source, AL10.AL_GAIN, initialGain)
                AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE)

                if (startSeekMs > 0) {
                    stream.seekMs(startSeekMs)
                }

                for (i in 0 until BUFFER_COUNT) {
                    alDataBuf.clear()
                    val read = stream.readPcm(alDataBuf)
                    if (read > 0) {
                        alDataBuf.flip()
                        AL10.alBufferData(buffers[i], stream.format, alDataBuf, stream.sampleRate)
                        AL10.alSourceQueueBuffers(source, buffers[i])
                    }
                }

                if (!startPaused) {
                    AL10.alSourcePlay(source)
                }

                while (!stopRequested) {
                    val seekMs = seekRequestedMs
                    if (seekMs != -1L) {
                        seekRequestedMs = -1L
                        val shouldResume = seekResumeAfter
                        seekResumeAfter = false
                        AL10.alSourceStop(source)
                        
                        val queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED)
                        if (queued > 0) {
                            val unqueued = IntArray(queued)
                            AL10.alSourceUnqueueBuffers(source, unqueued)
                        }
                        
                        stream.seekMs(seekMs)
                        
                        var queuedCount = 0
                        for (i in 0 until BUFFER_COUNT) {
                            alDataBuf.clear()
                            val read = stream.readPcm(alDataBuf)
                            if (read > 0) {
                                alDataBuf.flip()
                                AL10.alBufferData(buffers[i], stream.format, alDataBuf, stream.sampleRate)
                                AL10.alSourceQueueBuffers(source, buffers[i])
                                queuedCount++
                            }
                        }
                        if (queuedCount > 0 && shouldResume) {
                            AL10.alSourcePlay(source)
                        } else if (queuedCount == 0) {
                            break 
                        }
                    }

                    val state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE)
                    val processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED)

                    var buffersToFill = processed
                    while (buffersToFill > 0) {
                        val unqueued = AL10.alSourceUnqueueBuffers(source)
                        alDataBuf.clear()
                        val read = stream.readPcm(alDataBuf)
                        if (read > 0) {
                            alDataBuf.flip()
                            AL10.alBufferData(unqueued, stream.format, alDataBuf, stream.sampleRate)
                            AL10.alSourceQueueBuffers(source, unqueued)
                        }
                        buffersToFill--
                    }

                    if (state == AL10.AL_STOPPED && !stopRequested) {
                        val queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED)
                        if (queued == 0) {
                            break 
                        } else {
                            AL10.alSourcePlay(source) 
                        }
                    }

                    Thread.sleep(10)
                }

            } catch (e: Exception) {
                dev.mcrib884.musync.MuSyncLog.error("Streaming error on source $source: ${e.message}")
            } finally {
                try {
                    AL10.alSourceStop(source)
                    val queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED)
                    if (queued > 0) {
                        val arr = IntArray(queued)
                        AL10.alSourceUnqueueBuffers(source, arr)
                    }
                    AL10.alDeleteBuffers(buffers)
                    AL10.alDeleteSources(source)
                } catch (_: Exception) {}
                alDataBuf?.let { MemoryUtil.memFree(it) }
                stream.close()
                activeStreams.remove(source)
            }
        }
    }

    fun playStream(stream: AudioStream, seekMs: Long = 0, gain: Float? = null, startPaused: Boolean = false): Int {
        return try {
            val source = AL10.alGenSources()
            val thread = StreamThread(stream, source, seekMs, gain ?: 1.0f, startPaused)
            activeStreams[source] = thread
            thread.isDaemon = true
            thread.start()
            source
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Error starting stream: ${e.message}")
            -1
        }
    }

    fun seek(source: Int, ms: Long) {
        val thread = activeStreams[source]
        if (thread != null) {
            val shouldResume = try {
                AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING
            } catch (_: Exception) {
                false
            }
            if (shouldResume) {
                try {
                    AL10.alSourcePause(source)
                } catch (_: Exception) {}
            }
            thread.seekResumeAfter = shouldResume
            thread.seekRequestedMs = ms
        } else {
            try { AL10.alSourcef(source, AL11.AL_SEC_OFFSET, ms / 1000f) } catch (_: Exception) {}
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
        val thread = activeStreams.remove(source)
        if (thread != null) {
            thread.stopRequested = true
        } else {
            try {
                AL10.alSourceStop(source)
                val buffer = AL10.alGetSourcei(source, AL10.AL_BUFFER)
                AL10.alDeleteSources(source)
                if (buffer != 0) AL10.alDeleteBuffers(buffer)
            } catch (e: Exception) {
                dev.mcrib884.musync.MuSyncLog.error("Error stopping custom track: ${e.message}")
            }
        }
    }
}
