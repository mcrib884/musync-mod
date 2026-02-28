package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class TrackDownloadScreen : Screen(Component.literal("MuSync - Syncing Tracks")) {

    private val panelW = 300
    private val panelH = 220
    private var panelX = 0
    private var panelY = 0

    private var closeCountdown = -1

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
    }

    fun onDownloadComplete() {
        closeCountdown = 40
    }

    override fun tick() {
        super.tick()

        if (ClientTrackManager.downloadComplete && closeCountdown < 0) {
            onDownloadComplete()
        }
        if (closeCountdown > 0) {
            closeCountdown--
        } else if (closeCountdown == 0) {

            Minecraft.getInstance().setScreen(MusicControlScreen())
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)

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
            graphics.drawString(font, "$displayName (${mgr.formatSize(trackSize.toLong())})", panelX + 60, currentY, nameColor)

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

        for (i in 0 until minOf(maxVisible, totalCount)) {
            val idx = i
            val y = listStartY + i * 12
            val (name, size) = tracks[idx]
            val displayName = name.replace("_", " ").replaceFirstChar { it.uppercase() }

            val (statusIcon, statusColor) = when {
                idx < currentIdx -> "\u2714" to 0xFF00FF88.toInt()
                idx == currentIdx -> "\u21E9" to 0xFF00AAFF.toInt()
                else -> "\u25CB" to 0xFF555555.toInt()
            }

            graphics.drawString(font, statusIcon, panelX + 14, y, statusColor)
            graphics.drawString(font, displayName, panelX + 26, y, statusColor)

            val sizeStr = mgr.formatSize(size.toLong())
            val sizeW = font.width(sizeStr)
            graphics.drawString(font, sizeStr, panelX + panelW - 14 - sizeW, y, 0xFF666666.toInt())
        }

        if (totalCount > maxVisible) {
            val moreY = listStartY + maxVisible * 12
            graphics.drawString(font, "... and ${totalCount - maxVisible} more", panelX + 26, moreY, 0xFF555555.toInt())
        }

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun shouldCloseOnEsc(): Boolean = true

    override fun isPauseScreen(): Boolean = false
}
