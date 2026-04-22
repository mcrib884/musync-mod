package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVChannelLayout
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.swresample.SwrContext
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swresample.*
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Loader
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import java.io.File
import java.lang.management.ManagementFactory
import java.io.RandomAccessFile
import java.nio.file.Files
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

    class FfmpegCompressedStream private constructor(
        private var inputFile: File?,
        private val deleteInputFileOnClose: Boolean,
        private val shouldCancel: () -> Boolean = { false }
    ) : AudioStream {
        override var format: Int = 0
        override var sampleRate: Int = 0
        override var durationMs: Long = -1L

        private var outputChannels: Int = 0
        private var frameSize: Int = 0
        private var streamIndex: Int = -1
        private var formatContext: AVFormatContext? = null
        private var stream: AVStream? = null
        private var codecContext: AVCodecContext? = null
        private var packet: AVPacket? = null
        private var frame: AVFrame? = null
        private var swrContext: SwrContext? = null
        private var pendingPcmData: ByteArray? = null
        private var pendingOffset: Int = 0
        private var outputBuffer: BytePointer? = null
        private var outputBufferCapacity: Int = 0
        private var inputExhausted = false
        private var decoderFlushed = false

        constructor(file: File, shouldCancel: () -> Boolean = { false }) : this(file, false, shouldCancel)

        constructor(audioData: ByteArray, sourceName: String? = null, shouldCancel: () -> Boolean = { false }) :
            this(stageInputData(audioData, sourceName), true, shouldCancel)

        init {
            ensureFfmpegInitialized()
            openInput(inputFile ?: throw Exception("FFmpeg: missing input file"))
        }

        override fun readPcm(buffer: ByteBuffer): Int {
            if (frameSize <= 0) return 0

            var totalRead = 0
            while (buffer.remaining() >= frameSize) {
                val pending = pendingPcmData
                if (pending != null) {
                    val available = pending.size - pendingOffset
                    if (available > 0) {
                        val toCopy = minOf(buffer.remaining(), available)
                        val alignedCopy = toCopy - (toCopy % frameSize)
                        if (alignedCopy <= 0) break

                        buffer.put(pending, pendingOffset, alignedCopy)
                        pendingOffset += alignedCopy
                        totalRead += alignedCopy
                        if (pendingOffset >= pending.size) {
                            pendingPcmData = null
                            pendingOffset = 0
                        }
                        continue
                    }

                    pendingPcmData = null
                    pendingOffset = 0
                }

                val nextChunk = decodeNextChunk() ?: break
                if (nextChunk.isEmpty()) break
                pendingPcmData = nextChunk
            }

            return totalRead
        }

        override fun seekMs(ms: Long) {
            val currentFormat = formatContext ?: return
            val currentCodec = codecContext ?: return
            val currentStream = stream ?: return
            val currentSwr = swrContext ?: return
            val currentPacket = packet
            val currentFrame = frame

            val millisTimeBase = AVRational().num(1).den(1000)
            val targetTimestamp = av_rescale_q(ms.coerceAtLeast(0L), millisTimeBase, currentStream.time_base())
            millisTimeBase.close()
            val seekResult = av_seek_frame(currentFormat, streamIndex, targetTimestamp, AVSEEK_FLAG_BACKWARD)
            if (seekResult < 0) {
                throw Exception("FFmpeg: seek failed ($seekResult)")
            }

            avcodec_flush_buffers(currentCodec)
            swr_close(currentSwr)
            val reinitResult = swr_init(currentSwr)
            if (reinitResult < 0) {
                throw Exception("FFmpeg: failed to reinitialize resampler after seek ($reinitResult)")
            }

            if (currentPacket != null) {
                av_packet_unref(currentPacket)
            }
            if (currentFrame != null) {
                av_frame_unref(currentFrame)
            }

            pendingPcmData = null
            pendingOffset = 0
            inputExhausted = false
            decoderFlushed = false
        }

        private fun openInput(file: File) {
            val openedFormatContext = avformat_alloc_context() ?: throw Exception("FFmpeg: failed to allocate format context")
            try {
                openedFormatContext.seek2any(1)

                val openResult = avformat_open_input(openedFormatContext, file.absolutePath, null, null)
                if (openResult < 0) {
                    throw Exception("FFmpeg: failed to open input ($openResult)")
                }

                val infoResult = avformat_find_stream_info(openedFormatContext, null as PointerPointer<Pointer>?)
                if (infoResult < 0) {
                    throw Exception("FFmpeg: failed to read stream info ($infoResult)")
                }

                val openedStreamIndex = findAudioStreamIndex(openedFormatContext)
                val openedStream = openedFormatContext.streams(openedStreamIndex)
                val codecParameters = openedStream.codecpar()
                val codec = avcodec_find_decoder(codecParameters.codec_id())
                    ?: throw Exception("FFmpeg: no decoder found for codec id ${codecParameters.codec_id()}")

                val openedCodecContext = avcodec_alloc_context3(codec)
                    ?: throw Exception("FFmpeg: failed to allocate codec context")
                try {
                    val parametersResult = avcodec_parameters_to_context(openedCodecContext, codecParameters)
                    if (parametersResult < 0) {
                        throw Exception("FFmpeg: failed to copy codec parameters ($parametersResult)")
                    }

                    openedCodecContext.thread_count(1)
                    val codecOpenResult = avcodec_open2(openedCodecContext, codec, null as PointerPointer<Pointer>?)
                    if (codecOpenResult < 0) {
                        throw Exception("FFmpeg: failed to open decoder ($codecOpenResult)")
                    }

                    val detectedSampleRate = openedCodecContext.sample_rate()
                    val detectedChannels = openedCodecContext.ch_layout().nb_channels().coerceAtLeast(1)
                    if (detectedSampleRate <= 0) {
                        throw Exception("FFmpeg: decoder reported invalid sample rate $detectedSampleRate")
                    }

                    sampleRate = detectedSampleRate
                    outputChannels = if (detectedChannels == 1) 1 else 2
                    frameSize = outputChannels * 2
                    format = if (outputChannels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
                    durationMs = resolveDurationMs(openedFormatContext, openedStream)

                    val openedSwr = createResampler(openedCodecContext, detectedChannels)
                    val openedPacket = av_packet_alloc() ?: throw Exception("FFmpeg: failed to allocate packet")
                    val openedFrame = av_frame_alloc() ?: throw Exception("FFmpeg: failed to allocate frame")

                    formatContext = openedFormatContext
                    stream = openedStream
                    streamIndex = openedStreamIndex
                    codecContext = openedCodecContext
                    swrContext = openedSwr
                    packet = openedPacket
                    frame = openedFrame
                    inputExhausted = false
                    decoderFlushed = false
                } catch (e: Exception) {
                    avcodec_free_context(openedCodecContext)
                    throw e
                }
            } catch (e: Exception) {
                avformat_close_input(openedFormatContext)
                throw e
            }
        }

        private fun findAudioStreamIndex(openedFormatContext: AVFormatContext): Int {
            for (index in 0 until openedFormatContext.nb_streams()) {
                val candidate = openedFormatContext.streams(index)
                if (candidate.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
                    return index
                }
            }
            throw Exception("FFmpeg: no audio stream found")
        }

        private fun createResampler(openedCodecContext: AVCodecContext, inputChannels: Int): SwrContext {
            val inputLayout = resolveChannelLayout(openedCodecContext, inputChannels)
            val outputLayout = defaultChannelLayout(outputChannels)
            val openedSwr = swr_alloc()
                ?: throw Exception("FFmpeg: failed to allocate resampler")

            try {
                val inLayoutResult = av_opt_set_chlayout(openedSwr, "in_chlayout", inputLayout, 0)
                if (inLayoutResult < 0) {
                    throw Exception("FFmpeg: failed to set input channel layout ($inLayoutResult)")
                }

                val outLayoutResult = av_opt_set_chlayout(openedSwr, "out_chlayout", outputLayout, 0)
                if (outLayoutResult < 0) {
                    throw Exception("FFmpeg: failed to set output channel layout ($outLayoutResult)")
                }

                av_opt_set_int(openedSwr, "in_sample_rate", openedCodecContext.sample_rate().toLong(), 0)
                av_opt_set_int(openedSwr, "out_sample_rate", sampleRate.toLong(), 0)
                av_opt_set_sample_fmt(openedSwr, "in_sample_fmt", openedCodecContext.sample_fmt(), 0)
                av_opt_set_sample_fmt(openedSwr, "out_sample_fmt", AV_SAMPLE_FMT_S16, 0)

                val initResult = swr_init(openedSwr)
                if (initResult < 0) {
                    throw Exception("FFmpeg: failed to initialize resampler ($initResult)")
                }

                return openedSwr
            } catch (e: Exception) {
                swr_free(openedSwr)
                throw e
            } finally {
                av_channel_layout_uninit(inputLayout)
                inputLayout.close()
                av_channel_layout_uninit(outputLayout)
                outputLayout.close()
            }
        }

        private fun resolveChannelLayout(openedCodecContext: AVCodecContext, inputChannels: Int): AVChannelLayout {
            val codecLayout = openedCodecContext.ch_layout()
            if (codecLayout.nb_channels() > 0 && codecLayout.order() != AV_CHANNEL_ORDER_UNSPEC) {
                val copiedLayout = AVChannelLayout()
                val copyResult = av_channel_layout_copy(copiedLayout, codecLayout)
                if (copyResult < 0) {
                    copiedLayout.close()
                    throw Exception("FFmpeg: failed to copy input channel layout ($copyResult)")
                }
                return copiedLayout
            }

            return defaultChannelLayout(inputChannels)
        }

        private fun defaultChannelLayout(channelCount: Int): AVChannelLayout {
            val layout = AVChannelLayout()
            av_channel_layout_default(layout, channelCount)
            if (layout.nb_channels() <= 0) {
                layout.close()
                throw Exception("FFmpeg: unsupported channel count $channelCount")
            }
            return layout
        }

        private fun resolveDurationMs(openedFormatContext: AVFormatContext, openedStream: AVStream): Long {
            val formatDuration = openedFormatContext.duration()
            if (formatDuration > 0L && formatDuration != AV_NOPTS_VALUE.toLong()) {
                return formatDuration / 1000L
            }

            val streamDuration = openedStream.duration()
            if (streamDuration > 0L && streamDuration != AV_NOPTS_VALUE.toLong()) {
                val millisTimeBase = AVRational().num(1).den(1000)
                val durationMs = av_rescale_q(streamDuration, openedStream.time_base(), millisTimeBase)
                millisTimeBase.close()
                return durationMs
            }

            return -1L
        }

        private fun decodeNextChunk(): ByteArray? {
            val currentCodec = codecContext ?: return null
            val currentFormat = formatContext ?: return null
            val currentPacket = packet ?: return null
            val currentFrame = frame ?: return null

            while (true) {
                if (shouldCancel()) throw CancellationException()

                val receiveResult = avcodec_receive_frame(currentCodec, currentFrame)
                when {
                    receiveResult >= 0 -> {
                        try {
                            val chunk = convertFrame(currentFrame)
                            if (chunk.isNotEmpty()) {
                                return chunk
                            }
                        } finally {
                            av_frame_unref(currentFrame)
                        }
                    }
                    receiveResult == AVERROR_EOF -> return null
                    receiveResult != AVERROR_EAGAIN() -> throw Exception("FFmpeg: decode failed ($receiveResult)")
                    else -> {
                        if (!inputExhausted) {
                            val queuedPacket = queueNextPacket(currentFormat, currentCodec, currentPacket)
                            if (queuedPacket) {
                                continue
                            }
                            inputExhausted = true
                        }

                        if (!decoderFlushed) {
                            val flushResult = avcodec_send_packet(currentCodec, null as AVPacket?)
                            if (flushResult < 0 && flushResult != AVERROR_EOF && flushResult != AVERROR_EAGAIN()) {
                                throw Exception("FFmpeg: decoder flush failed ($flushResult)")
                            }
                            decoderFlushed = true
                            continue
                        }

                        return null
                    }
                }
            }
        }

        private fun queueNextPacket(currentFormat: AVFormatContext, currentCodec: AVCodecContext, currentPacket: AVPacket): Boolean {
            while (true) {
                if (shouldCancel()) throw CancellationException()

                val readResult = av_read_frame(currentFormat, currentPacket)
                if (readResult < 0) {
                    av_packet_unref(currentPacket)
                    return false
                }

                if (currentPacket.stream_index() != streamIndex) {
                    av_packet_unref(currentPacket)
                    continue
                }

                val sendResult = avcodec_send_packet(currentCodec, currentPacket)
                av_packet_unref(currentPacket)
                if (sendResult == AVERROR_EAGAIN()) {
                    return true
                }
                if (sendResult < 0) {
                    throw Exception("FFmpeg: failed to queue packet ($sendResult)")
                }
                return true
            }
        }

        private fun convertFrame(currentFrame: AVFrame): ByteArray {
            val currentSwr = swrContext ?: return ByteArray(0)
            val sampleCount = currentFrame.nb_samples()
            if (sampleCount <= 0) return ByteArray(0)

            val maxOutputSamples = swr_get_out_samples(currentSwr, sampleCount)
            if (maxOutputSamples <= 0) return ByteArray(0)

            val requiredBytes = maxOutputSamples * outputChannels * 2
            ensureOutputBufferCapacity(requiredBytes)
            val currentOutputBuffer = outputBuffer ?: return ByteArray(0)

            val outputPointers = PointerPointer<Pointer>(1L)
            outputPointers.put(0, currentOutputBuffer)

            val convertedSamples = swr_convert(currentSwr, outputPointers, maxOutputSamples, currentFrame.extended_data(), sampleCount)
            if (convertedSamples < 0) {
                throw Exception("FFmpeg: sample conversion failed ($convertedSamples)")
            }

            val actualBytes = convertedSamples * outputChannels * 2
            if (actualBytes <= 0) return ByteArray(0)

            val pcmBytes = ByteArray(actualBytes)
            currentOutputBuffer.position(0)
            currentOutputBuffer.get(pcmBytes, 0, actualBytes)
            currentOutputBuffer.position(0)
            return pcmBytes
        }

        private fun ensureOutputBufferCapacity(requiredBytes: Int) {
            if (requiredBytes <= outputBufferCapacity) return

            val previousBuffer = outputBuffer
            outputBuffer = BytePointer(requiredBytes.toLong())
            outputBufferCapacity = requiredBytes
            if (previousBuffer != null) {
                previousBuffer.close()
            }
        }

        override fun close() {
            pendingPcmData = null
            pendingOffset = 0
            inputExhausted = true
            decoderFlushed = true

            val currentPacket = packet
            packet = null
            if (currentPacket != null) {
                av_packet_free(currentPacket)
            }

            val currentFrame = frame
            frame = null
            if (currentFrame != null) {
                av_frame_free(currentFrame)
            }

            val currentSwr = swrContext
            swrContext = null
            if (currentSwr != null) {
                swr_free(currentSwr)
            }

            val currentCodec = codecContext
            codecContext = null
            if (currentCodec != null) {
                avcodec_free_context(currentCodec)
            }

            val currentFormat = formatContext
            formatContext = null
            stream = null
            if (currentFormat != null) {
                avformat_close_input(currentFormat)
            }

            val currentOutputBuffer = outputBuffer
            outputBuffer = null
            outputBufferCapacity = 0
            if (currentOutputBuffer != null) {
                currentOutputBuffer.close()
            }

            val currentInputFile = inputFile
            inputFile = null
            if (deleteInputFileOnClose && currentInputFile != null) {
                currentInputFile.delete()
            }
        }

        companion object {
            @Volatile
            private var ffmpegInitialized = false
            @Volatile
            private var ffmpegAvailable = true

            internal fun isFfmpegAvailable(): Boolean {
                if (!ffmpegAvailable) return false
                if (ffmpegInitialized) return true
                // Try to init — will set ffmpegAvailable = false if bytedeco is missing
                return try {
                    ensureFfmpegInitialized()
                    true
                } catch (_: Throwable) {
                    false
                }
            }

            private fun ensureFfmpegInitialized() {
                if (ffmpegInitialized) return
                if (!ffmpegAvailable) throw IllegalStateException("FFmpeg not available")
                synchronized(this) {
                    if (ffmpegInitialized) return
                    if (!ffmpegAvailable) throw IllegalStateException("FFmpeg not available")

                    try {
                        // Manually extract and load native DLLs to a single flat directory.
                        // JavaCPP's Loader splits DLLs across artifact subdirectories, and the
                        // OS can't resolve dependent DLLs across directories. We bypass Loader
                        // entirely for native loading.
                        loadFfmpegNatives()
                    } catch (e: NoClassDefFoundError) {
                        ffmpegAvailable = false
                        dev.mcrib884.musync.MuSyncLog.warn(
                            "FFmpeg/bytedeco not found. MP3 custom tracks require a mod that provides FFmpeg " +
                            "(e.g. watermedia). WAV and OGG custom tracks still work."
                        )
                        throw e
                    } catch (e: Throwable) {
                        ffmpegAvailable = false
                        val rootCause = generateSequence(e) { it.cause }.last()
                        dev.mcrib884.musync.MuSyncLog.error(
                            "FFmpeg native loading failed: ${e.javaClass.simpleName}: ${e.message}" +
                            if (rootCause !== e) " | Root cause: ${rootCause.javaClass.simpleName}: ${rootCause.message}" else ""
                        )
                        throw e
                    }
                    // These may trigger bytedeco class static initializers which call
                    // Loader.load() internally — wrap them as non-fatal since FFmpeg
                    // decoding still works without these config calls.
                    try { av_log_set_level(AV_LOG_ERROR) } catch (e: Throwable) {
                        dev.mcrib884.musync.MuSyncLog.warn("av_log_set_level failed (non-fatal): ${e.javaClass.simpleName}: ${e.message}")
                    }
                    try { avformat_network_init() } catch (e: Throwable) {
                        dev.mcrib884.musync.MuSyncLog.warn("avformat_network_init failed (non-fatal): ${e.javaClass.simpleName}: ${e.message}")
                    }
                    dev.mcrib884.musync.MuSyncLog.info("FFmpeg native libraries loaded successfully")
                    ffmpegInitialized = true
                }
            }

            /**
             * Load FFmpeg native libraries completely manually, bypassing JavaCPP's
             * Loader which fails in multiple ways in the modded environment:
             *
             * - Loader uses the class's ClassLoader for resource lookup, but bytedeco
             *   classes come from watermedia while DLLs are in our JAR
             * - Loader's preload mechanism can't find dependent DLLs across ClassLoaders
             * - Loader caches its cacheDir in a static field (locked by watermedia)
             * - Class static initializers call Loader.load() and can't be prevented
             *
             * Solution: extract all DLLs to a flat directory, System.load() in order,
             * then register in Loader's loadedLibraries map via reflection so class
             * static initializers find them "already loaded" and skip re-extraction.
             */
            private fun loadFfmpegNatives() {
                val ourCL = FfmpegCompressedStream::class.java.classLoader
                    ?: throw IllegalStateException("No ClassLoader available")
                val platform = detectPlatform()
                val gameDir = net.minecraft.client.Minecraft.getInstance().gameDirectory

                val nativeDir = File(gameDir, "musync-natives/$platform")
                nativeDir.mkdirs()

                // Step 1: Extract ALL DLLs to a single flat directory
                extractAllNatives(ourCL, platform, nativeDir)

                // Step 2: Pre-register in BOTH Loader maps BEFORE System.load().
                // Loader.load(Class) checks "foundLibraries" (class name → path) first.
                // If found, it returns immediately — NO extraction, NO System.load, NOTHING.
                // This is critical because System.load(jniavutil.dll) triggers JNI_OnLoad
                // → class clinit → Loader.load() recursively. Without pre-registration,
                // the recursive Loader.load() tries to extract files that are locked.
                val jniPrefix = if (platform.startsWith("windows")) "jni" else "libjni"
                val jniSuffix = if (platform.startsWith("windows")) ".dll" else ".so"
                val classMap = mapOf(
                    "avutil" to "org.bytedeco.ffmpeg.global.avutil",
                    "avcodec" to "org.bytedeco.ffmpeg.global.avcodec",
                    "avformat" to "org.bytedeco.ffmpeg.global.avformat",
                    "swresample" to "org.bytedeco.ffmpeg.global.swresample",
                    "javacpp" to "org.bytedeco.javacpp.Loader"
                )

                try {
                    val loaderClass = org.bytedeco.javacpp.Loader::class.java

                    // Register in foundLibraries (class name → path, checked FIRST by Loader.load)
                    @Suppress("UNCHECKED_CAST")
                    val foundField = loaderClass.getDeclaredField("foundLibraries")
                    foundField.isAccessible = true
                    val foundLibs = foundField.get(null) as MutableMap<String, String>

                    // Register in loadedLibraries (library name → path)
                    @Suppress("UNCHECKED_CAST")
                    val loadedField = loaderClass.getDeclaredField("loadedLibraries")
                    loadedField.isAccessible = true
                    val loadedLibs = loadedField.get(null) as MutableMap<String, String>

                    for ((shortName, className) in classMap) {
                        val jniName = "$jniPrefix$shortName$jniSuffix"
                        val libFile = File(nativeDir, jniName)
                        if (libFile.isFile) {
                            val path = libFile.absolutePath
                            // foundLibraries: keyed by class name
                            foundLibs[className] = path
                            // loadedLibraries: keyed by lib name / class name
                            loadedLibs[className] = path
                            loadedLibs["jni$shortName"] = path
                            loadedLibs[jniName] = path
                        }
                    }

                    // Also register all other libs by filename
                    for (libName in getLoadOrder(platform)) {
                        val libFile = File(nativeDir, libName)
                        if (libFile.isFile) {
                            val path = libFile.absolutePath
                            loadedLibs[libName] = path
                            loadedLibs[libName.substringBeforeLast(".")] = path
                        }
                    }

                    dev.mcrib884.musync.MuSyncLog.info(
                        "Pre-registered ${foundLibs.size} entries in foundLibraries, " +
                        "${loadedLibs.size} in loadedLibraries"
                    )
                } catch (e: Exception) {
                    dev.mcrib884.musync.MuSyncLog.warn(
                        "Could not pre-register in Loader maps: ${e.javaClass.simpleName}: ${e.message}"
                    )
                }

                // Step 3: System.load() ALL libraries in dependency order.
                // Class static initializers triggered by JNI_OnLoad will call
                // Loader.load() → finds entry in foundLibraries → returns immediately.
                // Suppress stderr during loading to hide harmless putMemberOffset
                // warnings from JavaCPP (version mismatch with watermedia's bindings).
                val originalErr = System.err
                System.setErr(java.io.PrintStream(java.io.OutputStream.nullOutputStream()))
                try {
                    for (libName in getLoadOrder(platform)) {
                        val libFile = File(nativeDir, libName)
                        if (!libFile.isFile) continue
                        try {
                            @Suppress("UnsafeDynamicallyLoadedCode")
                            System.load(libFile.absolutePath)
                        } catch (_: UnsatisfiedLinkError) {
                            // Already loaded — fine
                        }
                    }
                } finally {
                    System.setErr(originalErr)
                }
            }



            private fun extractAllNatives(cl: ClassLoader, platform: String, targetDir: File) {
                val prefixes = listOf(
                    "org/bytedeco/javacpp/$platform",
                    "org/bytedeco/ffmpeg/$platform"
                )
                for (prefix in prefixes) {
                    for (libName in getAllNativeNames(platform)) {
                        try {
                            val stream = cl.getResourceAsStream("$prefix/$libName") ?: continue
                            val target = File(targetDir, libName)
                            if (!target.exists() || target.length() == 0L) {
                                stream.use { input ->
                                    target.outputStream().use { output -> input.copyTo(output) }
                                }
                            } else {
                                stream.close()
                            }
                        } catch (_: Exception) { /* skip */ }
                    }
                }
            }

            private fun detectPlatform(): String {
                val os = System.getProperty("os.name", "").lowercase()
                val arch = System.getProperty("os.arch", "").lowercase()
                return when {
                    os.contains("win") && (arch == "amd64" || arch == "x86_64") -> "windows-x86_64"
                    os.contains("linux") && (arch == "amd64" || arch == "x86_64") -> "linux-x86_64"
                    else -> throw UnsupportedOperationException("Unsupported platform: $os/$arch")
                }
            }

            private fun getAllNativeNames(platform: String): List<String> {
                return if (platform.startsWith("windows")) listOf(
                    "api-ms-win-crt-locale-l1-1-0.dll", "api-ms-win-crt-runtime-l1-1-0.dll",
                    "api-ms-win-crt-stdio-l1-1-0.dll", "api-ms-win-crt-math-l1-1-0.dll",
                    "api-ms-win-crt-heap-l1-1-0.dll", "api-ms-win-crt-string-l1-1-0.dll",
                    "api-ms-win-crt-convert-l1-1-0.dll", "api-ms-win-crt-time-l1-1-0.dll",
                    "api-ms-win-crt-environment-l1-1-0.dll", "api-ms-win-crt-process-l1-1-0.dll",
                    "api-ms-win-crt-conio-l1-1-0.dll", "api-ms-win-crt-filesystem-l1-1-0.dll",
                    "api-ms-win-crt-utility-l1-1-0.dll", "api-ms-win-crt-multibyte-l1-1-0.dll",
                    "api-ms-win-crt-private-l1-1-0.dll",
                    "ucrtbase.dll", "vcruntime140.dll", "vcruntime140_1.dll",
                    "vcruntime140_threads.dll",
                    "msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll",
                    "concrt140.dll", "vcomp140.dll", "libomp140.x86_64.dll",
                    "jnijavacpp.dll",
                    "avutil-60.dll", "swresample-6.dll", "avcodec-62.dll", "avformat-62.dll",
                    "jniavutil.dll", "jniswresample.dll", "jniavcodec.dll", "jniavformat.dll"
                ) else listOf(
                    "libjnijavacpp.so",
                    "libavutil.so.60", "libswresample.so.6", "libavcodec.so.62", "libavformat.so.62",
                    "libjniavutil.so", "libjniswresample.so", "libjniavcodec.so", "libjniavformat.so"
                )
            }

            private fun getLoadOrder(platform: String): List<String> {
                return if (platform.startsWith("windows")) listOf(
                    // 1. MSVC/Universal CRT runtime
                    "vcruntime140.dll", "vcruntime140_1.dll", "vcruntime140_threads.dll",
                    "msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll",
                    "ucrtbase.dll", "concrt140.dll", "vcomp140.dll", "libomp140.x86_64.dll",
                    // 2. JavaCPP core JNI
                    "jnijavacpp.dll",
                    // 3. FFmpeg shared libs (dependency order)
                    "avutil-60.dll", "swresample-6.dll", "avcodec-62.dll", "avformat-62.dll",
                    // 4. FFmpeg JNI bridges
                    "jniavutil.dll", "jniswresample.dll", "jniavcodec.dll", "jniavformat.dll"
                ) else listOf(
                    "libjnijavacpp.so",
                    "libavutil.so.60", "libswresample.so.6",
                    "libavcodec.so.62", "libavformat.so.62",
                    "libjniavutil.so", "libjniswresample.so",
                    "libjniavcodec.so", "libjniavformat.so"
                )
            }

            private fun configureJavaCppCacheDir() {
                val propertyName = "org.bytedeco.javacpp.cachedir"
                val configuredPath = System.getProperty(propertyName)
                if (!configuredPath.isNullOrBlank()) return

                val baseDir = resolveJavaCppCacheBaseDir()
                val cacheDir = File(baseDir, resolveProcessCacheDirName())
                try {
                    Files.createDirectories(cacheDir.toPath())
                    System.setProperty(propertyName, cacheDir.absolutePath)
                } catch (e: Exception) {
                    dev.mcrib884.musync.MuSyncLog.warn("Failed to prepare JavaCPP cache dir '${cacheDir.absolutePath}': ${e.message}")
                }
            }

            private fun resolveJavaCppCacheBaseDir(): File {
                return try {
                    File(Minecraft.getInstance().gameDirectory, "musync-native-cache")
                } catch (_: Throwable) {
                    File(System.getProperty("java.io.tmpdir"), "musync-native-cache")
                }
            }

            private fun resolveProcessCacheDirName(): String {
                val runtimeName = try {
                    ManagementFactory.getRuntimeMXBean().name
                } catch (_: Throwable) {
                    ""
                }
                val processId = runtimeName.substringBefore('@').filter { it.isDigit() }.ifBlank { "process" }
                return "ffmpeg-$processId"
            }

            private fun stageInputData(audioData: ByteArray, sourceName: String?): File {
                val extension = sourceName
                    ?.substringAfterLast('.', "")
                    ?.lowercase()
                    ?.takeIf { it.isNotBlank() && it.length <= 8 && it.all(Char::isLetterOrDigit) }

                val stagedPath = Files.createTempFile("musync-codec-", extension?.let { ".${it}" } ?: ".bin")
                val stagedFile = stagedPath.toFile()
                stagedFile.writeBytes(audioData)
                stagedFile.deleteOnExit()
                return stagedFile
            }
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
                else -> if (FfmpegCompressedStream.isFfmpegAvailable()) FfmpegCompressedStream(file, shouldCancel) else {
                    dev.mcrib884.musync.MuSyncLog.warn("prepareStream: FFmpeg not available for ${file.name}")
                    null
                }
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
                else -> if (FfmpegCompressedStream.isFfmpegAvailable()) FfmpegCompressedStream(audioData, sourceName, shouldCancel) else null
            }
        } catch (_: CancellationException) {
            null
        } catch (e: Throwable) {
            dev.mcrib884.musync.MuSyncLog.error("Error preparing audio stream: ${e.message}")
            null
        }
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
