package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent*/
//?}
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class TrackDownloadScreen : Screen(Component.literal("MuSync - Syncing Tracks")) {

    private val panelW = 300
    private val panelH = 220
    private var panelX = 0
    private var panelY = 0

    private var closeCountdown = -1
    private var showingFailedOverlay = false

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
    }

    fun onDownloadComplete() {
        val failed = ClientTrackManager.getFailedTracks()
        if (failed.isNotEmpty()) {
            showingFailedOverlay = true
        } else {
            closeCountdown = 40
        }
    }

    override fun tick() {
        super.tick()

        if (!isDownloadDone() && !ClientTrackManager.isDownloading && ClientTrackManager.tracksToDownload.isNotEmpty()) {
            if (!showingFailedOverlay && closeCountdown < 0) onDownloadComplete()
        }
        if (ClientTrackManager.downloadComplete && closeCountdown < 0 && !showingFailedOverlay) {
            onDownloadComplete()
        }
        if (closeCountdown > 0) {
            closeCountdown--
        } else if (closeCountdown == 0) {
            Minecraft.getInstance().setScreen(MusicControlScreen())
        }
    }

    private fun isDownloadDone(): Boolean =
        closeCountdown >= 0 || showingFailedOverlay || ClientTrackManager.downloadComplete

    //? if >=1.21 {
    /*override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}*/
    //?}

    //? if >=1.20 {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        //? if >=1.21 {
        /*super.renderBackground(graphics, mouseX, mouseY, partialTick)*/
        //?} else {
        renderBackground(graphics)
        //?}

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00AAFF.toInt())

        val cx = panelX + panelW / 2
        val mgr = ClientTrackManager

        val titleColor = if (mgr.downloadComplete) 0xFF00FF88.toInt() else 0xFF00AAFF.toInt()
        val titleText = if (mgr.downloadComplete) "\u2714 Sync Complete!" else "\u21E9 Syncing Custom Tracks..."
        graphics.drawCenteredString(font, titleText, cx, panelY + 10, titleColor)

        if (mgr.downloadComplete) {
            graphics.drawCenteredString(font, "All tracks are synced", cx, panelY + 28, 0xFF888888.toInt())
            if (closeCountdown >= 0) {
                graphics.drawCenteredString(font, "Opening controls in ${(closeCountdown / 20) + 1}s...", cx, panelY + 44, 0xFF666666.toInt())
            }
            super.render(graphics, mouseX, mouseY, partialTick)
            return
        }

        val tracks = mgr.tracksToDownload
        val totalCount = tracks.size
        val currentIdx = mgr.currentDownloadIndex

        graphics.drawCenteredString(
            font,
            "Downloading $totalCount track${if (totalCount > 1) "s" else ""} " +
                    "(${mgr.formatSize(mgr.totalBytesToDownload)})",
            cx, panelY + 26, 0xFFCCCCCC.toInt()
        )

        val overallBarY = panelY + 42
        val barW = panelW - 40
        val barH = 10
        val barX = panelX + 20

        val overallProgress = if (mgr.totalBytesToDownload > 0) {
            (mgr.totalBytesReceived.toFloat() / mgr.totalBytesToDownload.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val overallFilled = (barW * overallProgress).toInt()

        graphics.fill(barX, overallBarY, barX + barW, overallBarY + barH, 0xFF222244.toInt())

        if (overallFilled > 0) {
            graphics.fill(barX, overallBarY, barX + overallFilled, overallBarY + barH, 0xFF00AAFF.toInt())
            graphics.fill(barX, overallBarY, barX + overallFilled, overallBarY + barH / 2, 0xFF44CCFF.toInt())
        }

        graphics.fill(barX - 1, overallBarY - 1, barX + barW + 1, overallBarY, 0xFF333355.toInt())
        graphics.fill(barX - 1, overallBarY + barH, barX + barW + 1, overallBarY + barH + 1, 0xFF333355.toInt())

        val pctText = "${(overallProgress * 100).toInt()}%"
        graphics.drawCenteredString(font, pctText, cx, overallBarY + 1, 0xFFFFFFFF.toInt())

        val bytesText = "${mgr.formatSize(mgr.totalBytesReceived)} / ${mgr.formatSize(mgr.totalBytesToDownload)}"
        graphics.drawCenteredString(font, bytesText, cx, overallBarY + barH + 4, 0xFF888888.toInt())

        val currentY = overallBarY + barH + 20
        if (currentIdx < totalCount) {
            val trackName = mgr.currentTrackName()
            val trackSize = mgr.currentTrackSize()
            val displayName = trackName.replace("_", " ").replaceFirstChar { it.uppercase() }

            graphics.drawString(font, "Current:", panelX + 14, currentY, 0xFF888888.toInt())
            val nameColor = 0xFF00CCFF.toInt()
            graphics.drawString(font, "$displayName (${mgr.formatSize(trackSize)})", panelX + 60, currentY, nameColor)

            val chunkBarY = currentY + 14
            val chunkProgress = if (mgr.currentTrackTotalChunks > 0) {
                (mgr.currentTrackChunksReceived.toFloat() / mgr.currentTrackTotalChunks.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val chunkFilled = (barW * chunkProgress).toInt()

            graphics.fill(barX, chunkBarY, barX + barW, chunkBarY + 6, 0xFF222244.toInt())
            if (chunkFilled > 0) {
                graphics.fill(barX, chunkBarY, barX + chunkFilled, chunkBarY + 6, 0xFF0088CC.toInt())
            }

            val chunkText = if (mgr.currentTrackTotalChunks > 0) {
                "Chunk ${mgr.currentTrackChunksReceived}/${mgr.currentTrackTotalChunks}"
            } else "Waiting..."
            graphics.drawString(font, chunkText, panelX + 14, chunkBarY + 8, 0xFF666666.toInt())
        }

        val listY = currentY + 36
        graphics.drawString(font, "Tracks:", panelX + 14, listY, 0xFF888888.toInt())

        val maxVisible = 5
        val listStartY = listY + 14

        val failedSet = ClientTrackManager.getFailedTracks().toSet()
        for (i in 0 until minOf(maxVisible, totalCount)) {
            val idx = i
            val y = listStartY + i * 12
            val (name, size) = tracks[idx]
            val displayName = ClientTrackManager.displayTrackName(name).replace("_", " ").replaceFirstChar { it.uppercase() }
            val isFailed = name in failedSet

            val (statusIcon, statusColor) = when {
                isFailed -> "\u2718" to 0xFFFF5555.toInt()
                idx < currentIdx -> "\u2714" to 0xFF00FF88.toInt()
                idx == currentIdx -> "\u21E9" to 0xFF00AAFF.toInt()
                else -> "\u25CB" to 0xFF555555.toInt()
            }

            graphics.drawString(font, statusIcon, panelX + 14, y, statusColor)
            graphics.drawString(font, displayName, panelX + 26, y, statusColor)

            val sizeStr = mgr.formatSize(size)
            val sizeW = font.width(sizeStr)
            graphics.drawString(font, sizeStr, panelX + panelW - 14 - sizeW, y, 0xFF666666.toInt())
        }

        if (totalCount > maxVisible) {
            val moreY = listStartY + maxVisible * 12
            graphics.drawString(font, "... and ${totalCount - maxVisible} more", panelX + 26, moreY, 0xFF555555.toInt())
        }

        super.render(graphics, mouseX, mouseY, partialTick)

        if (showingFailedOverlay) {
            renderFailedOverlay(graphics, mouseX, mouseY)
        }
    }
    //?} else {
    /*override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(poseStack)

        GuiComponent.fill(poseStack, panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + 2, 0xFF00AAFF.toInt())

        val cx = panelX + panelW / 2
        val mgr = ClientTrackManager

        val titleColor = if (mgr.downloadComplete) 0xFF00FF88.toInt() else 0xFF00AAFF.toInt()
        val titleText = if (mgr.downloadComplete) "\u2714 Sync Complete!" else "\u21E9 Syncing Custom Tracks..."
        GuiComponent.drawCenteredString(poseStack, font, titleText, cx, panelY + 10, titleColor)

        if (mgr.downloadComplete) {
            GuiComponent.drawCenteredString(poseStack, font, "All tracks are synced", cx, panelY + 28, 0xFF888888.toInt())
            if (closeCountdown >= 0) {
                GuiComponent.drawCenteredString(poseStack, font, "Opening controls in ${(closeCountdown / 20) + 1}s...", cx, panelY + 44, 0xFF666666.toInt())
            }
            super.render(poseStack, mouseX, mouseY, partialTick)
            return
        }

        val tracks = mgr.tracksToDownload
        val totalCount = tracks.size
        val currentIdx = mgr.currentDownloadIndex

        GuiComponent.drawCenteredString(
            poseStack, font,
            "Downloading $totalCount track${if (totalCount > 1) "s" else ""} " +
                    "(${mgr.formatSize(mgr.totalBytesToDownload)})",
            cx, panelY + 26, 0xFFCCCCCC.toInt()
        )

        val overallBarY = panelY + 42
        val barW = panelW - 40
        val barH = 10
        val barX = panelX + 20

        val overallProgress = if (mgr.totalBytesToDownload > 0) {
            (mgr.totalBytesReceived.toFloat() / mgr.totalBytesToDownload.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val overallFilled = (barW * overallProgress).toInt()

        GuiComponent.fill(poseStack, barX, overallBarY, barX + barW, overallBarY + barH, 0xFF222244.toInt())

        if (overallFilled > 0) {
            GuiComponent.fill(poseStack, barX, overallBarY, barX + overallFilled, overallBarY + barH, 0xFF00AAFF.toInt())
            GuiComponent.fill(poseStack, barX, overallBarY, barX + overallFilled, overallBarY + barH / 2, 0xFF44CCFF.toInt())
        }

        GuiComponent.fill(poseStack, barX - 1, overallBarY - 1, barX + barW + 1, overallBarY, 0xFF333355.toInt())
        GuiComponent.fill(poseStack, barX - 1, overallBarY + barH, barX + barW + 1, overallBarY + barH + 1, 0xFF333355.toInt())

        val pctText = "${(overallProgress * 100).toInt()}%"
        GuiComponent.drawCenteredString(poseStack, font, pctText, cx, overallBarY + 1, 0xFFFFFFFF.toInt())

        val bytesText = "${mgr.formatSize(mgr.totalBytesReceived)} / ${mgr.formatSize(mgr.totalBytesToDownload)}"
        GuiComponent.drawCenteredString(poseStack, font, bytesText, cx, overallBarY + barH + 4, 0xFF888888.toInt())

        val currentY = overallBarY + barH + 20
        if (currentIdx < totalCount) {
            val trackName = mgr.currentTrackName()
            val trackSize = mgr.currentTrackSize()
            val displayName = trackName.replace("_", " ").replaceFirstChar { it.uppercase() }

            GuiComponent.drawString(poseStack, font, "Current:", panelX + 14, currentY, 0xFF888888.toInt())
            val nameColor = 0xFF00CCFF.toInt()
            GuiComponent.drawString(poseStack, font, "$displayName (${mgr.formatSize(trackSize)})", panelX + 60, currentY, nameColor)

            val chunkBarY = currentY + 14
            val chunkProgress = if (mgr.currentTrackTotalChunks > 0) {
                (mgr.currentTrackChunksReceived.toFloat() / mgr.currentTrackTotalChunks.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val chunkFilled = (barW * chunkProgress).toInt()

            GuiComponent.fill(poseStack, barX, chunkBarY, barX + barW, chunkBarY + 6, 0xFF222244.toInt())
            if (chunkFilled > 0) {
                GuiComponent.fill(poseStack, barX, chunkBarY, barX + chunkFilled, chunkBarY + 6, 0xFF0088CC.toInt())
            }

            val chunkText = if (mgr.currentTrackTotalChunks > 0) {
                "Chunk ${mgr.currentTrackChunksReceived}/${mgr.currentTrackTotalChunks}"
            } else "Waiting..."
            GuiComponent.drawString(poseStack, font, chunkText, panelX + 14, chunkBarY + 8, 0xFF666666.toInt())
        }

        val listY = currentY + 36
        GuiComponent.drawString(poseStack, font, "Tracks:", panelX + 14, listY, 0xFF888888.toInt())

        val maxVisible = 5
        val listStartY = listY + 14

        val failedSet1919 = ClientTrackManager.getFailedTracks().toSet()
        for (i in 0 until minOf(maxVisible, totalCount)) {
            val idx = i
            val y = listStartY + i * 12
            val (name, size) = tracks[idx]
            val displayName = ClientTrackManager.displayTrackName(name).replace("_", " ").replaceFirstChar { it.uppercase() }
            val isFailed = name in failedSet1919

            val (statusIcon, statusColor) = when {
                isFailed -> "\u2718" to 0xFFFF5555.toInt()
                idx < currentIdx -> "\u2714" to 0xFF00FF88.toInt()
                idx == currentIdx -> "\u21E9" to 0xFF00AAFF.toInt()
                else -> "\u25CB" to 0xFF555555.toInt()
            }

            GuiComponent.drawString(poseStack, font, statusIcon, panelX + 14, y, statusColor)
            GuiComponent.drawString(poseStack, font, displayName, panelX + 26, y, statusColor)

            val sizeStr = mgr.formatSize(size)
            val sizeW = font.width(sizeStr)
            GuiComponent.drawString(poseStack, font, sizeStr, panelX + panelW - 14 - sizeW, y, 0xFF666666.toInt())
        }

        if (totalCount > maxVisible) {
            val moreY = listStartY + maxVisible * 12
            GuiComponent.drawString(poseStack, font, "... and ${totalCount - maxVisible} more", panelX + 26, moreY, 0xFF555555.toInt())
        }

        super.render(poseStack, mouseX, mouseY, partialTick)

        if (showingFailedOverlay) {
            renderFailedOverlay1919(poseStack, mouseX, mouseY)
        }
    }*/
    //?}

    private fun handleMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean? {
        if (showingFailedOverlay && button == 0) {
            val failed = ClientTrackManager.getFailedTracks()
            val overlayW = 260
            val overlayH = 60 + failed.size.coerceAtMost(8) * 12
            val ox = (width - overlayW) / 2
            val oy = (height - overlayH) / 2
            val closeX = ox + overlayW - 14
            val closeY = oy + 4
            if (mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10) {
                showingFailedOverlay = false
                Minecraft.getInstance().setScreen(MusicControlScreen())
                return true
            }
            if (mouseX < ox || mouseX > ox + overlayW || mouseY < oy || mouseY > oy + overlayH) {
                showingFailedOverlay = false
                Minecraft.getInstance().setScreen(MusicControlScreen())
                return true
            }
            return true
        }
        return null
    }

    //? if >=1.21.11 {
    /*override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, bl: Boolean): Boolean {
        return handleMouseClicked(event.x(), event.y(), event.button()) ?: super.mouseClicked(event, bl)
    }*/
    //?} else {
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return handleMouseClicked(mouseX, mouseY, button) ?: super.mouseClicked(mouseX, mouseY, button)
    }
    //?}

    //? if >=1.20 {
    private fun renderFailedOverlay(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val failed = ClientTrackManager.getFailedTracks()
        if (failed.isEmpty()) return
        val overlayW = 260
        val overlayH = 60 + failed.size.coerceAtMost(8) * 12
        val ox = (width - overlayW) / 2
        val oy = (height - overlayH) / 2

        graphics.fill(0, 0, width, height, 0x88000000.toInt())
        graphics.fill(ox - 2, oy - 2, ox + overlayW + 2, oy + overlayH + 2, 0xFF1A1A2E.toInt())
        graphics.fill(ox, oy, ox + overlayW, oy + overlayH, 0xF0101020.toInt())
        graphics.fill(ox, oy, ox + overlayW, oy + 2, 0xFFFF5555.toInt())

        val closeX = ox + overlayW - 14
        val closeY = oy + 4
        val closeHov = mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10
        graphics.drawString(font, "\u2716", closeX, closeY, if (closeHov) 0xFFFF8888.toInt() else 0xFFAA5555.toInt())

        val cx = ox + overlayW / 2
        graphics.drawCenteredString(font, "\u26A0 Failed Tracks", cx, oy + 6, 0xFFFF5555.toInt())
        graphics.drawCenteredString(font, "These tracks could not sync and won't play:", cx, oy + 20, 0xFF999999.toInt())

        val listStartY = oy + 36
        for ((i, name) in failed.take(8).withIndex()) {
            val displayName = ClientTrackManager.displayTrackName(name).replace("_", " ").replaceFirstChar { it.uppercase() }
            graphics.drawString(font, "\u2718 $displayName", ox + 12, listStartY + i * 12, 0xFFFF6666.toInt())
        }
        if (failed.size > 8) {
            graphics.drawString(font, "... and ${failed.size - 8} more", ox + 12, listStartY + 8 * 12, 0xFF886666.toInt())
        }
    }
    //?} else {
    /*private fun renderFailedOverlay1919(poseStack: PoseStack, mouseX: Int, mouseY: Int) {
        val failed = ClientTrackManager.getFailedTracks()
        if (failed.isEmpty()) return
        val overlayW = 260
        val overlayH = 60 + failed.size.coerceAtMost(8) * 12
        val ox = (width - overlayW) / 2
        val oy = (height - overlayH) / 2

        GuiComponent.fill(poseStack, 0, 0, width, height, 0x88000000.toInt())
        GuiComponent.fill(poseStack, ox - 2, oy - 2, ox + overlayW + 2, oy + overlayH + 2, 0xFF1A1A2E.toInt())
        GuiComponent.fill(poseStack, ox, oy, ox + overlayW, oy + overlayH, 0xF0101020.toInt())
        GuiComponent.fill(poseStack, ox, oy, ox + overlayW, oy + 2, 0xFFFF5555.toInt())

        val closeX = ox + overlayW - 14
        val closeY = oy + 4
        val closeHov = mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10
        GuiComponent.drawString(poseStack, font, "\u2716", closeX, closeY, if (closeHov) 0xFFFF8888.toInt() else 0xFFAA5555.toInt())

        val cx = ox + overlayW / 2
        GuiComponent.drawCenteredString(poseStack, font, "\u26A0 Failed Tracks", cx, oy + 6, 0xFFFF5555.toInt())
        GuiComponent.drawCenteredString(poseStack, font, "These tracks could not sync and won't play:", cx, oy + 20, 0xFF999999.toInt())

        val listStartY = oy + 36
        for ((i, name) in failed.take(8).withIndex()) {
            val displayName = ClientTrackManager.displayTrackName(name).replace("_", " ").replaceFirstChar { it.uppercase() }
            GuiComponent.drawString(poseStack, font, "\u2718 $displayName", ox + 12, listStartY + i * 12, 0xFFFF6666.toInt())
        }
        if (failed.size > 8) {
            GuiComponent.drawString(poseStack, font, "... and ${failed.size - 8} more", ox + 12, listStartY + 8 * 12, 0xFF886666.toInt())
        }
    }*/
    //?}

    override fun shouldCloseOnEsc(): Boolean = true

    override fun isPauseScreen(): Boolean = false
}
