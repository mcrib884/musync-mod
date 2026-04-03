package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.MusicControlPacket
import dev.mcrib884.musync.network.PacketHandler
import net.minecraft.client.Minecraft
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent*/
//?}
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class PlaylistScreen : Screen(Component.literal("MuSync - Playlist")) {

    private var panelW = 300
    private var panelH = 268
    private var panelX = 0
    private var panelY = 0

    private var queueScrollOffset = 0
    private var visibleRows = 4
    private val rowH = 16
    private var savedScrollOffset = 0
    private var savedVisibleRows = 3

    private var draggingQueueScrollbar = false
    private var draggingSavedScrollbar = false
    private var playlistNameField: EditBox? = null
    private var selectedSavedName: String? = null
    private data class BtnBounds(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, var active: Boolean = true)

    private var backBounds: BtnBounds? = null
    private var saveBounds: BtnBounds? = null
    private var loadBounds: BtnBounds? = null
    private var deleteBounds: BtnBounds? = null

    private val isOp: Boolean
        get() = ClientOnlyController.isActive || dev.mcrib884.musync.isOp(Minecraft.getInstance().player)

    private val theme: MuSyncThemePalette
        get() = ClientTrackManager.getThemePreset().palette

    //? if >=1.20 {
    private fun useTextShadow(): Boolean = !ClientTrackManager.getThemePreset().id.startsWith("clear_")

    private fun drawThemeText(graphics: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
        graphics.drawString(font, text, x, y, color, useTextShadow())
    }

    private fun drawCenteredThemeText(graphics: GuiGraphics, text: String, centerX: Int, y: Int, color: Int) {
        drawThemeText(graphics, text, centerX - font.width(text) / 2, y, color)
    }
    //?} else {
    /*private fun useTextShadow(): Boolean = true

    private fun drawThemeText(unused: Any, text: String, x: Int, y: Int, color: Int) {}

    private fun drawCenteredThemeText(unused: Any, text: String, centerX: Int, y: Int, color: Int) {}*/
    //?}

    private fun effectiveStatus(): dev.mcrib884.musync.network.MusicStatusPacket? {
        return if (ClientOnlyController.isActive) ClientOnlyController.getStatus() else ClientMusicPlayer.getCurrentStatus()
    }

    private fun formatTrack(id: String): String {
        return dev.mcrib884.musync.TrackNames.formatTrack(id)
    }

    private fun queueHeaderY(): Int = panelY + 28

    private fun queueRowsY(): Int = queueHeaderY() + 16

    private fun savedHeaderY(): Int = queueRowsY() + visibleRows * rowH + 18

    private fun savedRowsY(): Int = savedHeaderY() + 14

    private fun nameLabelY(): Int = savedRowsY() + savedVisibleRows * rowH + 12

    private fun nameFieldY(): Int = nameLabelY() + 10

    private fun savedNames(): List<String> = SavedPlaylistManager.getPlaylistNames()

    private fun getSaveableTracks(): List<String> {
        val status = effectiveStatus() ?: return emptyList()
        val tracks = mutableListOf<String>()
        if (status.mode == dev.mcrib884.musync.network.MusicStatusPacket.PlayMode.PLAYLIST && status.currentTrack != null) {
            tracks.add(dev.mcrib884.musync.command.MuSyncCommand.toBrowserTrackKey(status.currentTrack))
        }
        status.queue.mapTo(tracks) { dev.mcrib884.musync.command.MuSyncCommand.toBrowserTrackKey(it) }
        return tracks
    }

    private fun currentPlaylistNameInput(): String = playlistNameField?.value.orEmpty().trim()

    private fun saveCurrentPlaylist() {
        val name = currentPlaylistNameInput()
        val tracks = getSaveableTracks()
        val savedName = SavedPlaylistManager.savePlaylist(name, tracks) ?: return
        selectedSavedName = savedName
        playlistNameField?.value = savedName
    }

    private fun loadSelectedPlaylist() {
        if (!isOp) return
        val playlistName = SavedPlaylistManager.findPlaylistName(currentPlaylistNameInput()) ?: selectedSavedName ?: return
        val tracks = SavedPlaylistManager.getPlaylist(playlistName).mapNotNull { key ->
            dev.mcrib884.musync.command.MuSyncCommand.resolveTrackValue(key)
                ?: dev.mcrib884.musync.command.MuSyncCommand.getTrackId(key)
                ?: if (key.startsWith("custom:", ignoreCase = true)) key else null
        }
        if (tracks.isEmpty()) return

        if (ClientOnlyController.isActive) {
            ClientOnlyController.clearQueue()
            ClientOnlyController.playTrack(tracks.first())
            for (track in tracks.drop(1)) {
                ClientOnlyController.addToQueue(track)
            }
        } else {
            PacketHandler.sendToServer(
                MusicControlPacket(MusicControlPacket.Action.CLEAR_QUEUE, null, null)
            )
            val status = effectiveStatus()
            val shouldStartImmediately = status?.isPlaying == true &&
                status.currentTrack != null &&
                status.mode != dev.mcrib884.musync.network.MusicStatusPacket.PlayMode.PLAYLIST
            if (shouldStartImmediately) {
                PacketHandler.sendToServer(
                    MusicControlPacket(MusicControlPacket.Action.PLAY_TRACK, tracks.first(), null)
                )
            } else {
                PacketHandler.sendToServer(
                    MusicControlPacket(MusicControlPacket.Action.ADD_TO_QUEUE, tracks.first(), null)
                )
            }
            for (track in tracks.drop(1)) {
                PacketHandler.sendToServer(
                    MusicControlPacket(MusicControlPacket.Action.ADD_TO_QUEUE, track, null)
                )
            }
        }
    }

    private fun deleteSelectedPlaylist() {
        val playlistName = SavedPlaylistManager.findPlaylistName(currentPlaylistNameInput()) ?: selectedSavedName ?: return
        if (SavedPlaylistManager.deletePlaylist(playlistName)) {
            if (selectedSavedName == playlistName) selectedSavedName = null
            if (playlistNameField?.value?.equals(playlistName, ignoreCase = true) == true) {
                playlistNameField?.value = ""
            }
        }
    }

    override fun init() {
        super.init()
        panelW = minOf(300, width - 16)
        panelH = minOf(268, height - 16)
        visibleRows = if (panelH >= 248) 4 else 3
        savedVisibleRows = if (panelH >= 248) 3 else 2
        panelX = (width - panelW) / 2
        panelY = maxOf(8, (height - panelH) / 2)

        val nameBoxY = nameFieldY()
        val nameTextY = nameBoxY + ((16 - font.lineHeight + 1).coerceAtLeast(0) / 2)
        playlistNameField = EditBox(font, panelX + 10 + 4, nameTextY, panelW - 20 - 8, 12, Component.literal("Playlist name"))
        playlistNameField!!.setMaxLength(64)
        playlistNameField!!.setBordered(false)
        playlistNameField!!.setTextColor(0xFFFFFFFF.toInt())
        playlistNameField!!.setTextColorUneditable(0xFF888888.toInt())
        playlistNameField!!.setResponder { value ->
            if (selectedSavedName != null && !selectedSavedName.equals(value, ignoreCase = true)) {
                selectedSavedName = null
            }
        }
        addRenderableWidget(playlistNameField!!)

        val btnY = panelY + panelH - 24
        backBounds = BtnBounds(panelX + 6, btnY, 50, 16, "\u2190 Back")
        saveBounds = BtnBounds(panelX + 62, btnY, 52, 16, "Save")
        loadBounds = BtnBounds(panelX + 118, btnY, 52, 16, "Load")
        deleteBounds = BtnBounds(panelX + 174, btnY, 60, 16, "Delete")
    }

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

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, theme.frameColor)
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, theme.panelColor)
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, theme.accentColor)

        val cx = panelX + panelW / 2

        drawCenteredThemeText(graphics, "\u266B Playlist Queue \u266B", cx, panelY + 8, theme.accentColor)

        val status = effectiveStatus()
        val queue = status?.queue ?: emptyList()
        val saved = savedNames()
        val queueListY = queueHeaderY()
        val queueRowsY = queueRowsY()
        val savedTitleY = savedHeaderY()
        val savedListY = savedRowsY()

        drawThemeText(graphics, "Queue", panelX + 10, queueListY, theme.statusIdleColor)
        val queueMaxScroll = (queue.size - visibleRows).coerceAtLeast(0)
        queueScrollOffset = queueScrollOffset.coerceIn(0, queueMaxScroll)

        if (queue.isEmpty()) {
            drawCenteredThemeText(graphics, "Queue is empty", cx, queueRowsY + 18, theme.statusIdleColor)
            drawCenteredThemeText(graphics, "Use the Track Browser to add tracks", cx, queueRowsY + 34, theme.statusIdleColor)
        }

        if (queue.isNotEmpty()) {
            for (i in 0 until visibleRows) {
                val idx = i + queueScrollOffset
                val slotY = queueRowsY + i * rowH
                val rowTop = slotY + 1
                val rowBottom = slotY + rowH - 1
                val rowLeft = panelX + 6
                val rowRight = panelX + panelW - 14
                val hasTrack = idx < queue.size
                val rowHovered = hasTrack && mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom
                val rowFill = when {
                    rowHovered -> theme.listHoverColor
                    hasTrack && i % 2 == 0 -> theme.listEvenColor
                    hasTrack -> theme.listOddColor
                    else -> 0x00000000
                }
                val rowBorder = if (hasTrack) theme.listBorderColor else 0x00000000

                graphics.fill(rowLeft, rowTop, rowRight, rowBottom, rowFill)
                graphics.fill(rowLeft, rowTop, rowRight, rowTop + 1, rowBorder)
                graphics.fill(rowLeft, rowBottom - 1, rowRight, rowBottom, rowBorder)
                graphics.fill(rowLeft, rowTop, rowLeft + 1, rowBottom, rowBorder)
                graphics.fill(rowRight - 1, rowTop, rowRight, rowBottom, rowBorder)

                if (!hasTrack) continue

                val trackId = queue[idx]
                val textY = rowTop + ((rowBottom - rowTop) - font.lineHeight) / 2 + 1

                drawThemeText(graphics, "${idx + 1}.", rowLeft + 4, textY, theme.statusIdleColor)

                val displayName = formatTrack(trackId)
                val nameW = panelW - 68
                val trimmed = if (font.width(displayName) > nameW) {
                    font.plainSubstrByWidth(displayName, nameW) + "..."
                } else displayName
                drawThemeText(graphics, trimmed, rowLeft + 18, textY, theme.buttonTextColor)

                if (isOp) {
                    val xBtnX = rowRight - 14
                    val hovered = mouseX >= xBtnX && mouseX < xBtnX + 12 && mouseY >= rowTop && mouseY < rowBottom
                    val color = if (hovered) 0xFFFF6666.toInt() else 0xFFAA6655.toInt()
                    drawThemeText(graphics, "\u2716", xBtnX, textY, color)
                }
            }
        }

        if (queue.size > visibleRows) {
            val barTotalH = visibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((visibleRows.toFloat() / queue.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = queueRowsY + ((queueScrollOffset.toFloat() / queueMaxScroll) * (barTotalH - thumbH)).toInt()

            graphics.fill(barX, queueRowsY, barX + barW, queueRowsY + barTotalH, 0xFF151726.toInt())
            graphics.fill(barX, queueRowsY, barX + barW, queueRowsY + 1, theme.progressBorderColor)
            graphics.fill(barX, queueRowsY + barTotalH - 1, barX + barW, queueRowsY + barTotalH, theme.progressBorderColor)

            val thumbColor = if (draggingQueueScrollbar) theme.accentStrongColor else theme.accentColor
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        drawCenteredThemeText(graphics, "Saved Playlists", cx, savedTitleY, theme.accentColor)
        val savedMaxScroll = (saved.size - savedVisibleRows).coerceAtLeast(0)
        savedScrollOffset = savedScrollOffset.coerceIn(0, savedMaxScroll)

        if (saved.isEmpty()) {
            drawCenteredThemeText(graphics, "No saved playlists on this client", cx, savedListY + 20, theme.statusIdleColor)
        }

        for (i in 0 until savedVisibleRows) {
            val idx = i + savedScrollOffset
            val slotY = savedListY + i * rowH
            val rowTop = slotY + 1
            val rowBottom = slotY + rowH - 1
            val rowLeft = panelX + 6
            val rowRight = panelX + panelW - 14
            val hasPlaylist = idx < saved.size
            val isSelected = hasPlaylist && saved[idx] == selectedSavedName
            val rowHovered = hasPlaylist && mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom
            val rowFill = when {
                isSelected -> theme.listSelectedColor
                rowHovered -> theme.listHoverColor
                hasPlaylist && i % 2 == 0 -> theme.listEvenColor
                hasPlaylist -> theme.listOddColor
                else -> 0x22151726.toInt()
            }
            val rowBorder = if (isSelected) theme.accentColor else if (hasPlaylist) theme.listBorderColor else theme.progressBorderColor

            graphics.fill(rowLeft, rowTop, rowRight, rowBottom, rowFill)
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + 1, rowBorder)
            graphics.fill(rowLeft, rowBottom - 1, rowRight, rowBottom, rowBorder)
            graphics.fill(rowLeft, rowTop, rowLeft + 1, rowBottom, rowBorder)
            graphics.fill(rowRight - 1, rowTop, rowRight, rowBottom, rowBorder)

            if (!hasPlaylist) continue

            val name = saved[idx]
            val textY = rowTop + ((rowBottom - rowTop) - font.lineHeight) / 2 + 1
            val displayName = if (font.width(name) > panelW - 46) {
                font.plainSubstrByWidth(name, panelW - 46) + "..."
            } else name
            drawThemeText(graphics, displayName, rowLeft + 4, textY, if (isSelected) theme.accentStrongColor else theme.buttonTextColor)

            val xBtnX2 = rowRight - 14
            val xHov2 = mouseX >= xBtnX2 && mouseX < xBtnX2 + 10 && mouseY >= rowTop && mouseY < rowBottom
            drawThemeText(graphics, "\u2716", xBtnX2, textY, if (xHov2) 0xFFFF6666.toInt() else 0xFFAA6655.toInt())
        }

        if (saved.size > savedVisibleRows) {
            val barTotalH = savedVisibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((savedVisibleRows.toFloat() / saved.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = savedListY + ((savedScrollOffset.toFloat() / savedMaxScroll) * (barTotalH - thumbH)).toInt()

            graphics.fill(barX, savedListY, barX + barW, savedListY + barTotalH, 0xFF151726.toInt())
            graphics.fill(barX, savedListY, barX + barW, savedListY + 1, theme.progressBorderColor)
            graphics.fill(barX, savedListY + barTotalH - 1, barX + barW, savedListY + barTotalH, theme.progressBorderColor)

            val thumbColor = if (draggingSavedScrollbar) theme.accentStrongColor else theme.accentColor
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        drawThemeText(graphics, "Name", panelX + 10, nameLabelY(), theme.statusIdleColor)

        val nfY = nameFieldY()
        val nfFocused = playlistNameField?.isFocused == true
        val nfBc = if (nfFocused) theme.accentStrongColor else theme.accentColor
        graphics.fill(panelX + 10, nfY, panelX + panelW - 10, nfY + 16, theme.buttonColor)
        graphics.fill(panelX + 10, nfY, panelX + panelW - 10, nfY + 1, nfBc)
        graphics.fill(panelX + 10, nfY, panelX + 11, nfY + 16, nfBc)
        graphics.fill(panelX + panelW - 11, nfY, panelX + panelW - 10, nfY + 16, nfBc)
        graphics.fill(panelX + 10, nfY + 15, panelX + panelW - 10, nfY + 16, nfBc)

        val canSave = getSaveableTracks().isNotEmpty()
        val canLoad = isOp && selectedSavedName != null
        val canDelete = selectedSavedName != null
        backBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) }
        saveBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canSave) }
        loadBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canLoad) }
        deleteBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canDelete) }

        if (!isOp && !ClientOnlyController.isActive) {
            drawCenteredThemeText(graphics, "\u26A0 Load requires OP; saves are local to this client", cx, panelY + panelH - 70, 0xFFFF5555.toInt())
        }

        super.render(graphics, mouseX, mouseY, partialTick)
    }
    //?} else {
    /*override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(poseStack)

        GuiComponent.fill(poseStack, panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val cx = panelX + panelW / 2

        GuiComponent.drawCenteredString(poseStack, font, "\u266B Playlist Queue \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = effectiveStatus()
        val queue = status?.queue ?: emptyList()
        val saved = savedNames()
        val queueListY = queueHeaderY()
        val queueRowsY = queueRowsY()
        val savedTitleY = savedHeaderY()
        val savedListY = savedRowsY()

        GuiComponent.drawString(poseStack, font, "Queue", panelX + 10, queueListY, 0xFF888888.toInt())
        val queueMaxScroll = (queue.size - visibleRows).coerceAtLeast(0)
        queueScrollOffset = queueScrollOffset.coerceIn(0, queueMaxScroll)

        if (queue.isEmpty()) {
            GuiComponent.drawCenteredString(poseStack, font, "Queue is empty", cx, queueRowsY + 18, 0xFF666666.toInt())
            GuiComponent.drawCenteredString(poseStack, font, "Use the Track Browser to add tracks", cx, queueRowsY + 34, 0xFF444466.toInt())
        }

        if (queue.isNotEmpty()) {
            for (i in 0 until visibleRows) {
                val idx = i + queueScrollOffset
                val slotY = queueRowsY + i * rowH
                val rowTop = slotY + 1
                val rowBottom = slotY + rowH - 1
                val rowLeft = panelX + 6
                val rowRight = panelX + panelW - 14
                val hasTrack = idx < queue.size
                val rowHovered = hasTrack && mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom
                val rowFill = when {
                    rowHovered -> 0x6634364A.toInt()
                    hasTrack && i % 2 == 0 -> 0x442A2C3E.toInt()
                    hasTrack -> 0x33202233.toInt()
                    else -> 0x00000000
                }
                val rowBorder = if (hasTrack) 0x884A4D68.toInt() else 0x00000000

                GuiComponent.fill(poseStack, rowLeft, rowTop, rowRight, rowBottom, rowFill)
                GuiComponent.fill(poseStack, rowLeft, rowTop, rowRight, rowTop + 1, rowBorder)
                GuiComponent.fill(poseStack, rowLeft, rowBottom - 1, rowRight, rowBottom, rowBorder)
                GuiComponent.fill(poseStack, rowLeft, rowTop, rowLeft + 1, rowBottom, rowBorder)
                GuiComponent.fill(poseStack, rowRight - 1, rowTop, rowRight, rowBottom, rowBorder)

                if (!hasTrack) continue

                val trackId = queue[idx]
                val textY = rowTop + ((rowBottom - rowTop) - font.lineHeight) / 2 + 1

                GuiComponent.drawString(poseStack, font, "${idx + 1}.", rowLeft + 4, textY, 0xFF999999.toInt())

                val displayName = formatTrack(trackId)
                val nameW = panelW - 68
                val trimmed = if (font.width(displayName) > nameW) {
                    font.plainSubstrByWidth(displayName, nameW) + "..."
                } else displayName
                GuiComponent.drawString(poseStack, font, trimmed, rowLeft + 18, textY, 0xFFDDDDDD.toInt())

                if (isOp) {
                    val xBtnX = rowRight - 14
                    val hovered = mouseX >= xBtnX && mouseX < xBtnX + 12 && mouseY >= rowTop && mouseY < rowBottom
                    val color = if (hovered) 0xFFFF6666.toInt() else 0xFFAA6655.toInt()
                    GuiComponent.drawString(poseStack, font, "\u2716", xBtnX, textY, color)
                }
            }
        }

        if (queue.size > visibleRows) {
            val barTotalH = visibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((visibleRows.toFloat() / queue.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = queueRowsY + ((queueScrollOffset.toFloat() / queueMaxScroll) * (barTotalH - thumbH)).toInt()

            GuiComponent.fill(poseStack, barX, queueRowsY, barX + barW, queueRowsY + barTotalH, 0xFF151726.toInt())
            GuiComponent.fill(poseStack, barX, queueRowsY, barX + barW, queueRowsY + 1, 0xFF2A2C40.toInt())
            GuiComponent.fill(poseStack, barX, queueRowsY + barTotalH - 1, barX + barW, queueRowsY + barTotalH, 0xFF2A2C40.toInt())

            val thumbColor = if (draggingQueueScrollbar) 0xFF33EE88.toInt() else 0xFF00CC66.toInt()
            GuiComponent.fill(poseStack, barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        GuiComponent.drawCenteredString(poseStack, font, "Saved Playlists", cx, savedTitleY, 0xFF00CC66.toInt())
        val savedMaxScroll = (saved.size - savedVisibleRows).coerceAtLeast(0)
        savedScrollOffset = savedScrollOffset.coerceIn(0, savedMaxScroll)

        if (saved.isEmpty()) {
            GuiComponent.drawCenteredString(poseStack, font, "No saved playlists on this client", cx, savedListY + 20, 0xFF666666.toInt())
        }

        for (i in 0 until savedVisibleRows) {
            val idx = i + savedScrollOffset
            val slotY = savedListY + i * rowH
            val rowTop = slotY + 1
            val rowBottom = slotY + rowH - 1
            val rowLeft = panelX + 6
            val rowRight = panelX + panelW - 14
            val hasPlaylist = idx < saved.size
            val isSelected = hasPlaylist && saved[idx] == selectedSavedName
            val rowHovered = hasPlaylist && mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom
            val rowFill = when {
                isSelected -> 0x661F3A31.toInt()
                rowHovered -> 0x6634364A.toInt()
                hasPlaylist && i % 2 == 0 -> 0x442A2C3E.toInt()
                hasPlaylist -> 0x33202233.toInt()
                else -> 0x22151726.toInt()
            }
            val rowBorder = if (isSelected) 0xAA00CC66.toInt() else if (hasPlaylist) 0x884A4D68.toInt() else 0x442A2C40.toInt()

            GuiComponent.fill(poseStack, rowLeft, rowTop, rowRight, rowBottom, rowFill)
            GuiComponent.fill(poseStack, rowLeft, rowTop, rowRight, rowTop + 1, rowBorder)
            GuiComponent.fill(poseStack, rowLeft, rowBottom - 1, rowRight, rowBottom, rowBorder)
            GuiComponent.fill(poseStack, rowLeft, rowTop, rowLeft + 1, rowBottom, rowBorder)
            GuiComponent.fill(poseStack, rowRight - 1, rowTop, rowRight, rowBottom, rowBorder)

            if (!hasPlaylist) continue

            val name = saved[idx]
            val textY = rowTop + ((rowBottom - rowTop) - font.lineHeight) / 2 + 1
            val displayName = if (font.width(name) > panelW - 46) {
                font.plainSubstrByWidth(name, panelW - 46) + "..."
            } else name
            GuiComponent.drawString(poseStack, font, displayName, rowLeft + 4, textY, if (isSelected) 0xFF00FF88.toInt() else 0xFFDDDDDD.toInt())

            val xBtnX2 = rowRight - 14
            val xHov2 = mouseX >= xBtnX2 && mouseX < xBtnX2 + 10 && mouseY >= rowTop && mouseY < rowBottom
            GuiComponent.drawString(poseStack, font, "\u2716", xBtnX2, textY, if (xHov2) 0xFFFF6666.toInt() else 0xFFAA6655.toInt())
        }

        if (saved.size > savedVisibleRows) {
            val barTotalH = savedVisibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((savedVisibleRows.toFloat() / saved.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = savedListY + ((savedScrollOffset.toFloat() / savedMaxScroll) * (barTotalH - thumbH)).toInt()

            GuiComponent.fill(poseStack, barX, savedListY, barX + barW, savedListY + barTotalH, 0xFF151726.toInt())
            GuiComponent.fill(poseStack, barX, savedListY, barX + barW, savedListY + 1, 0xFF2A2C40.toInt())
            GuiComponent.fill(poseStack, barX, savedListY + barTotalH - 1, barX + barW, savedListY + barTotalH, 0xFF2A2C40.toInt())

            val thumbColor = if (draggingSavedScrollbar) 0xFF33EE88.toInt() else 0xFF00CC66.toInt()
            GuiComponent.fill(poseStack, barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        GuiComponent.drawString(poseStack, font, "Name", panelX + 10, nameLabelY(), 0xFF888888.toInt())

        val nfY1919 = nameFieldY()
        val nfFocused1919 = playlistNameField?.isFocused == true
        val nfBc1919 = if (nfFocused1919) 0xFF33EE88.toInt() else 0xFF00CC66.toInt()
        GuiComponent.fill(poseStack, panelX + 10, nfY1919, panelX + panelW - 10, nfY1919 + 16, 0xFF1C1C2A.toInt())
        GuiComponent.fill(poseStack, panelX + 10, nfY1919, panelX + panelW - 10, nfY1919 + 1, nfBc1919)
        GuiComponent.fill(poseStack, panelX + 10, nfY1919, panelX + 11, nfY1919 + 16, nfBc1919)
        GuiComponent.fill(poseStack, panelX + panelW - 11, nfY1919, panelX + panelW - 10, nfY1919 + 16, nfBc1919)
        GuiComponent.fill(poseStack, panelX + 10, nfY1919 + 15, panelX + panelW - 10, nfY1919 + 16, nfBc1919)

        val canSave1919 = getSaveableTracks().isNotEmpty()
        val canLoad1919 = isOp && selectedSavedName != null
        val canDelete1919 = selectedSavedName != null
        backBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) }
        saveBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canSave1919) }
        loadBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canLoad1919) }
        deleteBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canDelete1919) }

        if (!isOp && !ClientOnlyController.isActive) {
            GuiComponent.drawCenteredString(poseStack, font, "\u26A0 Load requires OP; saves are local to this client", cx, panelY + panelH - 70, 0xFFFF5555.toInt())
        }

        super.render(poseStack, mouseX, mouseY, partialTick)
    }*/
    //?}

    private fun handleMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean? {
        if (button == 0) {
            val status = effectiveStatus()
            val queue = status?.queue ?: emptyList()
            val queueListY = queueRowsY()
            val queueMaxScroll = (queue.size - visibleRows).coerceAtLeast(0)
            val saved = savedNames()
            val savedListY = savedRowsY()
            val savedMaxScroll = (saved.size - savedVisibleRows).coerceAtLeast(0)

            if (queue.size > visibleRows) {
                val barX = panelX + panelW - 10
                val barW = 6
                val barTotalH = visibleRows * rowH
                if (mouseX >= barX && mouseX <= barX + barW &&
                    mouseY >= queueListY && mouseY <= queueListY + barTotalH) {
                    draggingQueueScrollbar = true
                    val ratio = ((mouseY - queueListY) / barTotalH).toFloat().coerceIn(0f, 1f)
                    queueScrollOffset = (ratio * queueMaxScroll).toInt().coerceIn(0, queueMaxScroll)
                    return true
                }
            }

            if (saved.size > savedVisibleRows) {
                val barX = panelX + panelW - 10
                val barW = 6
                val barTotalH = savedVisibleRows * rowH
                if (mouseX >= barX && mouseX <= barX + barW &&
                    mouseY >= savedListY && mouseY <= savedListY + barTotalH) {
                    draggingSavedScrollbar = true
                    val ratio = ((mouseY - savedListY) / barTotalH).toFloat().coerceIn(0f, 1f)
                    savedScrollOffset = (ratio * savedMaxScroll).toInt().coerceIn(0, savedMaxScroll)
                    return true
                }
            }

            if (isOp) {
                val rowRight = panelX + panelW - 14
                val xBtnX = rowRight - 14
                for (i in 0 until visibleRows) {
                    val idx = i + queueScrollOffset
                    if (idx >= queue.size) break
                    val slotY = queueListY + i * rowH
                    val rowTop = slotY + 1
                    val rowBottom = slotY + rowH - 1
                    if (mouseX >= xBtnX && mouseX < xBtnX + 12 && mouseY >= rowTop && mouseY < rowBottom) {
                        if (ClientOnlyController.isActive) {
                            ClientOnlyController.removeFromQueue(idx)
                        } else {
                            val packet = MusicControlPacket(
                                action = MusicControlPacket.Action.REMOVE_FROM_QUEUE,
                                trackId = null,
                                queuePosition = idx
                            )
                            PacketHandler.sendToServer(packet)
                        }
                        return true
                    }
                }
            }

            val savedRowRight = panelX + panelW - 14
            val savedXBtnX = savedRowRight - 14
            for (i in 0 until savedVisibleRows) {
                val idx = i + savedScrollOffset
                if (idx >= saved.size) break
                val slotY = savedListY + i * rowH
                val rowTop = slotY + 1
                val rowBottom = slotY + rowH - 1
                if (mouseX >= savedXBtnX && mouseX < savedXBtnX + 10 && mouseY >= rowTop && mouseY < rowBottom) {
                    val name = saved[idx]
                    if (SavedPlaylistManager.deletePlaylist(name)) {
                        if (selectedSavedName == name) {
                            selectedSavedName = null
                            playlistNameField?.value = ""
                        }
                    }
                    return true
                }
            }

            if (mouseX >= panelX + 6 && mouseX < panelX + panelW - 14 &&
                mouseY >= savedListY && mouseY < savedListY + savedVisibleRows * rowH) {
                val rowIdx = ((mouseY - savedListY) / rowH).toInt()
                val idx = rowIdx + savedScrollOffset
                if (idx in saved.indices) {
                    selectedSavedName = saved[idx]
                    playlistNameField?.value = selectedSavedName ?: ""
                    return true
                }
            }

            val mx = mouseX.toInt(); val my = mouseY.toInt()
            if (backBounds?.let { mx in it.x until it.x + it.w && my in it.y until it.y + it.h } == true) {
                Minecraft.getInstance().setScreen(MusicControlScreen()); return true
            }
            if (saveBounds?.let { mx in it.x until it.x + it.w && my in it.y until it.y + it.h } == true && getSaveableTracks().isNotEmpty()) {
                saveCurrentPlaylist(); return true
            }
            if (loadBounds?.let { mx in it.x until it.x + it.w && my in it.y until it.y + it.h } == true && isOp && selectedSavedName != null) {
                loadSelectedPlaylist(); return true
            }
            if (deleteBounds?.let { mx in it.x until it.x + it.w && my in it.y until it.y + it.h } == true && selectedSavedName != null) {
                deleteSelectedPlaylist(); return true
            }
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

    private fun handleMouseDragged(mouseX: Double, mouseY: Double, button: Int): Boolean? {
        if (draggingQueueScrollbar && button == 0) {
            val queueSize = effectiveStatus()?.queue?.size ?: 0
            val listY = queueRowsY()
            val barTotalH = visibleRows * rowH
            val maxScroll = (queueSize - visibleRows).coerceAtLeast(0)
            val ratio = ((mouseY - listY) / barTotalH).toFloat().coerceIn(0f, 1f)
            queueScrollOffset = (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
            return true
        }
        if (draggingSavedScrollbar && button == 0) {
            val savedSize = savedNames().size
            val listY = savedRowsY()
            val barTotalH = savedVisibleRows * rowH
            val maxScroll = (savedSize - savedVisibleRows).coerceAtLeast(0)
            val ratio = ((mouseY - listY) / barTotalH).toFloat().coerceIn(0f, 1f)
            savedScrollOffset = (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
            return true
        }
        return null
    }

    //? if >=1.21.11 {
    /*override fun mouseDragged(event: net.minecraft.client.input.MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        return handleMouseDragged(event.x(), event.y(), event.button()) ?: super.mouseDragged(event, deltaX, deltaY)
    }*/
    //?} else {
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return handleMouseDragged(mouseX, mouseY, button) ?: super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }
    //?}

    //? if >=1.21.11 {
    /*override fun mouseReleased(event: net.minecraft.client.input.MouseButtonEvent): Boolean {
        if (event.button() == 0 && (draggingQueueScrollbar || draggingSavedScrollbar)) {
            draggingQueueScrollbar = false
            draggingSavedScrollbar = false
            return true
        }
        return super.mouseReleased(event)
    }*/
    //?} else {
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && (draggingQueueScrollbar || draggingSavedScrollbar)) {
            draggingQueueScrollbar = false
            draggingSavedScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }
    //?}

    //? if >=1.21 {
    /*override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, delta: Double): Boolean {*/
    //?} else {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
    //?}
        val queueAreaTop = queueRowsY()
        val queueAreaBottom = queueAreaTop + visibleRows * rowH
        val savedAreaTop = savedRowsY()
        val savedAreaBottom = savedAreaTop + savedVisibleRows * rowH

        if (mouseY >= savedAreaTop && mouseY <= savedAreaBottom) {
            val maxScroll = (savedNames().size - savedVisibleRows).coerceAtLeast(0)
            savedScrollOffset = (savedScrollOffset - delta.toInt()).coerceIn(0, maxScroll)
        } else {
            val queueSize = effectiveStatus()?.queue?.size ?: 0
            val maxScroll = (queueSize - visibleRows).coerceAtLeast(0)
            queueScrollOffset = (queueScrollOffset - delta.toInt()).coerceIn(0, maxScroll)
        }
        return true
    }

    override fun isPauseScreen(): Boolean = false

    //? if >=1.21.11 {
    /*override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (dev.mcrib884.musync.KeyBindings.MUSIC_GUI_KEY.matches(event)) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }*/
    //?} else {
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dev.mcrib884.musync.KeyBindings.MUSIC_GUI_KEY.matches(keyCode, scanCode)) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
    //?}

    //? if >=1.20 {
    private fun drawCustomBtn(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, label: String, hovered: Boolean, active: Boolean = true) {
        val bg = when { !active -> theme.buttonDisabledColor; hovered -> theme.buttonHoverColor; else -> theme.buttonColor }
        val borderColor = if (active) theme.accentColor else theme.buttonDisabledBorderColor
        val textColor = if (active) theme.buttonTextColor else theme.buttonDisabledTextColor
        graphics.fill(x, y, x + w, y + h, bg)
        graphics.fill(x, y, x + w, y + 1, borderColor)
        graphics.fill(x, y, x + 1, y + h, borderColor)
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor)
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor)
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2, textColor, useTextShadow())
    }
    //?} else {
    /*private fun drawCustomBtn1919(poseStack: PoseStack, x: Int, y: Int, w: Int, h: Int, label: String, hovered: Boolean, active: Boolean = true) {
        val bg = when { !active -> 0xFF111118.toInt(); hovered -> 0xFF334433.toInt(); else -> 0xFF1C1C2A.toInt() }
        val borderColor = if (active) 0xFF00CC66.toInt() else 0xFF336644.toInt()
        val textColor = if (active) 0xFFFFFFFF.toInt() else 0xFF667766.toInt()
        GuiComponent.fill(poseStack, x, y, x + w, y + h, bg)
        GuiComponent.fill(poseStack, x, y, x + w, y + 1, borderColor)
        GuiComponent.fill(poseStack, x, y, x + 1, y + h, borderColor)
        GuiComponent.fill(poseStack, x + w - 1, y, x + w, y + h, borderColor)
        GuiComponent.fill(poseStack, x, y + h - 1, x + w, y + h, borderColor)
        GuiComponent.drawString(poseStack, font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2, textColor)
    }*/
    //?}

}
