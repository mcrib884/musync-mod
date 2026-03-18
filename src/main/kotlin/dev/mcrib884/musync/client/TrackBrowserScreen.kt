package dev.mcrib884.musync.client

import dev.mcrib884.musync.command.MuSyncCommand
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

class TrackBrowserScreen : Screen(Component.literal("MuSync - Tracks")) {

    private val panelW = 280
    private val panelH = 256
    private var panelX = 0
    private var panelY = 0

    private var scrollOffset = 0
    private val visibleRows = 9
    private val rowH = 16

    private var selectedTrackKey: String? = null

    private var draggingScrollbar = false

    private data class BtnBounds(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, var active: Boolean = true)

    private var backBounds: BtnBounds? = null
    private var playBounds: BtnBounds? = null
    private var queueBounds: BtnBounds? = null
    private var searchField: EditBox? = null

    private val isOp: Boolean
        get() = ClientOnlyController.isActive || Minecraft.getInstance().player?.hasPermissions(2) == true

    private val tracks: List<Pair<String, String>> by lazy {
        val base = MuSyncCommand.getAllTracksForBrowser().toMutableList()
        if (ClientOnlyController.isActive) {
            for (name in ClientOnlyController.getLocalCustomTracks()) {
                val key = "custom:$name"
                if (base.none { it.first == key }) {
                    val displayName = "[Local] " + dev.mcrib884.musync.TrackNames.formatCustomTrackName(name)
                    base.add(key to displayName)
                }
            }
        }
        base
    }

    private fun filteredTracks(): List<Pair<String, String>> {
        val query = searchField?.value?.trim()?.lowercase().orEmpty()
        if (query.isEmpty()) return tracks
        return tracks.filter { (key, displayName) ->
            key.lowercase().contains(query) || displayName.lowercase().contains(query)
        }
    }

    private fun currentSelectionIndex(items: List<Pair<String, String>>): Int {
        val selected = selectedTrackKey ?: return -1
        return items.indexOfFirst { it.first == selected }
    }

    private fun syncSelectionToFilter() {
        val filteredKeys = filteredTracks().map { it.first }.toSet()
        if (selectedTrackKey !in filteredKeys) selectedTrackKey = null
        scrollOffset = 0
    }

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        searchField = EditBox(font, panelX + 10, panelY + 25, panelW - 20, 14, Component.literal("Search tracks"))
        searchField!!.setMaxLength(64)
        searchField!!.setBordered(false)
        searchField!!.setTextColor(0xFFCCCCCC.toInt())
        searchField!!.setTextColorUneditable(0xFF888888.toInt())
        searchField!!.setResponder { syncSelectionToFilter() }
        addRenderableWidget(searchField!!)

        val btnY = panelY + panelH - 26
        val btnW = 70

