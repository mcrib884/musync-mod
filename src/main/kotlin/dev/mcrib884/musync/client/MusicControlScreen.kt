package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.MusicControlPacket
import dev.mcrib884.musync.network.MusicStatusPacket
import dev.mcrib884.musync.network.PacketHandler
import net.minecraft.client.Minecraft
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent*/
//?}
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
//? if >=1.20
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraftforge.network.PacketDistributor
import dev.mcrib884.musync.entityLevel

class MusicControlScreen : Screen(Component.literal("MuSync")) {

    private val panelW = 260
    private val panelH = 240

    private var lastSeekTimeMs: Long = 0
    private val SEEK_COOLDOWN_MS = 500L
    private var panelX = 0
    private var panelY = 0
    private val barH = 8
    private val barW = 220
    private var barX = 0
    private var barY = 0

    private var stopBtn: Button? = null
    private var pauseBtn: Button? = null
    private var skipBtn: Button? = null
    private var minDelayField: EditBox? = null
    private var maxDelayField: EditBox? = null
    private var applyBtn: Button? = null
    private var resetBtn: Button? = null
    private var dimSyncBtn: Button? = null
    private var syncAllBtn: Button? = null
    private var dimLabel: String = "?"

    private val isOp: Boolean
        get() = Minecraft.getInstance().player?.hasPermissions(2) == true

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
        barX = panelX + (panelW - barW) / 2
        barY = panelY + 82

        val op = isOp
        val btnW = 56
        val btnH = 20
        val btnSpacing = 6
        val totalBtnW = btnW * 3 + btnSpacing * 2
        val btnStartX = panelX + (panelW - totalBtnW) / 2
        val btnY = panelY + 102

        //? if >=1.20 {
        stopBtn = addRenderableWidget(Button.builder(Component.literal("\u25A0 Stop")) {
            sendControl(MusicControlPacket.Action.STOP)
        }.bounds(btnStartX, btnY, btnW, btnH).build())
        //?} else {
        /*stopBtn = addRenderableWidget(Button(btnStartX, btnY, btnW, btnH, Component.literal("\u25A0 Stop")) {
            sendControl(MusicControlPacket.Action.STOP)
        })*/
        //?}
        stopBtn!!.active = op

        //? if >=1.20 {
        pauseBtn = addRenderableWidget(Button.builder(Component.literal("\u23F8 Pause")) {
            val status = ClientMusicPlayer.getCurrentStatus()
            if (status != null && status.isPlaying) {
                sendControl(MusicControlPacket.Action.PAUSE)
            } else {
                sendControl(MusicControlPacket.Action.RESUME)
            }
        }.bounds(btnStartX + btnW + btnSpacing, btnY, btnW, btnH).build())
        //?} else {
        /*pauseBtn = addRenderableWidget(Button(btnStartX + btnW + btnSpacing, btnY, btnW, btnH, Component.literal("\u23F8 Pause")) {
            val status = ClientMusicPlayer.getCurrentStatus()
            if (status != null && status.isPlaying) {
                sendControl(MusicControlPacket.Action.PAUSE)
            } else {
                sendControl(MusicControlPacket.Action.RESUME)
            }
        })*/
        //?}
        pauseBtn!!.active = op

        //? if >=1.20 {
        skipBtn = addRenderableWidget(Button.builder(Component.literal("\u23ED Skip")) {
            sendControl(MusicControlPacket.Action.SKIP)
        }.bounds(btnStartX + (btnW + btnSpacing) * 2, btnY, btnW, btnH).build())
        //?} else {
        /*skipBtn = addRenderableWidget(Button(btnStartX + (btnW + btnSpacing) * 2, btnY, btnW, btnH, Component.literal("\u23ED Skip")) {
            sendControl(MusicControlPacket.Action.SKIP)
        })*/
        //?}
        skipBtn!!.active = op

        val delayY = panelY + 150
        val fieldW = 50
        val fieldH = 16
        val delayLabelW = font.width("Delay:") + 4
        val dashW = font.width(" - ") + 2
        val ticksLabelW = font.width("ticks")
        val totalDelayW = delayLabelW + fieldW + dashW + fieldW + 4 + ticksLabelW
        val delayStartX = panelX + (panelW - totalDelayW) / 2

