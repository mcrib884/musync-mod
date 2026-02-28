package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

object OggDurationReader {

    fun getDurationMs(soundLocation: ResourceLocation): Long {
        var buffer: ByteBuffer? = null
        try {
            val fileLoc = ResourceLocation(soundLocation.namespace, "sounds/" + soundLocation.path + ".ogg")
            val resourceManager = Minecraft.getInstance().resourceManager
            val resource = resourceManager.getResource(fileLoc).orElse(null) ?: return -1

            val bytes = resource.open().use { it.readBytes() }

            buffer = MemoryUtil.memAlloc(bytes.size)
            buffer.put(bytes)
            buffer.flip()

            MemoryStack.stackPush().use { stack ->
                val error = stack.mallocInt(1)
                val handle = STBVorbis.stb_vorbis_open_memory(buffer, error, null)
                if (handle == 0L) {
                    println("[MuSync] STBVorbis error ${error[0]} for $fileLoc")
                    return -1
                }

                val totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle)
                val info = STBVorbisInfo.malloc(stack)
                STBVorbis.stb_vorbis_get_info(handle, info)
                val sampleRate = info.sample_rate()

                STBVorbis.stb_vorbis_close(handle)

                if (sampleRate <= 0) return -1
                return (totalSamples.toLong() * 1000L) / sampleRate.toLong()
            }
        } catch (e: Exception) {
            println("[MuSync] Error reading OGG duration: ${e.message}")
            return -1
        } finally {
            buffer?.let { MemoryUtil.memFree(it) }
        }
    }
}
