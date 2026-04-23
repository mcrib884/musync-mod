package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException

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
            val buffer = MemoryUtil.memAlloc(oggData.size)
            inputBuffer = buffer
            try {
                buffer.put(oggData)
                buffer.flip()

                MemoryStack.stackPush().use { stack ->
                    val error = stack.mallocInt(1)
                    handle = STBVorbis.stb_vorbis_open_memory(buffer, error, null)
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
            } catch (e: Exception) {
                MemoryUtil.memFree(buffer)
                inputBuffer = null
                throw e
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
            val buffer = inputBuffer
            if (buffer != null) {
                MemoryUtil.memFree(buffer)
                inputBuffer = null
            }
        }
    }

    class OggFileStream(file: File) : AudioStream {
        private var handle: Long = 0
        override var format: Int = 0
        override var sampleRate: Int = 0
        override var durationMs: Long = 0
        private var channels: Int = 0

        init {
            MemoryStack.stackPush().use { stack ->
                val error = stack.mallocInt(1)
                handle = STBVorbis.stb_vorbis_open_filename(file.absolutePath, error, null)
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

    class WavFileStream(file: File) : AudioStream {
        override var format: Int = 0
        override var sampleRate: Int = 0
        override var durationMs: Long = 0
        private var dataOffset: Long = 0L
        private var dataSize: Long = 0L
        private var currentPos: Long = 0L
        private var frameSize: Int = 0
        private var wavFile: RandomAccessFile? = null
        private var readBuffer = ByteArray(0)

        init {
            val raf = RandomAccessFile(file, "r")
            wavFile = raf
            try {
                if (raf.length() < 12L) {
                    throw Exception("WAV: file too small")
                }

                raf.seek(12L)
                var channels = 0
                var bitsPerSample = 0

                while (raf.filePointer <= raf.length() - 8L) {
                    val chunkId = ByteArray(4)
                    raf.readFully(chunkId)
                    val chunkSize = readLittleEndianInt(raf).toLong() and 0xFFFFFFFFL
                    val chunkStart = raf.filePointer
                    if (chunkSize > raf.length() - chunkStart) {
                        throw Exception("WAV: invalid chunk size $chunkSize")
                    }

                    when (String(chunkId, Charsets.US_ASCII)) {
                        "fmt " -> {
                            if (chunkSize < 16L) throw Exception("WAV: unsupported format")
                            val audioFormat = readLittleEndianShort(raf)
                            if (audioFormat != 1) throw Exception("WAV: unsupported format")
                            channels = readLittleEndianShort(raf)
                            sampleRate = readLittleEndianInt(raf)
                            readLittleEndianInt(raf)
                            readLittleEndianShort(raf)
                            bitsPerSample = readLittleEndianShort(raf)
                        }
                        "data" -> {
                            dataOffset = raf.filePointer
                            dataSize = chunkSize
                        }
                    }

                    raf.seek(chunkStart + chunkSize + (chunkSize and 1L))
                }

                if (channels == 0 || sampleRate == 0 || bitsPerSample == 0 || dataSize == 0L) {
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
                val totalSamples = dataSize / frameSize.toLong()
                durationMs = (totalSamples * 1000L) / sampleRate.toLong()
            } catch (e: Exception) {
                close()
                throw e
            }
        }

        override fun readPcm(buffer: ByteBuffer): Int {
            val currentFile = wavFile ?: return 0
            val remainingInStream = dataSize - currentPos
            if (remainingInStream <= 0L) return 0

            val toRead = minOf(buffer.remaining().toLong(), remainingInStream).toInt()
            val alignedRead = toRead - (toRead % frameSize)
            if (alignedRead <= 0) return 0

            if (readBuffer.size < alignedRead) {
                readBuffer = ByteArray(alignedRead)
            }

            val absolutePos = dataOffset + currentPos
            if (currentFile.filePointer != absolutePos) {
                currentFile.seek(absolutePos)
            }
            currentFile.readFully(readBuffer, 0, alignedRead)
            buffer.put(readBuffer, 0, alignedRead)
            currentPos += alignedRead.toLong()
            return alignedRead
        }

        override fun seekMs(ms: Long) {
            val targetSample = (ms * sampleRate) / 1000L
            val targetPos = targetSample * frameSize.toLong()
            currentPos = targetPos.coerceIn(0L, dataSize)
            currentPos -= currentPos % frameSize.toLong()
        }

        override fun close() {
            try {
                wavFile?.close()
            } catch (_: Exception) {}
            wavFile = null
            readBuffer = ByteArray(0)
        }
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
            OggStream(oggBytes)
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Error loading resource audio: ${e.message}")
            null
        }
    }

    fun prepareStream(file: File, shouldCancel: () -> Boolean = { false }): AudioStream? {
        if (!file.isFile || file.length() < 4L) {
            dev.mcrib884.musync.MuSyncLog.debug("prepareStream: file check failed (exists=${file.isFile}, size=${if (file.isFile) file.length() else -1}): ${file.name}")
            return null
        }
        val magicBytes = readFileMagic(file)
        if (magicBytes.size < 4) return null
        val magic = String(magicBytes, 0, 4, Charsets.US_ASCII)
        val hintedExt = file.extension.lowercase()
        return try {
            when {
                hintedExt == "wav" || magic == "RIFF" -> WavFileStream(file)
                hintedExt == "ogg" || magic == "OggS" -> OggStream(file.readBytes())
                isLikelyMp3(magicBytes, hintedExt) -> prepareMp3Stream(file, shouldCancel)
                else -> null
            }
        } catch (_: CancellationException) {
            dev.mcrib884.musync.MuSyncLog.debug("prepareStream: cancelled during decode of ${file.name}")
            null
        } catch (e: Throwable) {
            dev.mcrib884.musync.MuSyncLog.error("Error preparing audio stream: ${e.message}")
            null
        }
    }

    fun prepareStream(audioData: ByteArray, sourceName: String? = null, shouldCancel: () -> Boolean = { false }): AudioStream? {
        if (audioData.size < 4) return null
        val magic = String(audioData, 0, 4, Charsets.US_ASCII)
        val hintedExt = sourceName?.substringAfterLast('.', "")?.lowercase()
        return try {
            when {
                hintedExt == "wav" || magic == "RIFF" -> WavStream(audioData)
                hintedExt == "ogg" || magic == "OggS" -> OggStream(audioData)
                isLikelyMp3(audioData, hintedExt) -> prepareMp3Stream(audioData, sourceName, shouldCancel)
                else -> null
            }
        } catch (_: CancellationException) {
            null
        } catch (e: Throwable) {
            dev.mcrib884.musync.MuSyncLog.error("Error preparing audio stream: ${e.message}")
            null
        }
    }

    private fun prepareMp3Stream(file: File, shouldCancel: () -> Boolean): AudioStream {
        return Mp3AudioSupport.openFile(file, shouldCancel)
    }

    private fun prepareMp3Stream(audioData: ByteArray, sourceName: String?, shouldCancel: () -> Boolean): AudioStream {
        return Mp3AudioSupport.openBytes(audioData, sourceName, shouldCancel)
    }

    private fun isLikelyMp3(headerBytes: ByteArray, hintedExt: String?): Boolean {
        if (hintedExt == "mp3") return true
        if (headerBytes.size >= 3 && headerBytes[0] == 'I'.code.toByte() && headerBytes[1] == 'D'.code.toByte() && headerBytes[2] == '3'.code.toByte()) {
            return true
        }
        if (headerBytes.size >= 2) {
            val first = headerBytes[0].toInt() and 0xFF
            val second = headerBytes[1].toInt() and 0xE0
            if (first == 0xFF && second == 0xE0) {
                return true
            }
        }
        return false
    }

    private fun readFileMagic(file: File): ByteArray {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val size = minOf(4L, raf.length()).toInt()
                val bytes = ByteArray(size)
                raf.readFully(bytes)
                bytes
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun readLittleEndianShort(file: RandomAccessFile): Int {
        val b0 = file.readUnsignedByte()
        val b1 = file.readUnsignedByte()
        return b0 or (b1 shl 8)
    }

    private fun readLittleEndianInt(file: RandomAccessFile): Int {
        val b0 = file.readUnsignedByte()
        val b1 = file.readUnsignedByte()
        val b2 = file.readUnsignedByte()
        val b3 = file.readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private const val BUFFER_SIZE = 65536
    private const val BUFFER_COUNT = 4
    private const val STOP_WAIT_MS = 250L

    private fun closeQuietly(stream: AudioStream?) {
        if (stream == null) return
        try {
            stream.close()
        } catch (_: Exception) {}
    }

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
        @Volatile var seekInProgress = false

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
                        seekInProgress = true
                        try {
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
                        } finally {
                            seekInProgress = false
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

            } catch (_: InterruptedException) {
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
        var source = 0
        return try {
            source = AL10.alGenSources()
            val thread = StreamThread(stream, source, seekMs, gain ?: 1.0f, startPaused)
            activeStreams[source] = thread
            thread.isDaemon = true
            thread.start()
            source
        } catch (e: Exception) {
            if (source != 0) {
                try {
                    AL10.alDeleteSources(source)
                } catch (_: Exception) {}
            }
            closeQuietly(stream)
            dev.mcrib884.musync.MuSyncLog.error("Error starting stream: ${e.message}")
            -1
        }
    }

    fun seek(source: Int, ms: Long, resume: Boolean = true) {
        val thread = activeStreams[source]
        if (thread != null) {
            thread.seekInProgress = true
            try { AL10.alSourcePause(source) } catch (_: Exception) {}
            thread.seekResumeAfter = resume
            thread.seekRequestedMs = ms
        } else {
            try { AL10.alSourcef(source, AL11.AL_SEC_OFFSET, ms / 1000f) } catch (_: Exception) {}
        }
    }

    fun isPlaying(source: Int): Boolean {
        if (source == -1) return false
        val thread = activeStreams[source]
        if (thread != null && thread.seekInProgress) return true
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
            thread.interrupt()
            if (Thread.currentThread() !== thread) {
                try {
                    thread.join(STOP_WAIT_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
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

    fun stopAll() {
        val sources = activeStreams.keys.toList()
        for (source in sources) {
            stop(source)
        }
    }
}