        minDelayField = EditBox(font, delayStartX + delayLabelW, delayY, fieldW, fieldH, Component.literal("Min"))
        minDelayField!!.setMaxLength(7)
        minDelayField!!.setEditable(op)
        addRenderableWidget(minDelayField!!)

        maxDelayField = EditBox(font, delayStartX + delayLabelW + fieldW + dashW, delayY, fieldW, fieldH, Component.literal("Max"))
        maxDelayField!!.setMaxLength(7)
        maxDelayField!!.setEditable(op)
        addRenderableWidget(maxDelayField!!)

        val status = ClientMusicPlayer.getCurrentStatus()
        if (status != null && status.customMinDelay >= 0) {
            minDelayField!!.value = status.customMinDelay.toString()
            maxDelayField!!.value = status.customMaxDelay.toString()
        }

        val smallBtnW = 44
        val applyResetY = delayY + fieldH + 3
        val applyResetTotalW = smallBtnW * 2 + 6
        val applyResetX = panelX + (panelW - applyResetTotalW) / 2
        //? if >=1.20 {
        applyBtn = addRenderableWidget(Button.builder(Component.literal("Apply")) {
            applyDelay()
        }.bounds(applyResetX, applyResetY, smallBtnW, 16).build())
        //?} else {
        /*applyBtn = addRenderableWidget(Button(applyResetX, applyResetY, smallBtnW, 16, Component.literal("Apply")) {
            applyDelay()
        })*/
        //?}
        applyBtn!!.active = op

        //? if >=1.20 {
        resetBtn = addRenderableWidget(Button.builder(Component.literal("Reset")) {
            sendControl(MusicControlPacket.Action.SET_DELAY, trackId = "reset")
            minDelayField!!.value = ""
            maxDelayField!!.value = ""
        }.bounds(applyResetX + smallBtnW + 6, applyResetY, smallBtnW, 16).build())
        //?} else {
        /*resetBtn = addRenderableWidget(Button(applyResetX + smallBtnW + 6, applyResetY, smallBtnW, 16, Component.literal("Reset")) {
            sendControl(MusicControlPacket.Action.SET_DELAY, trackId = "reset")
            minDelayField!!.value = ""
            maxDelayField!!.value = ""
        })*/
        //?}
        resetBtn!!.active = op

        val navY = panelY + panelH - 26
        val navBtnW = 70
        //? if >=1.20 {
        addRenderableWidget(Button.builder(Component.literal("\u266B Playlist")) {
            Minecraft.getInstance().setScreen(PlaylistScreen())
        }.bounds(panelX + panelW / 2 - navBtnW - 3, navY, navBtnW, 18).build())
        //?} else {
        /*addRenderableWidget(Button(panelX + panelW / 2 - navBtnW - 3, navY, navBtnW, 18, Component.literal("\u266B Playlist")) {
            Minecraft.getInstance().setScreen(PlaylistScreen())
        })*/
        //?}

        //? if >=1.20 {
        addRenderableWidget(Button.builder(Component.literal("\u266A Tracks")) {
            Minecraft.getInstance().setScreen(TrackBrowserScreen())
        }.bounds(panelX + panelW / 2 + 3, navY, navBtnW, 18).build())
        //?} else {
        /*addRenderableWidget(Button(panelX + panelW / 2 + 3, navY, navBtnW, 18, Component.literal("\u266A Tracks")) {
            Minecraft.getInstance().setScreen(TrackBrowserScreen())
        })*/
        //?}

        val syncSize = 18
        //? if >=1.20 {
        syncAllBtn = addRenderableWidget(Button.builder(Component.literal("\u00A7l\u21C4")) {
            sendControl(MusicControlPacket.Action.FORCE_SYNC_ALL)
        }.bounds(panelX + 4, panelY + 4, syncSize, syncSize)
         .tooltip(Tooltip.create(Component.literal("Resync all clients")))
         .build())
        //?} else {
        /*syncAllBtn = addRenderableWidget(Button(panelX + 4, panelY + 4, syncSize, syncSize, Component.literal("\u00A7l\u21C4")) {
            sendControl(MusicControlPacket.Action.FORCE_SYNC_ALL)
        })*/
        //?}
        syncAllBtn!!.active = op

