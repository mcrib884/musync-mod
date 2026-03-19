package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.SampleBuffer
import java.io.ByteArrayInputStream
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

    class Mp3Stream(audioData: ByteArray, private val shouldCancel: () -> Boolean = { false }) : AudioStream {
        private data class SeekPoint(val timeMs: Long, val byteOffset: Int)

        override var format: Int = 0
        override var sampleRate: Int = 0
        override var durationMs: Long = 0

        private var sourceData: ByteArray? = audioData
        private var bitstream: Bitstream? = null
        private var decoder: Decoder? = null
        private var pendingPcmData: ByteArray? = null
        private var pendingOffset: Int = 0
        private var channels: Int = 0
        private var frameSize: Int = 0
        private val seekPoints = ArrayList<SeekPoint>()

        init {
            reopenDecoder()
            val firstHeaderDuration = LongArray(1)
            val firstOutput = decodeNextFrame { header ->
                val data = sourceData
                firstHeaderDuration[0] = if (data != null) header.total_ms(data.size).toLong() else -1L
            } ?: throw Exception("MP3: failed to decode any audio frames")

            sampleRate = firstOutput.sampleFrequency
            channels = firstOutput.channelCount
            frameSize = channels * 2
            format = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
            durationMs = if (firstHeaderDuration[0] > 0) firstHeaderDuration[0] else -1L
            buildSeekIndex()

            pendingPcmData = encodePcm(firstOutput)
            val initialPcm = pendingPcmData
            if (sampleRate == 0 || channels == 0 || initialPcm == null || initialPcm.isEmpty()) {
                throw Exception("MP3: failed to decode any audio frames")
            }
        }

        override fun readPcm(buffer: ByteBuffer): Int {
            if (frameSize <= 0) return 0
            var totalRead = 0

            while (buffer.remaining() >= frameSize) {
                val pending = pendingPcmData
                if (pending != null) {
                    val remaining = pending.size - pendingOffset
                    if (remaining > 0) {
                        val toRead = minOf(buffer.remaining(), remaining)
                        val aligned = toRead - (toRead % frameSize)
                        if (aligned <= 0) break
                        buffer.put(pending, pendingOffset, aligned)
                        pendingOffset += aligned
                        totalRead += aligned
                        if (pendingOffset >= pending.size) {
                            pendingPcmData = null
                            pendingOffset = 0
                        }
                        continue
                    }
                    pendingPcmData = null
                    pendingOffset = 0
                }

                val nextOutput = decodeNextFrame() ?: break
                val nextPcm = encodePcm(nextOutput)
                if (nextPcm.isEmpty()) break
                pendingPcmData = nextPcm
            }

            return totalRead
        }

        override fun seekMs(ms: Long) {
            val data = sourceData ?: return
            if (frameSize <= 0 || sampleRate <= 0) return
            val targetMs = ms.coerceAtLeast(0L)
            val seekPoint = findSeekPoint(targetMs)

            reopenDecoder(data, seekPoint.byteOffset)
            if (targetMs <= seekPoint.timeMs) return

            var currentMs = seekPoint.timeMs
            while (currentMs < targetMs) {
                val output = decodeNextFrame() ?: break
                val outputFrames = output.bufferLength / output.channelCount
                if (outputFrames <= 0) break
                val frameDurationMs = ((outputFrames.toLong() * 1000L) / sampleRate.toLong()).coerceAtLeast(1L)
                val remainingMs = targetMs - currentMs
                if (remainingMs >= frameDurationMs) {
                    currentMs += frameDurationMs
                    continue
                }

                val skipFrames = ((remainingMs * sampleRate.toLong()) / 1000L).toInt().coerceAtMost(outputFrames)
                pendingPcmData = encodePcm(output, skipFrames)
                pendingOffset = 0
                break
            }
        }

        private fun reopenDecoder(data: ByteArray? = sourceData, offset: Int = 0) {
            val actualData = data ?: return
            val safeOffset = offset.coerceIn(0, actualData.size)
            closeDecoder()
            bitstream = Bitstream(ByteArrayInputStream(actualData, safeOffset, actualData.size - safeOffset))
            decoder = Decoder()
            pendingPcmData = null
            pendingOffset = 0
        }

        private fun buildSeekIndex() {
            val data = sourceData ?: return
            seekPoints.clear()

            val indexBitstream = Bitstream(ByteArrayInputStream(data))
            val id3v2Offset = indexBitstream.header_pos().coerceAtLeast(0)
            seekPoints.add(SeekPoint(0L, id3v2Offset))

            var indexedTimeMs = 0L
            var byteOffset = id3v2Offset
            var nextCheckpointMs = SEEK_INDEX_INTERVAL_MS
            try {
                while (true) {
                    if (shouldCancel()) throw CancellationException()
                    val header = try {
                        indexBitstream.readFrame()
                    } catch (_: javazoom.jl.decoder.BitstreamException) {
                        break
                    } ?: break

                    try {
                        if (indexedTimeMs >= nextCheckpointMs) {
                            seekPoints.add(SeekPoint(indexedTimeMs, byteOffset))
                            nextCheckpointMs += SEEK_INDEX_INTERVAL_MS
                        }

                        val frameMs = header.ms_per_frame().toLong().coerceAtLeast(1L)
                        indexedTimeMs += frameMs
                        val frameSizeBytes = header.calculate_framesize()
                        if (frameSizeBytes > 0) {
                            byteOffset += frameSizeBytes
                        }
                    } finally {
                        try {
                            indexBitstream.closeFrame()
                        } catch (_: Exception) {}
                    }
                }
            } finally {
                try {
                    indexBitstream.close()
                } catch (_: Exception) {}
            }

            if (durationMs <= 0 && indexedTimeMs > 0) {
                durationMs = indexedTimeMs
            }
        }

        private fun findSeekPoint(targetMs: Long): SeekPoint {
            var low = 0
            var high = seekPoints.size - 1
            var best = seekPoints.firstOrNull() ?: SeekPoint(0L, 0)

            while (low <= high) {
                val mid = (low + high) ushr 1
                val candidate = seekPoints[mid]
                if (candidate.timeMs <= targetMs) {
                    best = candidate
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return best
        }

        private fun closeDecoder() {
            val currentBitstream = bitstream
            bitstream = null
            decoder = null
            if (currentBitstream != null) {
                try {
                    currentBitstream.close()
                } catch (_: Exception) {}
            }
        }

        private fun decodeNextFrame(onHeader: ((Header) -> Unit)? = null): SampleBuffer? {
            val currentBitstream = bitstream ?: return null
            val currentDecoder = decoder ?: return null

            while (true) {
                if (shouldCancel()) throw CancellationException()
                val header = try {
                    currentBitstream.readFrame()
                } catch (_: javazoom.jl.decoder.BitstreamException) {
                    return null
                } ?: return null

                try {
                    onHeader?.invoke(header)
                    val output = currentDecoder.decodeFrame(header, currentBitstream) as? SampleBuffer
                    if (output != null && output.bufferLength > 0) {
                        return output
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        currentBitstream.closeFrame()
                    } catch (_: Exception) {}
                }
            }
        }

        private fun encodePcm(output: SampleBuffer, startFrame: Int = 0): ByteArray {
            val startIndex = (startFrame * output.channelCount).coerceIn(0, output.bufferLength)
            val sampleCount = output.bufferLength - startIndex
            if (sampleCount <= 0) return ByteArray(0)

            val samples = output.buffer
            val pcmBytes = ByteArray(sampleCount * 2)
            var pcmOffset = 0
            for (i in startIndex until output.bufferLength) {
                val sample = samples[i].toInt()
                pcmBytes[pcmOffset++] = sample.toByte()
                pcmBytes[pcmOffset++] = (sample ushr 8).toByte()
            }
            return pcmBytes
        }

        override fun close() {
            closeDecoder()
            sourceData = null
            pendingPcmData = null
            pendingOffset = 0
            channels = 0
            frameSize = 0
            seekPoints.clear()
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
            prepareStream(oggBytes)
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Error loading resource audio: ${e.message}")
            null
        }
    }

    fun prepareStream(file: File, shouldCancel: () -> Boolean = { false }): AudioStream? {
        if (!file.isFile || file.length() < 4L) return null
        val magicBytes = readFileMagic(file)
        if (magicBytes.size < 4) return null
        val magic = String(magicBytes, 0, 4, Charsets.US_ASCII)
        val hintedExt = file.extension.lowercase()
        return try {
            when {
                hintedExt == "wav" || magic == "RIFF" -> WavFileStream(file)
                hintedExt == "mp3" || looksLikeMp3(magicBytes) -> Mp3Stream(file.readBytes(), shouldCancel)
                hintedExt == "ogg" || magic == "OggS" -> OggStream(file.readBytes())
                else -> null
            }
        } catch (_: CancellationException) {
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
                hintedExt == "mp3" || looksLikeMp3(audioData) -> Mp3Stream(audioData, shouldCancel)
                hintedExt == "ogg" || magic == "OggS" -> OggStream(audioData)
                else -> null
            }
        } catch (_: CancellationException) {
            null
        } catch (e: Throwable) {
            dev.mcrib884.musync.MuSyncLog.error("Error preparing audio stream: ${e.message}")
            null
        }
    }

    private fun looksLikeMp3(data: ByteArray): Boolean {
        if (data.size < 2) return false
        if (data.size >= 3 && data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
            return true
        }
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        return b0 == 0xFF && (b1 and 0xE0) == 0xE0
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
    private const val SEEK_INDEX_INTERVAL_MS = 5000L

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
