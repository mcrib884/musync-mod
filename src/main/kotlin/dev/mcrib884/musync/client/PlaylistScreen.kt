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
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraftforge.network.PacketDistributor

class PlaylistScreen : Screen(Component.literal("MuSync - Playlist")) {

    private val panelW = 260
    private val panelH = 220
    private var panelX = 0
    private var panelY = 0

    private var scrollOffset = 0
    private val visibleRows = 8
    private val rowH = 16

    private var draggingScrollbar = false

    private val isOp: Boolean
        get() = Minecraft.getInstance().player?.hasPermissions(2) == true

    private fun formatTrack(id: String): String {
        return dev.mcrib884.musync.TrackNames.formatTrack(id)
    }

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        //? if >=1.20 {
        addRenderableWidget(Button.builder(Component.literal("\u2190 Back")) {
            Minecraft.getInstance().setScreen(MusicControlScreen())
        }.bounds(panelX + 6, panelY + panelH - 24, 50, 18).build())
        //?} else {
        /*addRenderableWidget(Button(panelX + 6, panelY + panelH - 24, 50, 18, Component.literal("\u2190 Back")) {
            Minecraft.getInstance().setScreen(MusicControlScreen())
        })*/
        //?}
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

        if (queue.isEmpty()) {
            graphics.drawCenteredString(font, "Queue is empty", cx, panelY + 60, 0xFF666666.toInt())
            graphics.drawCenteredString(font, "Use the Track Browser to add tracks", cx, panelY + 76, 0xFF444466.toInt())
        } else {

            val listY = panelY + 26
            graphics.drawString(font, "#", panelX + 10, listY, 0xFF888888.toInt())
            graphics.drawString(font, "Track", panelX + 24, listY, 0xFF888888.toInt())

            val maxScroll = (queue.size - visibleRows).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)

            for (i in 0 until visibleRows) {
                val idx = i + scrollOffset
                if (idx >= queue.size) break

                val y = listY + 14 + i * rowH
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
                val barStartY = listY + 14
                val barX = panelX + panelW - 10
                val barW = 6
                val thumbH = ((visibleRows.toFloat() / queue.size) * barTotalH).toInt().coerceAtLeast(10)
                val thumbY = barStartY + ((scrollOffset.toFloat() / maxScroll) * (barTotalH - thumbH)).toInt()

                graphics.fill(barX, barStartY, barX + barW, barStartY + barTotalH, 0xFF222244.toInt())

                val thumbColor = if (draggingScrollbar) 0xFF44FFAA.toInt() else 0xFF00CC66.toInt()
                graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
            }

            graphics.drawCenteredString(font, "${queue.size} track${if (queue.size > 1) "s" else ""} in queue", cx, panelY + panelH - 36, 0xFF666688.toInt())
        }

        if (!isOp) {
            graphics.drawCenteredString(font, "\u26A0 View only (OP required)", cx, panelY + panelH - 12, 0xFFFF5555.toInt())
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

        if (queue.isEmpty()) {
            GuiComponent.drawCenteredString(poseStack, font, "Queue is empty", cx, panelY + 60, 0xFF666666.toInt())
            GuiComponent.drawCenteredString(poseStack, font, "Use the Track Browser to add tracks", cx, panelY + 76, 0xFF444466.toInt())
        } else {

            val listY = panelY + 26
            GuiComponent.drawString(poseStack, font, "#", panelX + 10, listY, 0xFF888888.toInt())
            GuiComponent.drawString(poseStack, font, "Track", panelX + 24, listY, 0xFF888888.toInt())

            val maxScroll = (queue.size - visibleRows).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)

            for (i in 0 until visibleRows) {
                val idx = i + scrollOffset
                if (idx >= queue.size) break

                val y = listY + 14 + i * rowH
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
                val barStartY = listY + 14
                val barX = panelX + panelW - 10
                val barW = 6
                val thumbH = ((visibleRows.toFloat() / queue.size) * barTotalH).toInt().coerceAtLeast(10)
                val thumbY = barStartY + ((scrollOffset.toFloat() / maxScroll) * (barTotalH - thumbH)).toInt()

                GuiComponent.fill(poseStack, barX, barStartY, barX + barW, barStartY + barTotalH, 0xFF222244.toInt())

                val thumbColor = if (draggingScrollbar) 0xFF44FFAA.toInt() else 0xFF00CC66.toInt()
                GuiComponent.fill(poseStack, barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
            }

            GuiComponent.drawCenteredString(poseStack, font, "${queue.size} track${if (queue.size > 1) "s" else ""} in queue", cx, panelY + panelH - 36, 0xFF666688.toInt())
        }

        if (!isOp) {
            GuiComponent.drawCenteredString(poseStack, font, "\u26A0 View only (OP required)", cx, panelY + panelH - 12, 0xFFFF5555.toInt())
        }

        super.render(poseStack, mouseX, mouseY, partialTick)
    }*/
    //?}

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val status = ClientMusicPlayer.getCurrentStatus()
            val queue = status?.queue ?: emptyList()
            val listY = panelY + 26 + 14
            val maxScroll = (queue.size - visibleRows).coerceAtLeast(0)

            if (queue.size > visibleRows) {
                val barX = panelX + panelW - 10
                val barW = 6
                val barTotalH = visibleRows * rowH
                if (mouseX >= barX && mouseX <= barX + barW &&
                    mouseY >= listY && mouseY <= listY + barTotalH) {
                    draggingScrollbar = true
                    val ratio = ((mouseY - listY) / barTotalH).toFloat().coerceIn(0f, 1f)
                    scrollOffset = (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
                    return true
                }
            }

            if (isOp) {
                val xBtnX = panelX + panelW - 20
                for (i in 0 until visibleRows) {
                    val idx = i + scrollOffset
                    if (idx >= queue.size) break
                    val y = listY + i * rowH
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
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            val status = ClientMusicPlayer.getCurrentStatus()
            val queueSize = status?.queue?.size ?: 0
            val listY = panelY + 26 + 14
            val barTotalH = visibleRows * rowH
            val maxScroll = (queueSize - visibleRows).coerceAtLeast(0)
            val ratio = ((mouseY - listY) / barTotalH).toFloat().coerceIn(0f, 1f)
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
        val status = ClientMusicPlayer.getCurrentStatus()
        val queueSize = status?.queue?.size ?: 0
        val maxScroll = (queueSize - visibleRows).coerceAtLeast(0)
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

}