        val mc = Minecraft.getInstance()
        val playerDim = mc.player?.entityLevel()?.dimension()?.location()?.toString()
        val isInNonOverworld = playerDim != null && playerDim != "minecraft:overworld"
        if (isInNonOverworld) {
            dimLabel = getDimLabel(playerDim!!)
            val tooltipDim = getDimDisplayName(playerDim)
            val nSize = 18
            //? if >=1.20 {
            dimSyncBtn = addRenderableWidget(Button.builder(Component.literal(dimLabel)) {
                sendControl(MusicControlPacket.Action.TOGGLE_NETHER_SYNC)
            }.bounds(panelX + panelW - nSize - 4, panelY + 4, nSize, nSize)
             .tooltip(Tooltip.create(Component.literal("$tooltipDim — Sync overworld music")))
             .build())
            //?} else {
            /*dimSyncBtn = addRenderableWidget(Button(panelX + panelW - nSize - 4, panelY + 4, nSize, nSize, Component.literal(dimLabel)) {
                sendControl(MusicControlPacket.Action.TOGGLE_NETHER_SYNC)
            })*/
            //?}
        }
    }

    //? if >=1.20 {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val cx = panelX + panelW / 2

        graphics.drawCenteredString(font, "\u266B MuSync \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = ClientMusicPlayer.getCurrentStatus()
        val position = if (status != null && status.isPlaying && status.currentTrack != null) {
            ClientMusicPlayer.getCurrentPositionMs()
        } else {
            status?.currentPositionMs ?: 0
        }
        val duration = status?.durationMs ?: 0

        val trackText = if (status?.currentTrack != null) {
            if (status.resolvedName.isNotEmpty()) {
                formatOggName(status.resolvedName)
            } else {
                formatSoundEvent(status.currentTrack)
            }
        } else {
            "No track"
        }

        graphics.drawCenteredString(font, trackText, cx, panelY + 26, 0xFFFFFF)

        if (status?.currentTrack != null && status.resolvedName.isNotEmpty()) {
            graphics.drawCenteredString(font, formatSoundEvent(status.currentTrack), cx, panelY + 38, 0xFF777799.toInt())
        }

        val statusText = when {
            status == null -> "\u23F9 Stopped"
            status.isPlaying -> "\u25B6 Playing"
            status.currentTrack != null -> "\u23F8 Paused"
            status.waitingForNextTrack -> {
                val ticksLeft = (status.nextMusicDelayTicks - status.ticksSinceLastMusic).coerceAtLeast(0)
                "\u23F3 Next in ${formatTicksShort(ticksLeft)}"
            }
            else -> "\u23F9 Stopped"
        }
        val statusColor = when {
            status != null && status.isPlaying -> 0xFF55FF55.toInt()
            status != null && status.waitingForNextTrack -> 0xFFFFAA00.toInt()
            else -> 0xFFAAAAAA.toInt()
        }
        graphics.drawCenteredString(font, statusText, cx, panelY + 52, statusColor)

        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        val filledW = (barW * progress).toInt()

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222244.toInt())
        if (filledW > 0) {
            graphics.fill(barX, barY, barX + filledW, barY + barH, 0xFF00CC66.toInt())
            graphics.fill(barX, barY, barX + filledW, barY + barH / 2, 0xFF00EE88.toInt())
        }
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY, 0xFF333355.toInt())
        graphics.fill(barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0xFF333355.toInt())
        graphics.fill(barX - 1, barY, barX, barY + barH, 0xFF333355.toInt())
        graphics.fill(barX + barW, barY, barX + barW + 1, barY + barH, 0xFF333355.toInt())

        if (filledW > 0 && duration > 0) {
            val headX = barX + filledW
            graphics.fill(headX - 1, barY - 1, headX + 1, barY + barH + 1, 0xFFFFFFFF.toInt())
        }

        val posStr = formatTime(position)
        val durStr = if (duration > 0) formatTime(duration) else "--:--"
        graphics.drawCenteredString(font, "$posStr / $durStr", cx, barY + barH + 3, 0xFF999999.toInt())

        if (status != null && status.isPlaying) {
            pauseBtn?.message = Component.literal("\u23F8 Pause")
        } else {
            pauseBtn?.message = Component.literal("\u25B6 Play")
        }

        if (dimSyncBtn != null) {
            val syncing = status?.syncOverworld == true
            dimSyncBtn!!.message = Component.literal(if (syncing) "\u00A7aO" else dimLabel)
        }

        val modeText = when (status?.mode) {
            MusicStatusPacket.PlayMode.AUTONOMOUS -> "Auto"
            MusicStatusPacket.PlayMode.PLAYLIST -> "Playlist"
            MusicStatusPacket.PlayMode.SINGLE_TRACK -> "Single"
            else -> "---"
        }
        graphics.drawCenteredString(font, "Mode: $modeText", cx, panelY + 128, 0xFF666688.toInt())

        val delayRenderY = panelY + 152
        val delayLabelWR = font.width("Delay:") + 4
        val dashWR = font.width(" - ") + 2
        val fieldWR = 50
        val ticksLabelWR = font.width("ticks")
        val totalDelayWR = delayLabelWR + fieldWR + dashWR + fieldWR + 4 + ticksLabelWR
        val delayRenderX = panelX + (panelW - totalDelayWR) / 2

        graphics.drawString(font, "Delay:", delayRenderX, delayRenderY, 0xFF888888.toInt())
        graphics.drawString(font, "-", delayRenderX + delayLabelWR + fieldWR + (dashWR - font.width("-")) / 2, delayRenderY, 0xFF888888.toInt())
        graphics.drawString(font, "ticks", delayRenderX + delayLabelWR + fieldWR + dashWR + fieldWR + 4, delayRenderY, 0xFF666666.toInt())

        val activeDelayY = panelY + 190
        if (status != null && status.customMinDelay >= 0 && status.customMaxDelay >= 0) {
            val dText = "Active: ${status.customMinDelay}-${status.customMaxDelay} ticks (${formatTicksShort(status.customMinDelay)}-${formatTicksShort(status.customMaxDelay)})"
            graphics.drawCenteredString(font, dText, cx, activeDelayY, 0xFF55AA77.toInt())
        } else {
            graphics.drawCenteredString(font, "Active: Vanilla defaults (10m 0s - 20m 0s)", cx, activeDelayY, 0xFF666666.toInt())
        }

        if (!isOp) {
            graphics.drawCenteredString(font, "\u26A0 View only (OP required)", cx, panelY + panelH - 42, 0xFFFF5555.toInt())
        }

        val queueSize = status?.queue?.size ?: 0
        if (queueSize > 0) {
            graphics.drawCenteredString(font, "Queue: $queueSize track${if (queueSize > 1) "s" else ""}", cx, panelY + 138, 0xFF666688.toInt())
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

        GuiComponent.drawCenteredString(poseStack, font, "\u266B MuSync \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = ClientMusicPlayer.getCurrentStatus()
        val position = if (status != null && status.isPlaying && status.currentTrack != null) {
            ClientMusicPlayer.getCurrentPositionMs()
        } else {
            status?.currentPositionMs ?: 0
        }
        val duration = status?.durationMs ?: 0

        val trackText = if (status?.currentTrack != null) {
            if (status.resolvedName.isNotEmpty()) {
                formatOggName(status.resolvedName)
            } else {
                formatSoundEvent(status.currentTrack)
            }
        } else {
            "No track"
        }

        GuiComponent.drawCenteredString(poseStack, font, trackText, cx, panelY + 26, 0xFFFFFF)

        if (status?.currentTrack != null && status.resolvedName.isNotEmpty()) {
            GuiComponent.drawCenteredString(poseStack, font, formatSoundEvent(status.currentTrack), cx, panelY + 38, 0xFF777799.toInt())
        }

        val statusText = when {
            status == null -> "\u23F9 Stopped"
            status.isPlaying -> "\u25B6 Playing"
            status.currentTrack != null -> "\u23F8 Paused"
            status.waitingForNextTrack -> {
                val ticksLeft = (status.nextMusicDelayTicks - status.ticksSinceLastMusic).coerceAtLeast(0)
                "\u23F3 Next in ${formatTicksShort(ticksLeft)}"
            }
            else -> "\u23F9 Stopped"
        }
        val statusColor = when {
            status != null && status.isPlaying -> 0xFF55FF55.toInt()
            status != null && status.waitingForNextTrack -> 0xFFFFAA00.toInt()
            else -> 0xFFAAAAAA.toInt()
        }
        GuiComponent.drawCenteredString(poseStack, font, statusText, cx, panelY + 52, statusColor)

        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        val filledW = (barW * progress).toInt()

        GuiComponent.fill(poseStack, barX, barY, barX + barW, barY + barH, 0xFF222244.toInt())
        if (filledW > 0) {
            GuiComponent.fill(poseStack, barX, barY, barX + filledW, barY + barH, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, barX, barY, barX + filledW, barY + barH / 2, 0xFF00EE88.toInt())
        }
        GuiComponent.fill(poseStack, barX - 1, barY - 1, barX + barW + 1, barY, 0xFF333355.toInt())
        GuiComponent.fill(poseStack, barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0xFF333355.toInt())
        GuiComponent.fill(poseStack, barX - 1, barY, barX, barY + barH, 0xFF333355.toInt())
        GuiComponent.fill(poseStack, barX + barW, barY, barX + barW + 1, barY + barH, 0xFF333355.toInt())

        if (filledW > 0 && duration > 0) {
            val headX = barX + filledW
            GuiComponent.fill(poseStack, headX - 1, barY - 1, headX + 1, barY + barH + 1, 0xFFFFFFFF.toInt())
        }

        val posStr = formatTime(position)
        val durStr = if (duration > 0) formatTime(duration) else "--:--"
        GuiComponent.drawCenteredString(poseStack, font, "$posStr / $durStr", cx, barY + barH + 3, 0xFF999999.toInt())

        if (status != null && status.isPlaying) {
            pauseBtn?.message = Component.literal("\u23F8 Pause")
        } else {
            pauseBtn?.message = Component.literal("\u25B6 Play")
        }

        if (dimSyncBtn != null) {
            val syncing = status?.syncOverworld == true
            dimSyncBtn!!.message = Component.literal(if (syncing) "\u00A7aO" else dimLabel)
        }

        val modeText = when (status?.mode) {
            MusicStatusPacket.PlayMode.AUTONOMOUS -> "Auto"
            MusicStatusPacket.PlayMode.PLAYLIST -> "Playlist"
            MusicStatusPacket.PlayMode.SINGLE_TRACK -> "Single"
            else -> "---"
        }
        GuiComponent.drawCenteredString(poseStack, font, "Mode: $modeText", cx, panelY + 128, 0xFF666688.toInt())

        val delayRenderY = panelY + 152
        val delayLabelWR = font.width("Delay:") + 4
        val dashWR = font.width(" - ") + 2
        val fieldWR = 50
        val ticksLabelWR = font.width("ticks")
        val totalDelayWR = delayLabelWR + fieldWR + dashWR + fieldWR + 4 + ticksLabelWR
        val delayRenderX = panelX + (panelW - totalDelayWR) / 2

        GuiComponent.drawString(poseStack, font, "Delay:", delayRenderX, delayRenderY, 0xFF888888.toInt())
        GuiComponent.drawString(poseStack, font, "-", delayRenderX + delayLabelWR + fieldWR + (dashWR - font.width("-")) / 2, delayRenderY, 0xFF888888.toInt())
        GuiComponent.drawString(poseStack, font, "ticks", delayRenderX + delayLabelWR + fieldWR + dashWR + fieldWR + 4, delayRenderY, 0xFF666666.toInt())

        val activeDelayY = panelY + 190
        if (status != null && status.customMinDelay >= 0 && status.customMaxDelay >= 0) {
            val dText = "Active: ${status.customMinDelay}-${status.customMaxDelay} ticks (${formatTicksShort(status.customMinDelay)}-${formatTicksShort(status.customMaxDelay)})"
            GuiComponent.drawCenteredString(poseStack, font, dText, cx, activeDelayY, 0xFF55AA77.toInt())
        } else {
            GuiComponent.drawCenteredString(poseStack, font, "Active: Vanilla defaults (10m 0s - 20m 0s)", cx, activeDelayY, 0xFF666666.toInt())
        }

        if (!isOp) {
            GuiComponent.drawCenteredString(poseStack, font, "\u26A0 View only (OP required)", cx, panelY + panelH - 42, 0xFFFF5555.toInt())
        }

        val queueSize = status?.queue?.size ?: 0
        if (queueSize > 0) {
            GuiComponent.drawCenteredString(poseStack, font, "Queue: $queueSize track${if (queueSize > 1) "s" else ""}", cx, panelY + 138, 0xFF666688.toInt())
        }

        super.render(poseStack, mouseX, mouseY, partialTick)
    }*/
    //?}

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isOp && button == 0 && mouseX >= barX && mouseX <= barX + barW && mouseY >= barY - 2 && mouseY <= barY + barH + 2) {
            val now = System.currentTimeMillis()
            if (now - lastSeekTimeMs < SEEK_COOLDOWN_MS) return true
            val status = ClientMusicPlayer.getCurrentStatus()
            if (status?.currentTrack != null && status.durationMs > 0 && (status.isPlaying || status.currentPositionMs > 0)) {
                val clickProgress = ((mouseX - barX) / barW).coerceIn(0.0, 1.0)
                val seekMs = (clickProgress * status.durationMs).toLong()
                val packet = MusicControlPacket(
                    action = MusicControlPacket.Action.SEEK,
                    trackId = null,
                    queuePosition = seekMs.toInt()
                )
                PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), packet)
                lastSeekTimeMs = now
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dev.mcrib884.musync.MuSyncForge.MUSIC_GUI_KEY.matches(keyCode, scanCode)) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun applyDelay() {
        val minStr = minDelayField?.value ?: return
        val maxStr = maxDelayField?.value ?: return
        val min = minStr.toIntOrNull()
        val max = maxStr.toIntOrNull()
        if (min == null || max == null || min < 0 || max < min) return
        sendControl(MusicControlPacket.Action.SET_DELAY, trackId = "$min:$max")
    }

    private fun sendControl(action: MusicControlPacket.Action, trackId: String? = null) {
        val packet = MusicControlPacket(action = action, trackId = trackId, queuePosition = null)
        PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), packet)
    }

    private fun getDimLabel(dimId: String): String = when (dimId) {
        "minecraft:the_nether" -> "N"
        "minecraft:the_end" -> "E"
        else -> dimId.substringAfterLast(":").firstOrNull()?.uppercase() ?: "?"
    }

    private fun getDimDisplayName(dimId: String): String = when (dimId) {
        "minecraft:the_nether" -> "Nether"
        "minecraft:the_end" -> "End"
        else -> dimId.substringAfterLast(":").replace("_", " ").replaceFirstChar { it.uppercase() }
    }

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
        "minecraft:music.overworld.meadow" to "Meadow",
        "minecraft:music.overworld.grove" to "Grove",
        "minecraft:music.overworld.forest" to "Forest",
        "minecraft:music.overworld.desert" to "Desert",
        "minecraft:music.overworld.badlands" to "Badlands",
        "minecraft:music.overworld.jungle" to "Jungle",
        "minecraft:music.overworld.bamboo_jungle" to "Bamboo Jungle",
        "minecraft:music.overworld.cherry_grove" to "Cherry Grove",
        "minecraft:music.overworld.deep_dark" to "Deep Dark",
        "minecraft:music.overworld.dripstone_caves" to "Dripstone Caves",
        "minecraft:music.overworld.lush_caves" to "Lush Caves",
        "minecraft:music.overworld.swamp" to "Swamp",
        "minecraft:music.overworld.old_growth_taiga" to "Old Growth Taiga",
        "minecraft:music.overworld.snowy_slopes" to "Snowy Slopes",
        "minecraft:music.overworld.jagged_peaks" to "Jagged Peaks",
        "minecraft:music.overworld.frozen_peaks" to "Frozen Peaks",
        "minecraft:music.overworld.stony_peaks" to "Stony Peaks",
    )

    private fun formatOggName(path: String): String {
        val cleanPath = if (path.contains(":")) path.substringAfter(":") else path
        return OGG_NAMES[cleanPath] ?: cleanPath.substringAfterLast("/").replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private fun formatSoundEvent(id: String): String {
        return POOL_NAMES[id] ?: id.substringAfterLast(":")
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun formatTicksShort(ticks: Int): String {
        val totalSec = ticks / 20
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
