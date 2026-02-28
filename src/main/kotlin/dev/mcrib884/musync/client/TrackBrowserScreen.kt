package dev.mcrib884.musync.client

import dev.mcrib884.musync.command.MuSyncCommand
import dev.mcrib884.musync.network.MusicControlPacket
import dev.mcrib884.musync.network.PacketHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraftforge.network.PacketDistributor

class TrackBrowserScreen : Screen(Component.literal("MuSync - Tracks")) {

    private val panelW = 280
    private val panelH = 240
    private var panelX = 0
    private var panelY = 0

    private var scrollOffset = 0
    private val visibleRows = 10
    private val rowH = 16

    private var selectedIndex: Int = -1

    private var draggingScrollbar = false

    private var playBtn: Button? = null
    private var queueBtn: Button? = null

    private val isOp: Boolean
        get() = Minecraft.getInstance().player?.hasPermissions(2) == true

    private val tracks: List<Pair<String, String>> by lazy {
        MuSyncCommand.getAllTracksForBrowser()
    }

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        val btnY = panelY + panelH - 26
        val btnW = 70

        addRenderableWidget(Button.builder(Component.literal("\u2190 Back")) {
            Minecraft.getInstance().setScreen(MusicControlScreen())
        }.bounds(panelX + 6, btnY, 50, 18).build())

        playBtn = addRenderableWidget(Button.builder(Component.literal("\u25B6 Play Now")) {
            playSelected()
        }.bounds(panelX + panelW - btnW * 2 - 12, btnY, btnW, 18).build())
        playBtn!!.active = false

        queueBtn = addRenderableWidget(Button.builder(Component.literal("+ Queue")) {
            queueSelected()
        }.bounds(panelX + panelW - btnW - 6, btnY, btnW, 18).build())
        queueBtn!!.active = false
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val cx = panelX + panelW / 2

        graphics.drawCenteredString(font, "\u266B Track Browser \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        graphics.drawCenteredString(font, "${tracks.size} tracks available", cx, panelY + 20, 0xFF666688.toInt())

        val listY = panelY + 34
        val listH = visibleRows * rowH

        graphics.fill(panelX + 4, listY - 2, panelX + panelW - 4, listY + listH + 2, 0x30000000)

        val maxScroll = (tracks.size - visibleRows).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        for (i in 0 until visibleRows) {
            val idx = i + scrollOffset
            if (idx >= tracks.size) break

            val y = listY + i * rowH
            val (_, displayName) = tracks[idx]
            val isSelected = idx == selectedIndex
            val isHovered = mouseX >= panelX + 6 && mouseX <= panelX + panelW - 10 &&
                    mouseY >= y && mouseY < y + rowH

            val bgColor = when {
                isSelected -> 0xFF003322.toInt()
                isHovered -> 0x30FFFFFF
                i % 2 == 0 -> 0x15FFFFFF
                else -> 0x00000000
            }
            if (bgColor != 0) {
                graphics.fill(panelX + 5, y, panelX + panelW - 5, y + rowH, bgColor)
            }

            if (isSelected) {
                graphics.fill(panelX + 5, y, panelX + 7, y + rowH, 0xFF00CC66.toInt())
            }

            val textColor = if (isSelected) 0xFF00FF88.toInt() else 0xFFDDDDDD.toInt()
            val maxNameW = panelW - 24
            val trimmed = if (font.width(displayName) > maxNameW) {
                font.plainSubstrByWidth(displayName, maxNameW) + "..."
            } else displayName
            graphics.drawString(font, trimmed, panelX + 12, y + 4, textColor)
        }

        if (tracks.size > visibleRows) {
            val barX = panelX + panelW - 10
            val barW = 6
            val barTotalH = listH
            val thumbH = ((visibleRows.toFloat() / tracks.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (barTotalH - thumbH)).toInt()

            graphics.fill(barX, listY, barX + barW, listY + barTotalH, 0xFF222244.toInt())

            val thumbColor = if (draggingScrollbar) 0xFF44FFAA.toInt() else 0xFF00CC66.toInt()
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        val hasSelection = selectedIndex >= 0 && selectedIndex < tracks.size
        playBtn?.active = hasSelection && isOp
        queueBtn?.active = hasSelection && isOp

        if (hasSelection) {
            val (_, selDisplay) = tracks[selectedIndex]
            graphics.drawCenteredString(font, "Selected: $selDisplay", cx, panelY + panelH - 44, 0xFF00CC66.toInt())
        }

        if (!isOp) {
            graphics.drawCenteredString(font, "\u26A0 View only (OP required)", cx, panelY + panelH - 44, 0xFFFF5555.toInt())
        }

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val listY = panelY + 34
            val listH = visibleRows * rowH
            val maxScroll = (tracks.size - visibleRows).coerceAtLeast(0)

            if (tracks.size > visibleRows) {
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

            if (isOp && mouseX >= panelX + 5 && mouseX <= panelX + panelW - 14 &&
                mouseY >= listY && mouseY < listY + listH) {
                val rowIdx = ((mouseY - listY) / rowH).toInt()
                val idx = rowIdx + scrollOffset
                if (idx in tracks.indices) {
                    selectedIndex = if (selectedIndex == idx) -1 else idx
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            val listY = panelY + 34
            val listH = visibleRows * rowH
            val maxScroll = (tracks.size - visibleRows).coerceAtLeast(0)
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val maxScroll = (tracks.size - visibleRows).coerceAtLeast(0)
        scrollOffset = (scrollOffset - delta.toInt()).coerceIn(0, maxScroll)
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

    private fun playSelected() {
        if (selectedIndex < 0 || selectedIndex >= tracks.size) return
        val (key, _) = tracks[selectedIndex]
        val packet = MusicControlPacket(
            action = MusicControlPacket.Action.PLAY_TRACK,
            trackId = key,
            queuePosition = null
        )
        PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), packet)
    }

    private fun queueSelected() {
        if (selectedIndex < 0 || selectedIndex >= tracks.size) return
        val (key, _) = tracks[selectedIndex]
        val packet = MusicControlPacket(
            action = MusicControlPacket.Action.ADD_TO_QUEUE,
            trackId = key,
            queuePosition = null
        )
        PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), packet)
    }
}
