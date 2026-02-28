package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.MusicControlPacket
import dev.mcrib884.musync.network.PacketHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
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

    private val OGG_NAMES: Map<String, String> = mapOf(
        "music/game/calm1" to "Minecraft", "music/game/calm2" to "Clark", "music/game/calm3" to "Sweden",
        "music/game/hal1" to "Subwoofer Lullaby", "music/game/hal2" to "Living Mice",
        "music/game/hal3" to "Haggstrom", "music/game/hal4" to "Danny",
        "music/game/nuance1" to "Key", "music/game/nuance2" to "Oxygene",
        "music/game/piano1" to "Dry Hands", "music/game/piano2" to "Wet Hands",
        "music/game/piano3" to "Mice on Venus",
        "music/game/creative/creative1" to "Biome Fest", "music/game/creative/creative2" to "Blind Spots",
        "music/game/creative/creative3" to "Haunt Muskie", "music/game/creative/creative4" to "Aria Math",
        "music/game/creative/creative5" to "Dreiton", "music/game/creative/creative6" to "Taswell",
        "music/menu/menu1" to "Mutation", "music/menu/menu2" to "Moog City 2",
        "music/menu/menu3" to "Beginning 2", "music/menu/menu4" to "Floating Trees",
        "music/game/nether/rubedo" to "Rubedo", "music/game/nether/chrysopoeia" to "Chrysopoeia",
        "music/game/nether/so_below" to "So Below",
        "music/game/nether/concrete_halls" to "Concrete Halls", "music/game/nether/dead_voxel" to "Dead Voxel",
        "music/game/nether/warmth" to "Warmth", "music/game/nether/ballad_of_the_cats" to "Ballad of the Cats",
        "music/game/end/end" to "The End", "music/game/end/boss" to "Boss",
        "music/game/end/credits" to "Alpha",
        "music/game/stand_tall" to "Stand Tall", "music/game/left_to_bloom" to "Left to Bloom",
        "music/game/one_more_day" to "One More Day", "music/game/infinite_amethyst" to "Infinite Amethyst",
        "music/game/wending" to "Wending", "music/game/ancestry" to "Ancestry",
        "music/game/comforting_memories" to "Comforting Memories", "music/game/floating_dream" to "Floating Dream",
        "music/game/an_ordinary_day" to "An Ordinary Day", "music/game/echo_in_the_wind" to "Echo in the Wind",
        "music/game/a_familiar_room" to "A Familiar Room", "music/game/bromeliad" to "Bromeliad",
        "music/game/crescent_dunes" to "Crescent Dunes", "music/game/firebugs" to "Firebugs",
        "music/game/labyrinthine" to "Labyrinthine", "music/game/eld_unknown" to "Eld Unknown",
        "music/game/deeper" to "Deeper", "music/game/featherfall" to "Featherfall",
        "music/game/water/axolotl" to "Axolotl", "music/game/water/dragon_fish" to "Dragon Fish",
        "music/game/water/shuniji" to "Shuniji",
        "music/game/swamp/aerie" to "Aerie", "music/game/swamp/firebugs" to "Firebugs (Swamp)",
    )

    private val POOL_NAMES: Map<String, String> = mapOf(
        "minecraft:music.game" to "Game", "minecraft:music.creative" to "Creative",
        "minecraft:music.menu" to "Menu", "minecraft:music.under_water" to "Underwater",
        "minecraft:music.end" to "The End", "minecraft:music.dragon" to "Dragon Fight",
        "minecraft:music.credits" to "Credits",
        "minecraft:music.nether.basalt_deltas" to "Basalt Deltas",
        "minecraft:music.nether.crimson_forest" to "Crimson Forest",
        "minecraft:music.nether.nether_wastes" to "Nether Wastes",
        "minecraft:music.nether.soul_sand_valley" to "Soul Sand Valley",
        "minecraft:music.nether.warped_forest" to "Warped Forest",
    )

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        addRenderableWidget(Button.builder(Component.literal("\u2190 Back")) {
            Minecraft.getInstance().setScreen(MusicControlScreen())
        }.bounds(panelX + 6, panelY + panelH - 24, 50, 18).build())
    }

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

    private fun formatTrack(id: String): String {

        if (id.contains("|")) {
            val oggPath = id.split("|", limit = 2)[1]
            return OGG_NAMES[oggPath] ?: oggPath.substringAfterLast("/")
                .replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        if (id.startsWith("custom:")) {
            return "[Custom] " + id.removePrefix("custom:")
                .replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        return POOL_NAMES[id] ?: id.removePrefix("minecraft:music.")
            .replace(".", " ").replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}