        backBounds = BtnBounds(panelX + 6, btnY, 50, 16, "\u2190 Back")
        playBounds = BtnBounds(panelX + panelW - btnW * 2 - 12, btnY, btnW, 16, "\u25B6 Play Now", false)
        queueBounds = BtnBounds(panelX + panelW - btnW - 6, btnY, btnW, 16, "+ Queue", false)
    }

    //? if >=1.21 {
    /*override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}*/
    //?}

    //? if >=1.20 {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        //? if <1.21 {
        renderBackground(graphics)
        //?}

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val cx = panelX + panelW / 2
        val visibleTracks = filteredTracks()

        graphics.drawCenteredString(font, "\u266B Track Browser \u266B", cx, panelY + 8, 0xFF00CC66.toInt())
        graphics.drawString(font, "Search", panelX + 8, panelY + 12, 0xFF888888.toInt())

        val sfY = panelY + 23
        val sfFocused = searchField?.isFocused == true
        val sfBc = if (sfFocused) 0xFF33EE88.toInt() else 0xCC4E8C6A.toInt()
        graphics.fill(panelX + 9, sfY, panelX + panelW - 9, sfY + 18, 0xFF171A25.toInt())
        graphics.fill(panelX + 9, sfY, panelX + panelW - 9, sfY + 1, sfBc)
        graphics.fill(panelX + 9, sfY, panelX + 10, sfY + 18, sfBc)
        graphics.fill(panelX + panelW - 10, sfY, panelX + panelW - 9, sfY + 18, sfBc)
        graphics.fill(panelX + 9, sfY + 17, panelX + panelW - 9, sfY + 18, sfBc)
        graphics.fill(panelX + 10, sfY + 1, panelX + panelW - 10, sfY + 17, 0x331A2230)

        val countLabel = if (visibleTracks.size == tracks.size) {
            "${tracks.size} tracks available"
        } else {
            "${visibleTracks.size} of ${tracks.size} tracks"
        }
        graphics.drawCenteredString(font, countLabel, cx, panelY + 44, 0xFF666688.toInt())

        val listY = panelY + 56
        val listH = visibleRows * rowH

        graphics.fill(panelX + 4, listY - 2, panelX + panelW - 4, listY + listH + 2, 0x30000000)

        val maxScroll = (visibleTracks.size - visibleRows).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        if (visibleTracks.isEmpty()) {
            graphics.drawCenteredString(font, "No matching tracks", cx, listY + 28, 0xFF666666.toInt())
        }

        val selectedIndex = currentSelectionIndex(visibleTracks)
        for (i in 0 until visibleRows) {
            val idx = i + scrollOffset
            val slotY = listY + i * rowH
            val rowTop = slotY + 1
            val rowBottom = slotY + rowH - 1
            val rowLeft = panelX + 6
            val rowRight = panelX + panelW - 14
            val hasTrack = idx < visibleTracks.size
            val isSelected = idx == selectedIndex
            val rowHovered = hasTrack && mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom
            val rowFill = when {
                isSelected -> 0x661F3A31.toInt()
                rowHovered -> 0x6634364A.toInt()
                hasTrack && i % 2 == 0 -> 0x442A2C3E.toInt()
                hasTrack -> 0x33202233.toInt()
                else -> 0x22151726.toInt()
            }
            val rowBorder = if (isSelected) 0xAA00CC66.toInt() else if (hasTrack) 0x884A4D68.toInt() else 0x442A2C40.toInt()

            graphics.fill(rowLeft, rowTop, rowRight, rowBottom, rowFill)
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + 1, rowBorder)
            graphics.fill(rowLeft, rowBottom - 1, rowRight, rowBottom, rowBorder)
            graphics.fill(rowLeft, rowTop, rowLeft + 1, rowBottom, rowBorder)
            graphics.fill(rowRight - 1, rowTop, rowRight, rowBottom, rowBorder)

            if (!hasTrack) continue

            val (_, displayName) = visibleTracks[idx]
            val textColor = if (isSelected) 0xFF00FF88.toInt() else 0xFFDDDDDD.toInt()
            val maxNameW = panelW - 34
            val trimmed = if (font.width(displayName) > maxNameW) {
                font.plainSubstrByWidth(displayName, maxNameW) + "..."
            } else displayName
            val textY = rowTop + ((rowBottom - rowTop) - font.lineHeight) / 2 + 1
            graphics.drawString(font, trimmed, rowLeft + 6, textY, textColor)
        }

        if (visibleTracks.size > visibleRows) {
            val barX = panelX + panelW - 10
            val barW = 6
            val barTotalH = listH
            val thumbH = ((visibleRows.toFloat() / visibleTracks.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (barTotalH - thumbH)).toInt()

            graphics.fill(barX, listY, barX + barW, listY + barTotalH, 0xFF151726.toInt())
            graphics.fill(barX, listY, barX + barW, listY + 1, 0xFF2A2C40.toInt())
            graphics.fill(barX, listY + barTotalH - 1, barX + barW, listY + barTotalH, 0xFF2A2C40.toInt())
            val thumbColor = if (draggingScrollbar) 0xFF33EE88.toInt() else 0xFF00CC66.toInt()
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        val hasSelection = selectedIndex >= 0 && selectedIndex < visibleTracks.size
        val canAct = hasSelection && isOp
        backBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) }
        playBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canAct) }
        queueBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canAct) }

        if (hasSelection) {
            val (_, selDisplay) = visibleTracks[selectedIndex]
            graphics.drawCenteredString(font, "Selected: $selDisplay", cx, panelY + panelH - 44, 0xFF00CC66.toInt())
        }

        if (!isOp && !ClientOnlyController.isActive) {
            graphics.drawCenteredString(font, "\u26A0 View only (OP required)", cx, panelY + panelH - 44, 0xFFFF5555.toInt())
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

        GuiComponent.drawCenteredString(poseStack, font, "\u266B Track Browser \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val visibleTracks = filteredTracks()
        GuiComponent.drawString(poseStack, font, "Search", panelX + 8, panelY + 14, 0xFF888888.toInt())
        val sfY = panelY + 23
        val sfFocused = searchField?.isFocused == true
        val sfBc = if (sfFocused) 0xFF33EE88.toInt() else 0xCC4E8C6A.toInt()
        GuiComponent.fill(poseStack, panelX + 9, sfY, panelX + panelW - 9, sfY + 18, 0xFF171A25.toInt())
        GuiComponent.fill(poseStack, panelX + 9, sfY, panelX + panelW - 9, sfY + 1, sfBc)
        GuiComponent.fill(poseStack, panelX + 9, sfY, panelX + 10, sfY + 18, sfBc)
        GuiComponent.fill(poseStack, panelX + panelW - 10, sfY, panelX + panelW - 9, sfY + 18, sfBc)
        GuiComponent.fill(poseStack, panelX + 9, sfY + 17, panelX + panelW - 9, sfY + 18, sfBc)
        GuiComponent.fill(poseStack, panelX + 10, sfY + 1, panelX + panelW - 10, sfY + 17, 0x331A2230)

        val countLabel = if (visibleTracks.size == tracks.size) {
            "${tracks.size} tracks available"
        } else {
            "${visibleTracks.size} of ${tracks.size} tracks"
        }
        GuiComponent.drawCenteredString(poseStack, font, countLabel, cx, panelY + 42, 0xFF666688.toInt())

        val listY = panelY + 56
        val listH = visibleRows * rowH

        GuiComponent.fill(poseStack, panelX + 4, listY - 2, panelX + panelW - 4, listY + listH + 2, 0x30000000)

        val maxScroll = (visibleTracks.size - visibleRows).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        if (visibleTracks.isEmpty()) {
            GuiComponent.drawCenteredString(poseStack, font, "No matching tracks", cx, listY + 28, 0xFF666666.toInt())
        }

        val selectedIndex = currentSelectionIndex(visibleTracks)

        for (i in 0 until visibleRows) {
            val idx = i + scrollOffset
            val slotY = listY + i * rowH
            val rowTop = slotY + 1
            val rowBottom = slotY + rowH - 1
            val rowLeft = panelX + 6
            val rowRight = panelX + panelW - 14
            val hasTrack = idx < visibleTracks.size
            val isSelected = idx == selectedIndex
            val rowHovered = hasTrack && mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom
            val rowFill = when {
                isSelected -> 0x661F3A31.toInt()
                rowHovered -> 0x6634364A.toInt()
                hasTrack && i % 2 == 0 -> 0x442A2C3E.toInt()
                hasTrack -> 0x33202233.toInt()
                else -> 0x22151726.toInt()
            }
            val rowBorder = if (isSelected) 0xAA00CC66.toInt() else if (hasTrack) 0x884A4D68.toInt() else 0x442A2C40.toInt()

            GuiComponent.fill(poseStack, rowLeft, rowTop, rowRight, rowBottom, rowFill)
            GuiComponent.fill(poseStack, rowLeft, rowTop, rowRight, rowTop + 1, rowBorder)
            GuiComponent.fill(poseStack, rowLeft, rowBottom - 1, rowRight, rowBottom, rowBorder)
            GuiComponent.fill(poseStack, rowLeft, rowTop, rowLeft + 1, rowBottom, rowBorder)
            GuiComponent.fill(poseStack, rowRight - 1, rowTop, rowRight, rowBottom, rowBorder)

            if (!hasTrack) continue

            val (_, displayName) = visibleTracks[idx]
            val textColor = if (isSelected) 0xFF00FF88.toInt() else 0xFFDDDDDD.toInt()
            val maxNameW = panelW - 34
            val trimmed = if (font.width(displayName) > maxNameW) {
                font.plainSubstrByWidth(displayName, maxNameW) + "..."
            } else displayName
            val textY = rowTop + ((rowBottom - rowTop) - font.lineHeight) / 2 + 1
            GuiComponent.drawString(poseStack, font, trimmed, rowLeft + 6, textY, textColor)
        }

        if (visibleTracks.size > visibleRows) {
            val barX = panelX + panelW - 10
            val barW = 6
            val barTotalH = listH
            val thumbH = ((visibleRows.toFloat() / visibleTracks.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (barTotalH - thumbH)).toInt()

            GuiComponent.fill(poseStack, barX, listY, barX + barW, listY + barTotalH, 0xFF151726.toInt())
            GuiComponent.fill(poseStack, barX, listY, barX + barW, listY + 1, 0xFF2A2C40.toInt())
            GuiComponent.fill(poseStack, barX, listY + barTotalH - 1, barX + barW, listY + barTotalH, 0xFF2A2C40.toInt())
            val thumbColor = if (draggingScrollbar) 0xFF33EE88.toInt() else 0xFF00CC66.toInt()
            GuiComponent.fill(poseStack, barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        val hasSelection = selectedIndex >= 0 && selectedIndex < visibleTracks.size
        val canAct1919 = hasSelection && isOp
        backBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) }
        playBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canAct1919) }
        queueBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canAct1919) }

        if (hasSelection) {
            val (_, selDisplay) = visibleTracks[selectedIndex]
            GuiComponent.drawCenteredString(poseStack, font, "Selected: $selDisplay", cx, panelY + panelH - 44, 0xFF00CC66.toInt())
        }

        if (!isOp && !ClientOnlyController.isActive) {
            GuiComponent.drawCenteredString(poseStack, font, "\u26A0 View only (OP required)", cx, panelY + panelH - 44, 0xFFFF5555.toInt())
        }

        super.render(poseStack, mouseX, mouseY, partialTick)
    }*/
    //?}

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val visibleTracks = filteredTracks()
            val listY = panelY + 56
            val listH = visibleRows * rowH
            val maxScroll = (visibleTracks.size - visibleRows).coerceAtLeast(0)

            if (visibleTracks.size > visibleRows) {
                val barX = panelX + panelW - 10
                val barW = 6
                if (mouseX >= barX && mouseX <= barX + barW &&
                    mouseY >= listY && mouseY <= listY + listH) {
                    draggingScrollbar = true

                    val ratio = ((mouseY - listY) / listH).toFloat().coerceIn(0f, 1f)
                    scrollOffset = (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
                    return true
                }
            }

            if (isOp && mouseX >= panelX + 6 && mouseX < panelX + panelW - 14 &&
                mouseY >= listY && mouseY < listY + listH) {
                val rowIdx = ((mouseY - listY) / rowH).toInt()
                val idx = rowIdx + scrollOffset
                if (idx in visibleTracks.indices) {
                    val clickedKey = visibleTracks[idx].first
                    selectedTrackKey = if (selectedTrackKey == clickedKey) null else clickedKey
                    return true
                }
            }

            val mx = mouseX.toInt(); val my = mouseY.toInt()
            if (backBounds?.let { mx in it.x until it.x + it.w && my in it.y until it.y + it.h } == true) {
                Minecraft.getInstance().setScreen(MusicControlScreen()); return true
            }
            val hasSelection = selectedTrackKey != null && isOp
            if (hasSelection && playBounds?.let { mx in it.x until it.x + it.w && my in it.y until it.y + it.h } == true) {
                playSelected(); return true
            }
            if (hasSelection && queueBounds?.let { mx in it.x until it.x + it.w && my in it.y until it.y + it.h } == true) {
                queueSelected(); return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            val listY = panelY + 56
            val listH = visibleRows * rowH
            val maxScroll = (filteredTracks().size - visibleRows).coerceAtLeast(0)
            val ratio = ((mouseY - listY) / listH).toFloat().coerceIn(0f, 1f)
            scrollOffset = (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    //? if >=1.21 {
    /*override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, delta: Double): Boolean {*/
    //?} else {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
    //?}
        val maxScroll = (filteredTracks().size - visibleRows).coerceAtLeast(0)
        scrollOffset = (scrollOffset - delta.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dev.mcrib884.musync.KeyBindings.MUSIC_GUI_KEY.matches(keyCode, scanCode)) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun playSelected() {
        val key = selectedTrackKey ?: return
        if (ClientOnlyController.isActive) {
            ClientOnlyController.playTrack(key)
            return
        }
        val packet = MusicControlPacket(
            action = MusicControlPacket.Action.PLAY_TRACK,
            trackId = key,
            queuePosition = null
        )
        PacketHandler.sendToServer(packet)
    }

    private fun queueSelected() {
        val key = selectedTrackKey ?: return
        if (ClientOnlyController.isActive) {
            ClientOnlyController.addToQueue(key)
            return
        }
        val packet = MusicControlPacket(
            action = MusicControlPacket.Action.ADD_TO_QUEUE,
            trackId = key,
            queuePosition = null
        )
        PacketHandler.sendToServer(packet)
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
