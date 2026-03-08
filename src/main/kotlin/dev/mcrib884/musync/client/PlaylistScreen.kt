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
import net.minecraftforge.network.PacketDistributor

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
        get() = Minecraft.getInstance().player?.hasPermissions(2) == true

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
        val status = ClientMusicPlayer.getCurrentStatus() ?: return emptyList()
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
        val tracks = SavedPlaylistManager.getPlaylist(playlistName)
        if (tracks.isEmpty()) return

        PacketHandler.INSTANCE.send(
            PacketDistributor.SERVER.noArg(),
            MusicControlPacket(MusicControlPacket.Action.CLEAR_QUEUE, null, null)
        )
        for (track in tracks) {
            PacketHandler.INSTANCE.send(
                PacketDistributor.SERVER.noArg(),
                MusicControlPacket(MusicControlPacket.Action.ADD_TO_QUEUE, track, null)
            )
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

        playlistNameField = EditBox(font, panelX + 10, nameFieldY(), panelW - 20, 16, Component.literal("Playlist name"))
        playlistNameField!!.setMaxLength(64)
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

    //? if >=1.20 {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val cx = panelX + panelW / 2

        graphics.drawCenteredString(font, "\u266B Playlist Queue \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = ClientMusicPlayer.getCurrentStatus()
        val queue = status?.queue ?: emptyList()
        val saved = savedNames()
        val queueListY = queueHeaderY()
        val queueRowsY = queueRowsY()
        val savedTitleY = savedHeaderY()
        val savedListY = savedRowsY()

        graphics.drawString(font, "Queue", panelX + 10, queueListY, 0xFF888888.toInt())
        val queueMaxScroll = (queue.size - visibleRows).coerceAtLeast(0)
        queueScrollOffset = queueScrollOffset.coerceIn(0, queueMaxScroll)

        if (queue.isEmpty()) {
            graphics.drawCenteredString(font, "Queue is empty", cx, queueRowsY + 18, 0xFF666666.toInt())
            graphics.drawCenteredString(font, "Use the Track Browser to add tracks", cx, queueRowsY + 34, 0xFF444466.toInt())
        }

        for (i in 0 until visibleRows) {
            val idx = i + queueScrollOffset
            if (idx >= queue.size) break

            val y = queueRowsY + i * rowH
            val trackId = queue[idx]

            if (i % 2 == 0) {
                graphics.fill(panelX + 4, y - 1, panelX + panelW - 4, y + rowH - 3, 0x20FFFFFF)
            }

            graphics.drawString(font, "${idx + 1}.", panelX + 10, y, 0xFF999999.toInt())

            val displayName = formatTrack(trackId)
            val nameW = panelW - 60
            val trimmed = if (font.width(displayName) > nameW) {
                font.plainSubstrByWidth(displayName, nameW) + "..."
            } else displayName
            graphics.drawString(font, trimmed, panelX + 24, y, 0xFFDDDDDD.toInt())

            if (isOp) {
                val xBtnX = panelX + panelW - 20
                val hovered = mouseX >= xBtnX && mouseX <= xBtnX + 12 && mouseY >= y - 1 && mouseY < y + rowH - 3
                val color = if (hovered) 0xFFFF5555.toInt() else 0xFF884444.toInt()
                graphics.drawString(font, "\u2716", xBtnX, y, color)
            }
        }

        if (queue.size > visibleRows) {
            val barTotalH = visibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((visibleRows.toFloat() / queue.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = queueRowsY + ((queueScrollOffset.toFloat() / queueMaxScroll) * (barTotalH - thumbH)).toInt()

            graphics.fill(barX, queueRowsY, barX + barW, queueRowsY + barTotalH, 0xFF222244.toInt())

            val thumbColor = if (draggingQueueScrollbar) 0xFF44FFAA.toInt() else 0xFF00CC66.toInt()
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        graphics.drawCenteredString(font, "Saved Playlists", cx, savedTitleY, 0xFF00CC66.toInt())
        val savedMaxScroll = (saved.size - savedVisibleRows).coerceAtLeast(0)
        savedScrollOffset = savedScrollOffset.coerceIn(0, savedMaxScroll)

        if (saved.isEmpty()) {
            graphics.drawCenteredString(font, "No saved playlists on this client", cx, savedListY + 20, 0xFF666666.toInt())
        }

        for (i in 0 until savedVisibleRows) {
            val idx = i + savedScrollOffset
            if (idx >= saved.size) break

            val y = savedListY + i * rowH
            val name = saved[idx]
            val isSelected = name == selectedSavedName
            if (isSelected) {
                graphics.fill(panelX + 5, y, panelX + panelW - 5, y + rowH - 2, 0xFF003322.toInt())
            } else if (i % 2 == 0) {
                graphics.fill(panelX + 5, y, panelX + panelW - 5, y + rowH - 2, 0x15000000)
            }

            val displayName = if (font.width(name) > panelW - 24) {
                font.plainSubstrByWidth(name, panelW - 24) + "..."
            } else name
            graphics.drawString(font, displayName, panelX + 10, y + 3, if (isSelected) 0xFF00FF88.toInt() else 0xFFDDDDDD.toInt())
        }

        if (saved.size > savedVisibleRows) {
            val barTotalH = savedVisibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((savedVisibleRows.toFloat() / saved.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = savedListY + ((savedScrollOffset.toFloat() / savedMaxScroll) * (barTotalH - thumbH)).toInt()

            graphics.fill(barX, savedListY, barX + barW, savedListY + barTotalH, 0xFF222244.toInt())

            val thumbColor = if (draggingSavedScrollbar) 0xFF44FFAA.toInt() else 0xFF00CC66.toInt()
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        graphics.drawString(font, "Name", panelX + 10, nameLabelY(), 0xFF888888.toInt())

        val canSave = getSaveableTracks().isNotEmpty()
        val canLoad = isOp && selectedSavedName != null
        val canDelete = selectedSavedName != null
        backBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) }
        saveBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canSave) }
        loadBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canLoad) }
        deleteBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canDelete) }

        if (!isOp) {
            graphics.drawCenteredString(font, "\u26A0 Load requires OP; saves are local to this client", cx, panelY + panelH - 70, 0xFFFF5555.toInt())
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

        val status = ClientMusicPlayer.getCurrentStatus()
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

        for (i in 0 until visibleRows) {
            val idx = i + queueScrollOffset
            if (idx >= queue.size) break

            val y = queueRowsY + i * rowH
            val trackId = queue[idx]

            if (i % 2 == 0) {
                GuiComponent.fill(poseStack, panelX + 4, y - 1, panelX + panelW - 4, y + rowH - 3, 0x20FFFFFF)
            }

            GuiComponent.drawString(poseStack, font, "${idx + 1}.", panelX + 10, y, 0xFF999999.toInt())

            val displayName = formatTrack(trackId)
            val nameW = panelW - 60
            val trimmed = if (font.width(displayName) > nameW) {
                font.plainSubstrByWidth(displayName, nameW) + "..."
            } else displayName
            GuiComponent.drawString(poseStack, font, trimmed, panelX + 24, y, 0xFFDDDDDD.toInt())

            if (isOp) {
                val xBtnX = panelX + panelW - 20
                val hovered = mouseX >= xBtnX && mouseX <= xBtnX + 12 && mouseY >= y - 1 && mouseY < y + rowH - 3
                val color = if (hovered) 0xFFFF5555.toInt() else 0xFF884444.toInt()
                GuiComponent.drawString(poseStack, font, "\u2716", xBtnX, y, color)
            }
        }

        if (queue.size > visibleRows) {
            val barTotalH = visibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((visibleRows.toFloat() / queue.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = queueRowsY + ((queueScrollOffset.toFloat() / queueMaxScroll) * (barTotalH - thumbH)).toInt()

            GuiComponent.fill(poseStack, barX, queueRowsY, barX + barW, queueRowsY + barTotalH, 0xFF222244.toInt())

            val thumbColor = if (draggingQueueScrollbar) 0xFF44FFAA.toInt() else 0xFF00CC66.toInt()
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
            if (idx >= saved.size) break

            val y = savedListY + i * rowH
            val name = saved[idx]
            val isSelected = name == selectedSavedName
            if (isSelected) {
                GuiComponent.fill(poseStack, panelX + 5, y, panelX + panelW - 5, y + rowH - 2, 0xFF003322.toInt())
            } else if (i % 2 == 0) {
                GuiComponent.fill(poseStack, panelX + 5, y, panelX + panelW - 5, y + rowH - 2, 0x15000000)
            }

            val displayName = if (font.width(name) > panelW - 24) {
                font.plainSubstrByWidth(name, panelW - 24) + "..."
            } else name
            GuiComponent.drawString(poseStack, font, displayName, panelX + 10, y + 3, if (isSelected) 0xFF00FF88.toInt() else 0xFFDDDDDD.toInt())
        }

        if (saved.size > savedVisibleRows) {
            val barTotalH = savedVisibleRows * rowH
            val barX = panelX + panelW - 10
            val barW = 6
            val thumbH = ((savedVisibleRows.toFloat() / saved.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = savedListY + ((savedScrollOffset.toFloat() / savedMaxScroll) * (barTotalH - thumbH)).toInt()

            GuiComponent.fill(poseStack, barX, savedListY, barX + barW, savedListY + barTotalH, 0xFF222244.toInt())

            val thumbColor = if (draggingSavedScrollbar) 0xFF44FFAA.toInt() else 0xFF00CC66.toInt()
            GuiComponent.fill(poseStack, barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        GuiComponent.drawString(poseStack, font, "Name", panelX + 10, nameLabelY(), 0xFF888888.toInt())

        val canSave1919 = getSaveableTracks().isNotEmpty()
        val canLoad1919 = isOp && selectedSavedName != null
        val canDelete1919 = selectedSavedName != null
        backBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) }
        saveBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canSave1919) }
        loadBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canLoad1919) }
        deleteBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canDelete1919) }

        if (!isOp) {
            GuiComponent.drawCenteredString(poseStack, font, "\u26A0 Load requires OP; saves are local to this client", cx, panelY + panelH - 70, 0xFFFF5555.toInt())
        }

        super.render(poseStack, mouseX, mouseY, partialTick)
    }*/
    //?}

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val status = ClientMusicPlayer.getCurrentStatus()
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
                val xBtnX = panelX + panelW - 20
                for (i in 0 until visibleRows) {
                    val idx = i + queueScrollOffset
                    if (idx >= queue.size) break
                    val y = queueListY + i * rowH
                    if (mouseX >= xBtnX && mouseX <= xBtnX + 12 && mouseY >= y - 1 && mouseY < y + rowH - 3) {
                        val packet = MusicControlPacket(
                            action = MusicControlPacket.Action.REMOVE_FROM_QUEUE,
                            trackId = null,
                            queuePosition = idx
                        )
                        PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), packet)
                        return true
                    }
                }
            }

            if (mouseX >= panelX + 5 && mouseX <= panelX + panelW - 14 &&
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
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingQueueScrollbar && button == 0) {
            val queueSize = ClientMusicPlayer.getCurrentStatus()?.queue?.size ?: 0
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && (draggingQueueScrollbar || draggingSavedScrollbar)) {
            draggingQueueScrollbar = false
            draggingSavedScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val queueAreaTop = queueRowsY()
        val queueAreaBottom = queueAreaTop + visibleRows * rowH
        val savedAreaTop = savedRowsY()
        val savedAreaBottom = savedAreaTop + savedVisibleRows * rowH

        if (mouseY >= savedAreaTop && mouseY <= savedAreaBottom) {
            val maxScroll = (savedNames().size - savedVisibleRows).coerceAtLeast(0)
            savedScrollOffset = (savedScrollOffset - delta.toInt()).coerceIn(0, maxScroll)
        } else {
            val queueSize = ClientMusicPlayer.getCurrentStatus()?.queue?.size ?: 0
            val maxScroll = (queueSize - visibleRows).coerceAtLeast(0)
            queueScrollOffset = (queueScrollOffset - delta.toInt()).coerceIn(0, maxScroll)
        }
        return true
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dev.mcrib884.musync.MuSyncForge.MUSIC_GUI_KEY.matches(keyCode, scanCode)) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    //? if >=1.20 {
    private fun drawCustomBtn(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, label: String, hovered: Boolean, active: Boolean = true) {
        val bg = when { !active -> 0xFF111118.toInt(); hovered -> 0xFF334433.toInt(); else -> 0xFF1C1C2A.toInt() }
        val borderColor = if (active) 0xFF00CC66.toInt() else 0xFF336644.toInt()
        val textColor = if (active) 0xFFFFFFFF.toInt() else 0xFF667766.toInt()
        graphics.fill(x, y, x + w, y + h, bg)
        graphics.fill(x, y, x + w, y + 1, borderColor)
        graphics.fill(x, y, x + 1, y + h, borderColor)
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor)
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor)
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor)
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
        GuiComponent.drawString(poseStack, font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor)
    }*/
    //?}

}
