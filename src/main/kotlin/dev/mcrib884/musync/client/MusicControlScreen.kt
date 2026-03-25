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
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import dev.mcrib884.musync.entityLevel
//? if >=1.21.11 {
/*import dev.mcrib884.musync.location*/
//?}
//? if >=1.20 {
import dev.mcrib884.musync.renderTooltipCompat
//?}

class MusicControlScreen : Screen(Component.literal("MuSync")) {

    private val panelW = 260
    private val panelH = 240

    private var lastSeekTimeMs: Long = 0
    private val SEEK_COOLDOWN_MS = 500L
    private var draggingSeekBar = false
    private var dragSeekProgress: Float = 0f
    private var seekHoldUntilMs: Long = 0
    private var seekHoldProgress: Float = 0f
    private var panelX = 0
    private var panelY = 0
    private val barH = 8
    private val barW = 220
    private var barX = 0
    private var barY = 0
    private val volumeBarW = 6
    private val volumeBarH = 28
    private var volumeBarX = 0
    private var volumeBarY = 0
    private var draggingVolume = false
    private var previousMusicVolume = 1f

    private data class BtnBounds(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, var active: Boolean = true)

    private var prevBounds: BtnBounds? = null
    private var stopBounds: BtnBounds? = null
    private var pauseBounds: BtnBounds? = null
    private var skipBounds: BtnBounds? = null
    private var pauseIsPlaying = false
    private var applyBounds: BtnBounds? = null
    private var resetBounds: BtnBounds? = null
    private var playlistNavBounds: BtnBounds? = null
    private var tracksNavBounds: BtnBounds? = null
    private var cacheBtnBounds: BtnBounds? = null
    private var repeatBtnBounds: BtnBounds? = null
    private var minDelayField: EditBox? = null
    private var maxDelayField: EditBox? = null
    private var minFieldX = 0
    private var maxFieldX = 0
    private var fieldBoxY = 0
    private var syncBtnX = 0
    private var syncBtnY = 0
    private var hotloadBtnX = 0
    private var hotloadBtnY = 0
    private var dimBtnX = 0
    private var dimBtnY = 0
    private val topBtnSize = 14
    private val topBtnGap = 4
    private var viewedDimId: String = "minecraft:overworld"
    private var dimFlyoutOpen: Boolean = false
    private var displayedDuration: Long = 0
    private var displayedHasTrack: Boolean = false

    private val isOp: Boolean
        get() = ClientOnlyController.isActive || dev.mcrib884.musync.isOp(Minecraft.getInstance().player)

    private fun effectiveStatus(): MusicStatusPacket? {
        return if (ClientOnlyController.isActive) ClientOnlyController.getStatus() else ClientMusicPlayer.getCurrentStatus()
    }

    private fun effectiveRepeatMode(): MusicStatusPacket.RepeatMode {
        return effectiveStatus()?.repeatMode ?: MusicStatusPacket.RepeatMode.OFF
    }

    private fun repeatModeLabel(mode: MusicStatusPacket.RepeatMode): String {
        return when (mode) {
            MusicStatusPacket.RepeatMode.OFF -> "Repeat: Off"
            MusicStatusPacket.RepeatMode.REPEAT_TRACK -> "Repeat: Track"
            MusicStatusPacket.RepeatMode.REPEAT_PLAYLIST -> "Repeat: Playlist"
            MusicStatusPacket.RepeatMode.SHUFFLE -> "Shuffle"
            MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT -> "Shuffle + Repeat"
        }
    }

    override fun init() {
        super.init()
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
        barX = panelX + (panelW - barW) / 2
        barY = panelY + 82

        val op = isOp
        val btnW = 42
        val btnH = 16
        val btnSpacing = 4
        val totalBtnW = btnW * 4 + btnSpacing * 3
        val btnStartX = panelX + (panelW - totalBtnW) / 2
        val btnY = panelY + 102

        prevBounds = BtnBounds(btnStartX, btnY, btnW, btnH, "\u23EE Prev", op)
        stopBounds = BtnBounds(btnStartX + (btnW + btnSpacing), btnY, btnW, btnH, "\u25A0 Stop", op)
        pauseBounds = BtnBounds(btnStartX + (btnW + btnSpacing) * 2, btnY, btnW, btnH, "\u23F8 Pause", op)
        skipBounds = BtnBounds(btnStartX + (btnW + btnSpacing) * 3, btnY, btnW, btnH, "\u23ED Skip", op)
        repeatBtnBounds = BtnBounds(panelX + panelW - 22, btnY, 16, 16, "")

        val delayY = panelY + 150
        val delayBoxW = 50
        val delayBoxH = 16
        val labelGap = 4
        val dashGap = 4
        val lw = font.width("Delay:") + labelGap
        val dw = dashGap + font.width("-") + dashGap
        val tw = labelGap + font.width("ticks")
        val totalDelayW = lw + delayBoxW + dw + delayBoxW + tw
        val delayStartX = panelX + (panelW - totalDelayW) / 2

        minFieldX = delayStartX + lw
        maxFieldX = delayStartX + lw + delayBoxW + dw
        fieldBoxY = delayY

        minDelayField = EditBox(font, minFieldX + 4, delayY + 2, delayBoxW - 8, delayBoxH - 4, Component.literal("Min"))
        minDelayField!!.setMaxLength(7)
        minDelayField!!.setEditable(op)
        minDelayField!!.setBordered(false)
        minDelayField!!.setTextColor(0xFFFFFFFF.toInt())
        minDelayField!!.setTextColorUneditable(0xFF667766.toInt())
        addRenderableWidget(minDelayField!!)

        maxDelayField = EditBox(font, maxFieldX + 4, delayY + 2, delayBoxW - 8, delayBoxH - 4, Component.literal("Max"))
        maxDelayField!!.setMaxLength(7)
        maxDelayField!!.setEditable(op)
        maxDelayField!!.setBordered(false)
        maxDelayField!!.setTextColor(0xFFFFFFFF.toInt())
        maxDelayField!!.setTextColorUneditable(0xFF667766.toInt())
        addRenderableWidget(maxDelayField!!)

        val status = effectiveStatus()
        syncDelayFields(status)

        val smallBtnW = 44
        val applyResetY = delayY + delayBoxH + 4
        val applyResetTotalW = smallBtnW * 2 + 6
        val applyResetX = panelX + (panelW - applyResetTotalW) / 2
        applyBounds = BtnBounds(applyResetX, applyResetY, smallBtnW, 16, "Apply", op)
        resetBounds = BtnBounds(applyResetX + smallBtnW + 6, applyResetY, smallBtnW, 16, "Reset", op)

        val navY = panelY + panelH - 26
        val navBtnW = 70
        playlistNavBounds = BtnBounds(panelX + 4, navY, navBtnW, 16, "\u266B Playlist")
        tracksNavBounds = BtnBounds(panelX + 78, navY, navBtnW, 16, "\u266A Tracks")

        val cacheBtnW = 76
        cacheBtnBounds = BtnBounds(panelX + panelW - cacheBtnW - 6, navY, cacheBtnW, 16, "Cache")

        val mc = Minecraft.getInstance()
        val playerDim = mc.player?.entityLevel()?.dimension()?.location()?.toString()
        viewedDimId = playerDim ?: "minecraft:overworld"
        syncBtnX = panelX + 4
        syncBtnY = panelY + 4
        hotloadBtnX = syncBtnX
        hotloadBtnY = syncBtnY + topBtnSize + topBtnGap
        dimBtnX = panelX + panelW - topBtnSize - 4
        dimBtnY = panelY + 4
        volumeBarX = dimBtnX + (topBtnSize - volumeBarW) / 2
        volumeBarY = dimBtnY + topBtnSize + topBtnGap + 1
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

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val syncingNow = effectiveStatus()?.syncOverworld == true
        val dimBtnLabel = if (syncingNow) "\u00A7aO" else getDimLabel(viewedDimId)
        val syncBtnHovered = mouseX in syncBtnX until syncBtnX + topBtnSize && mouseY in syncBtnY until syncBtnY + topBtnSize
        val hotloadBtnHovered = mouseX in hotloadBtnX until hotloadBtnX + topBtnSize && mouseY in hotloadBtnY until hotloadBtnY + topBtnSize
        val dimBtnHovered = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
        drawCustomTopBtn(graphics, syncBtnX, syncBtnY, "\u21C4", syncBtnHovered)
        drawHotloadTopBtn(graphics, hotloadBtnX, hotloadBtnY, hotloadBtnHovered)
        drawCustomTopBtn(graphics, dimBtnX, dimBtnY, dimBtnLabel, dimBtnHovered)

        val cx = panelX + panelW / 2

        graphics.drawCenteredString(font, "\u266B MuSync \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = effectiveStatus()
        syncDelayFields(status)
        val ownPosition = if (status != null && status.isPlaying && status.currentTrack != null) {
            ClientMusicPlayer.getCurrentPositionMs()
        } else {
            status?.currentPositionMs ?: 0
        }
        val playerDimId = Minecraft.getInstance().player?.entityLevel()?.dimension()?.location()?.toString() ?: "minecraft:overworld"
        val localLoading = ClientMusicPlayer.isLoading() && viewedDimId == playerDimId
        val localLoadingTrack = if (localLoading) ClientMusicPlayer.getLoadingTrack() else null
        val usePrimaryDisplay = status?.priorityActive == true || viewedDimId == "minecraft:overworld"
        val displayDimSt = if (!usePrimaryDisplay) status?.activeDimensions?.find { it.id == viewedDimId } else null
        val displayTrack = localLoadingTrack ?: displayDimSt?.currentTrack ?: status?.currentTrack
        val displayResolved = displayDimSt?.resolvedName ?: status?.resolvedName ?: ""
        val displayIsPlaying = if (localLoading) false else (displayDimSt?.isPlaying ?: (status?.isPlaying == true))
        val displayDuration: Long = displayDimSt?.durationMs ?: (status?.durationMs ?: 0)
        val rawDisplayPosition: Long = when {
            viewedDimId == playerDimId -> ownPosition
            displayDimSt != null -> displayDimSt.currentPositionMs
            else -> status?.currentPositionMs ?: 0
        }
        val displayPosition: Long = when {
            viewedDimId == playerDimId -> rawDisplayPosition
            displayDimSt != null && displayDimSt.isPlaying ->
                (rawDisplayPosition + ClientMusicPlayer.getStatusAge()).coerceAtMost(displayDimSt.durationMs.coerceAtLeast(rawDisplayPosition))
            usePrimaryDisplay && status?.isPlaying == true ->
                (rawDisplayPosition + ClientMusicPlayer.getStatusAge()).coerceAtMost(displayDuration.coerceAtLeast(rawDisplayPosition))
            else -> rawDisplayPosition
        }
        val displayWaiting = displayDimSt?.waitingForNextTrack ?: (status?.waitingForNextTrack == true)
        val displayTicksSince = displayDimSt?.ticksSinceLastMusic ?: (status?.ticksSinceLastMusic ?: 0)
        val displayDelayTicks = displayDimSt?.nextMusicDelayTicks ?: (status?.nextMusicDelayTicks ?: 0)
        displayedDuration = displayDuration
        displayedHasTrack = displayTrack != null
        pauseBounds?.active = isOp && !localLoading && displayTrack != null
        skipBounds?.active = isOp && !localLoading
        stopBounds?.active = isOp && (localLoading || displayTrack != null || displayIsPlaying || displayWaiting)

        val trackText = if (displayTrack != null) {
            if (displayResolved.isNotEmpty()) {
                formatOggName(displayResolved)
            } else {
                formatSoundEvent(displayTrack)
            }
        } else {
            "No track"
        }

        graphics.drawCenteredString(font, trackText, cx, panelY + 26, 0xFFFFFFFF.toInt())

        if (displayTrack != null && displayResolved.isNotEmpty()) {
            graphics.drawCenteredString(font, formatSoundEvent(displayTrack), cx, panelY + 38, 0xFF777799.toInt())
        }

        val statusText = when {
            localLoading -> "\u23F3 Loading"
            displayIsPlaying -> "\u25B6 Playing"
            displayTrack != null -> "\u23F8 Paused"
            displayWaiting -> {
                val ticksLeft = (displayDelayTicks - displayTicksSince).coerceAtLeast(0)
                "\u23F3 Next in ${formatTicksShort(ticksLeft)}"
            }
            else -> "\u23F9 Stopped"
        }
        val statusColor = when {
            localLoading -> 0xFF66CCFF.toInt()
            displayIsPlaying -> 0xFF55FF55.toInt()
            displayWaiting -> 0xFFFFAA00.toInt()
            else -> 0xFFAAAAAA.toInt()
        }
        graphics.drawCenteredString(font, statusText, cx, panelY + 52, statusColor)
        drawVolumeBar(graphics, volumeBarX, volumeBarY, volumeBarW, volumeBarH)

        val actualProgress = if (displayDuration > 0) (displayPosition.toFloat() / displayDuration.toFloat()).coerceIn(0f, 1f) else 0f
        if (!draggingSeekBar && seekHoldUntilMs > 0 && kotlin.math.abs(actualProgress - seekHoldProgress) < 0.05f) {
            seekHoldUntilMs = 0
        }
        val holdingSeek = !draggingSeekBar && System.currentTimeMillis() < seekHoldUntilMs
        val progress = if (draggingSeekBar) dragSeekProgress else if (holdingSeek) seekHoldProgress else actualProgress
        val filledW = (barW * progress).toInt()

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222244.toInt())
        if (filledW > 0) {
            val barColor = if (draggingSeekBar) 0xFF33AAFF.toInt() else 0xFF00CC66.toInt()
            val barHighlight = if (draggingSeekBar) 0xFF55CCFF.toInt() else 0xFF00EE88.toInt()
            graphics.fill(barX, barY, barX + filledW, barY + barH, barColor)
            graphics.fill(barX, barY, barX + filledW, barY + barH / 2, barHighlight)
        }
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY, 0xFF333355.toInt())
        graphics.fill(barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0xFF333355.toInt())
        graphics.fill(barX - 1, barY, barX, barY + barH, 0xFF333355.toInt())
        graphics.fill(barX + barW, barY, barX + barW + 1, barY + barH, 0xFF333355.toInt())

        if (filledW > 0 && displayDuration > 0) {
            val headX = barX + filledW
            graphics.fill(headX - 1, barY - 2, headX + 1, barY + barH + 2, 0xFFFFFFFF.toInt())
        }

        val shownPosition = if ((draggingSeekBar || holdingSeek) && displayDuration > 0) ((if (draggingSeekBar) dragSeekProgress else seekHoldProgress) * displayDuration).toLong() else displayPosition
        val timeText = if (displayTrack != null) {
            val posStr = formatTime(shownPosition)
            val durStr = if (displayDuration > 0) formatTime(displayDuration) else "--:--"
            "$posStr / $durStr"
        } else {
            "0:00 / 0:00"
        }
        val timeColor = if (draggingSeekBar || holdingSeek) 0xFF55CCFF.toInt() else 0xFF999999.toInt()
        graphics.drawCenteredString(font, timeText, cx, barY + barH + 3, timeColor)

        pauseIsPlaying = displayIsPlaying
        val pauseLabel = if (localLoading) "\u23F3 Wait" else if (pauseIsPlaying) "\u23F8 Pause" else "\u25B6 Play"
        if (ClientOnlyController.isActive) {
            prevBounds?.active = isOp && ClientOnlyController.hasHistory
        }
        prevBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        stopBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        pauseBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, pauseLabel, hov, b.active)
        }
        skipBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        applyBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        resetBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        playlistNavBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        tracksNavBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        cacheBtnBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            val bg = if (hov) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
            graphics.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg)
            graphics.fill(b.x, b.y, b.x + b.w, b.y + 1, 0xFF00CC66.toInt())
            graphics.fill(b.x, b.y, b.x + 1, b.y + b.h, 0xFF00CC66.toInt())
            graphics.fill(b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            graphics.fill(b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            val cacheEnabled = ClientTrackManager.cacheEnabled
            val cacheLabel = if (cacheEnabled) "Cache ON" else "Cache OFF"
            val cacheLabelColor = if (cacheEnabled) 0xFF00FF88.toInt() else 0xFFFF6655.toInt()
            val indicatorX = b.x + 6
            val indicatorY = b.y + (b.h - 6) / 2
            drawCacheIndicator(graphics, indicatorX, indicatorY, 6, cacheEnabled, cacheLabelColor)
            graphics.drawString(font, cacheLabel, indicatorX + 10, b.y + (b.h - 8) / 2, cacheLabelColor)
        }

        repeatBtnBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawRepeatBtn(graphics, b.x, b.y, b.w, b.h, hov)
        }

        val modeText = when (status?.mode) {
            MusicStatusPacket.PlayMode.AUTONOMOUS -> "Auto"
            MusicStatusPacket.PlayMode.PLAYLIST -> "Playlist"
            MusicStatusPacket.PlayMode.SINGLE_TRACK -> "Single"
            else -> "---"
        }
        graphics.drawCenteredString(font, "Mode: $modeText", cx, panelY + 128, 0xFF666688.toInt())

        val delayTextY = fieldBoxY + 4
        val dashX = minFieldX + 50 + 4
        val minFocused = minDelayField?.isFocused == true
        val maxFocused = maxDelayField?.isFocused == true
        for ((fx, focused) in listOf(Pair(minFieldX, minFocused), Pair(maxFieldX, maxFocused))) {
            val bc = if (focused) 0xFF33EE88.toInt() else 0xFF00CC66.toInt()
            graphics.fill(fx, fieldBoxY, fx + 50, fieldBoxY + 16, 0xFF1C1C2A.toInt())
            graphics.fill(fx, fieldBoxY, fx + 50, fieldBoxY + 1, bc)
            graphics.fill(fx, fieldBoxY, fx + 1, fieldBoxY + 16, bc)
            graphics.fill(fx + 49, fieldBoxY, fx + 50, fieldBoxY + 16, bc)
            graphics.fill(fx, fieldBoxY + 15, fx + 50, fieldBoxY + 16, bc)
        }
        graphics.drawString(font, "Delay:", minFieldX - font.width("Delay:") - 4, delayTextY, 0xFF888888.toInt())
        graphics.drawString(font, "-", dashX, delayTextY, 0xFF888888.toInt())
        graphics.drawString(font, "ticks", maxFieldX + 50 + 4, delayTextY, 0xFF666666.toInt())

        val activeDelayY = panelY + 190
        val dimLabel = getDimDisplayName(viewedDimId)
        if (status != null && status.customMinDelay >= 0 && status.customMaxDelay >= 0) {
            val dText = "$dimLabel: ${status.customMinDelay}-${status.customMaxDelay} ticks (${formatTicksShort(status.customMinDelay)}-${formatTicksShort(status.customMaxDelay)})"
            graphics.drawCenteredString(font, dText, cx, activeDelayY, 0xFF55AA77.toInt())
        } else {
            graphics.drawCenteredString(font, "$dimLabel: Vanilla/mod defaults", cx, activeDelayY, 0xFF666666.toInt())
        }

        if (!isOp) {
            graphics.drawCenteredString(font, "\u26A0 View only (OP required)", cx, panelY + panelH - 42, 0xFFFF5555.toInt())
        }

        val queueSize = status?.queue?.size ?: 0
        if (queueSize > 0) {
            graphics.drawCenteredString(font, "Queue: $queueSize track${if (queueSize > 1) "s" else ""}", cx, panelY + 138, 0xFF666688.toInt())
        }

        super.render(graphics, mouseX, mouseY, partialTick)

        renderDimensionOverlay(graphics, mouseX, mouseY, status)
        renderTopButtonTooltips(graphics, mouseX, mouseY, status)
        renderVolumeTooltip(graphics, mouseX, mouseY)
    }
    //?} else {
    /*override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(poseStack)

        GuiComponent.fill(poseStack, panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val syncHov1919 = mouseX in syncBtnX until syncBtnX + topBtnSize && mouseY in syncBtnY until syncBtnY + topBtnSize
        val hotloadHov1919 = mouseX in hotloadBtnX until hotloadBtnX + topBtnSize && mouseY in hotloadBtnY until hotloadBtnY + topBtnSize
        val dimHov1919 = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
        val syncing1919 = effectiveStatus()?.syncOverworld == true
        val dimBtnLbl1919 = if (syncing1919) "\u00A7aO" else getDimLabel(viewedDimId)
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY, syncBtnX + topBtnSize, syncBtnY + topBtnSize, if (syncHov1919) 0xFF334433.toInt() else 0xFF1C1C2A.toInt())
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY, syncBtnX + topBtnSize, syncBtnY + 1, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY, syncBtnX + 1, syncBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, syncBtnX + topBtnSize - 1, syncBtnY, syncBtnX + topBtnSize, syncBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY + topBtnSize - 1, syncBtnX + topBtnSize, syncBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.drawString(poseStack, font, "\u21C4", syncBtnX + (topBtnSize - font.width("\u21C4")) / 2, syncBtnY + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())
        drawHotloadTopBtn1919(poseStack, hotloadBtnX, hotloadBtnY, hotloadHov1919)
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY, dimBtnX + topBtnSize, dimBtnY + topBtnSize, if (dimHov1919) 0xFF334433.toInt() else 0xFF1C1C2A.toInt())
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY, dimBtnX + topBtnSize, dimBtnY + 1, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY, dimBtnX + 1, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, dimBtnX + topBtnSize - 1, dimBtnY, dimBtnX + topBtnSize, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY + topBtnSize - 1, dimBtnX + topBtnSize, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.drawString(poseStack, font, dimBtnLbl1919, dimBtnX + (topBtnSize - font.width(dimBtnLbl1919)) / 2, dimBtnY + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())

        val cx = panelX + panelW / 2

        GuiComponent.drawCenteredString(poseStack, font, "\u266B MuSync \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = effectiveStatus()
        syncDelayFields(status)
        val ownPosition = if (status != null && status.isPlaying && status.currentTrack != null) {
            ClientMusicPlayer.getCurrentPositionMs()
        } else {
            status?.currentPositionMs ?: 0
        }
        val playerDimId = Minecraft.getInstance().player?.entityLevel()?.dimension()?.location()?.toString() ?: "minecraft:overworld"
        val localLoading = ClientMusicPlayer.isLoading() && viewedDimId == playerDimId
        val localLoadingTrack = if (localLoading) ClientMusicPlayer.getLoadingTrack() else null
        val usePrimaryDisplay = status?.priorityActive == true || viewedDimId == "minecraft:overworld"
        val displayDimSt = if (!usePrimaryDisplay) status?.activeDimensions?.find { it.id == viewedDimId } else null
        val displayTrack = localLoadingTrack ?: displayDimSt?.currentTrack ?: status?.currentTrack
        val displayResolved = displayDimSt?.resolvedName ?: status?.resolvedName ?: ""
        val displayIsPlaying = if (localLoading) false else (displayDimSt?.isPlaying ?: (status?.isPlaying == true))
        val displayDuration: Long = displayDimSt?.durationMs ?: (status?.durationMs ?: 0)
        val rawDisplayPosition: Long = when {
            viewedDimId == playerDimId -> ownPosition
            displayDimSt != null -> displayDimSt.currentPositionMs
            else -> status?.currentPositionMs ?: 0
        }
        val displayPosition: Long = when {
            viewedDimId == playerDimId -> rawDisplayPosition
            displayDimSt != null && displayDimSt.isPlaying ->
                (rawDisplayPosition + ClientMusicPlayer.getStatusAge()).coerceAtMost(displayDimSt.durationMs.coerceAtLeast(rawDisplayPosition))
            usePrimaryDisplay && status?.isPlaying == true ->
                (rawDisplayPosition + ClientMusicPlayer.getStatusAge()).coerceAtMost(displayDuration.coerceAtLeast(rawDisplayPosition))
            else -> rawDisplayPosition
        }
        val displayWaiting = displayDimSt?.waitingForNextTrack ?: (status?.waitingForNextTrack == true)
        val displayTicksSince = displayDimSt?.ticksSinceLastMusic ?: (status?.ticksSinceLastMusic ?: 0)
        val displayDelayTicks = displayDimSt?.nextMusicDelayTicks ?: (status?.nextMusicDelayTicks ?: 0)
        displayedDuration = displayDuration
        displayedHasTrack = displayTrack != null
        pauseBounds?.active = isOp && !localLoading && displayTrack != null
        skipBounds?.active = isOp && !localLoading
        stopBounds?.active = isOp && (localLoading || displayTrack != null || displayIsPlaying || displayWaiting)

        val trackText = if (displayTrack != null) {
            if (displayResolved.isNotEmpty()) {
                formatOggName(displayResolved)
            } else {
                formatSoundEvent(displayTrack)
            }
        } else {
            "No track"
        }

        GuiComponent.drawCenteredString(poseStack, font, trackText, cx, panelY + 26, 0xFFFFFFFF.toInt())

        if (displayTrack != null && displayResolved.isNotEmpty()) {
            GuiComponent.drawCenteredString(poseStack, font, formatSoundEvent(displayTrack), cx, panelY + 38, 0xFF777799.toInt())
        }

        val statusText = when {
            localLoading -> "\u23F3 Loading"
            displayIsPlaying -> "\u25B6 Playing"
            displayTrack != null -> "\u23F8 Paused"
            displayWaiting -> {
                val ticksLeft = (displayDelayTicks - displayTicksSince).coerceAtLeast(0)
                "\u23F3 Next in ${formatTicksShort(ticksLeft)}"
            }
            else -> "\u23F9 Stopped"
        }
        val statusColor = when {
            localLoading -> 0xFF66CCFF.toInt()
            displayIsPlaying -> 0xFF55FF55.toInt()
            displayWaiting -> 0xFFFFAA00.toInt()
            else -> 0xFFAAAAAA.toInt()
        }
        GuiComponent.drawCenteredString(poseStack, font, statusText, cx, panelY + 52, statusColor)
        drawVolumeBar1919(poseStack, volumeBarX, volumeBarY, volumeBarW, volumeBarH)

        val actualProgress1919 = if (displayDuration > 0) (displayPosition.toFloat() / displayDuration.toFloat()).coerceIn(0f, 1f) else 0f
        if (!draggingSeekBar && seekHoldUntilMs > 0 && kotlin.math.abs(actualProgress1919 - seekHoldProgress) < 0.05f) {
            seekHoldUntilMs = 0
        }
        val holdingSeek1919 = !draggingSeekBar && System.currentTimeMillis() < seekHoldUntilMs
        val progress = if (draggingSeekBar) dragSeekProgress else if (holdingSeek1919) seekHoldProgress else actualProgress1919
        val filledW = (barW * progress).toInt()

        GuiComponent.fill(poseStack, barX, barY, barX + barW, barY + barH, 0xFF222244.toInt())
        if (filledW > 0) {
            val barColor1919 = if (draggingSeekBar) 0xFF33AAFF.toInt() else 0xFF00CC66.toInt()
            val barHighlight1919 = if (draggingSeekBar) 0xFF55CCFF.toInt() else 0xFF00EE88.toInt()
            GuiComponent.fill(poseStack, barX, barY, barX + filledW, barY + barH, barColor1919)
            GuiComponent.fill(poseStack, barX, barY, barX + filledW, barY + barH / 2, barHighlight1919)
        }
        GuiComponent.fill(poseStack, barX - 1, barY - 1, barX + barW + 1, barY, 0xFF333355.toInt())
        GuiComponent.fill(poseStack, barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0xFF333355.toInt())
        GuiComponent.fill(poseStack, barX - 1, barY, barX, barY + barH, 0xFF333355.toInt())
        GuiComponent.fill(poseStack, barX + barW, barY, barX + barW + 1, barY + barH, 0xFF333355.toInt())

        if (filledW > 0 && displayDuration > 0) {
            val headX = barX + filledW
            GuiComponent.fill(poseStack, headX - 1, barY - 1, headX + 1, barY + barH + 1, 0xFFFFFFFF.toInt())
        }

        val shownPosition1919 = if ((draggingSeekBar || holdingSeek1919) && displayDuration > 0) ((if (draggingSeekBar) dragSeekProgress else seekHoldProgress) * displayDuration).toLong() else displayPosition
        val timeText = if (displayTrack != null) {
            val posStr = formatTime(shownPosition1919)
            val durStr = if (displayDuration > 0) formatTime(displayDuration) else "--:--"
            "$posStr / $durStr"
        } else {
            "0:00 / 0:00"
        }
        val timeColor1919 = if (draggingSeekBar || holdingSeek1919) 0xFF55CCFF.toInt() else 0xFF999999.toInt()
        GuiComponent.drawCenteredString(poseStack, font, timeText, cx, barY + barH + 3, timeColor1919)

        pauseIsPlaying = displayIsPlaying
        val pauseLbl1919 = if (localLoading) "\u23F3 Wait" else if (pauseIsPlaying) "\u23F8 Pause" else "\u25B6 Play"
        if (ClientOnlyController.isActive) {
            prevBounds?.active = isOp && ClientOnlyController.hasHistory
        }
        prevBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        stopBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        pauseBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, pauseLbl1919, hov, b.active)
        }
        skipBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        applyBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        resetBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        playlistNavBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        tracksNavBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, hov, b.active)
        }
        cacheBtnBounds?.let { b ->
            val hovC = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            val bgC = if (hovC) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
            GuiComponent.fill(poseStack, b.x, b.y, b.x + b.w, b.y + b.h, bgC)
            GuiComponent.fill(poseStack, b.x, b.y, b.x + b.w, b.y + 1, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, b.x, b.y, b.x + 1, b.y + b.h, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            val cacheEnabled = ClientTrackManager.cacheEnabled
            val cacheLbl = if (cacheEnabled) "Cache ON" else "Cache OFF"
            val cacheTxtColor = if (cacheEnabled) 0xFF00FF88.toInt() else 0xFFFF6655.toInt()
            val indicatorX = b.x + 6
            val indicatorY = b.y + (b.h - 6) / 2
            drawCacheIndicator1919(poseStack, indicatorX, indicatorY, 6, cacheEnabled, cacheTxtColor)
            GuiComponent.drawString(poseStack, font, cacheLbl, indicatorX + 10, b.y + (b.h - 8) / 2, cacheTxtColor)
        }

        repeatBtnBounds?.let { b ->
            val hovR = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawRepeatBtn1919(poseStack, b.x, b.y, b.w, b.h, hovR)
        }

        val modeText = when (effectiveRepeatMode()) {
            MusicStatusPacket.RepeatMode.OFF -> "Off"
            MusicStatusPacket.RepeatMode.REPEAT_TRACK -> "Track"
            MusicStatusPacket.RepeatMode.REPEAT_PLAYLIST -> "Playlist"
            MusicStatusPacket.RepeatMode.SHUFFLE -> "Shuffle"
            MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT -> "Shuffle + Repeat"
        }
        GuiComponent.drawCenteredString(poseStack, font, "Mode: $modeText", cx, panelY + 128, 0xFF666688.toInt())

        val delayTextY = fieldBoxY + 4
        val dashX = minFieldX + 50 + 4
        val minFocused1919 = minDelayField?.isFocused == true
        val maxFocused1919 = maxDelayField?.isFocused == true
        for ((fx, focused) in listOf(Pair(minFieldX, minFocused1919), Pair(maxFieldX, maxFocused1919))) {
            val bc = if (focused) 0xFF33EE88.toInt() else 0xFF00CC66.toInt()
            GuiComponent.fill(poseStack, fx, fieldBoxY, fx + 50, fieldBoxY + 16, 0xFF1C1C2A.toInt())
            GuiComponent.fill(poseStack, fx, fieldBoxY, fx + 50, fieldBoxY + 1, bc)
            GuiComponent.fill(poseStack, fx, fieldBoxY, fx + 1, fieldBoxY + 16, bc)
            GuiComponent.fill(poseStack, fx + 49, fieldBoxY, fx + 50, fieldBoxY + 16, bc)
            GuiComponent.fill(poseStack, fx, fieldBoxY + 15, fx + 50, fieldBoxY + 16, bc)
        }
        GuiComponent.drawString(poseStack, font, "Delay:", minFieldX - font.width("Delay:") - 4, delayTextY, 0xFF888888.toInt())
        GuiComponent.drawString(poseStack, font, "-", dashX, delayTextY, 0xFF888888.toInt())
        GuiComponent.drawString(poseStack, font, "ticks", maxFieldX + 50 + 4, delayTextY, 0xFF666666.toInt())

        val activeDelayY = panelY + 190
        val dimLabel1919 = getDimDisplayName(viewedDimId)
        if (status != null && status.customMinDelay >= 0 && status.customMaxDelay >= 0) {
            val dText = "$dimLabel1919: ${status.customMinDelay}-${status.customMaxDelay} ticks (${formatTicksShort(status.customMinDelay)}-${formatTicksShort(status.customMaxDelay)})"
            GuiComponent.drawCenteredString(poseStack, font, dText, cx, activeDelayY, 0xFF55AA77.toInt())
        } else {
            GuiComponent.drawCenteredString(poseStack, font, "$dimLabel1919: Vanilla/mod defaults", cx, activeDelayY, 0xFF666666.toInt())
        }

        if (!isOp) {
            GuiComponent.drawCenteredString(poseStack, font, "\u26A0 View only (OP required)", cx, panelY + panelH - 42, 0xFFFF5555.toInt())
        }

        val queueSize = status?.queue?.size ?: 0
        if (queueSize > 0) {
            GuiComponent.drawCenteredString(poseStack, font, "Queue: $queueSize track${if (queueSize > 1) "s" else ""}", cx, panelY + 138, 0xFF666688.toInt())
        }

        super.render(poseStack, mouseX, mouseY, partialTick)

        renderDimensionOverlay(poseStack, mouseX, mouseY, status)

        if (mouseX in syncBtnX until syncBtnX + topBtnSize && mouseY in syncBtnY until syncBtnY + topBtnSize) {
            renderTooltip(poseStack, Component.literal("Sync with server"), mouseX, mouseY)
        }
        if (mouseX in hotloadBtnX until hotloadBtnX + topBtnSize && mouseY in hotloadBtnY until hotloadBtnY + topBtnSize) {
            renderTooltip(poseStack, Component.literal("Hotload custom tracks"), mouseX, mouseY)
        }
        repeatBtnBounds?.let { b ->
            if (mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) {
                val rLabel = repeatModeLabel(effectiveRepeatMode())
                renderTooltip(poseStack, Component.literal(rLabel), mouseX, mouseY)
            }
        }
        if (mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize) {
            val dimSt1919 = status?.activeDimensions?.find { it.id == viewedDimId }
            val players1919 = dimSt1919?.players ?: emptyList()
            val tooltipLines1919 = buildList {
                add(Component.literal(getDimDisplayName(viewedDimId)))
                if (players1919.isNotEmpty()) {
                    add(Component.literal("Players: ${players1919.joinToString(", ")}"))
                }
            }
            renderComponentTooltip(poseStack, tooltipLines1919, mouseX, mouseY)
        }
        renderVolumeTooltip1919(poseStack, mouseX, mouseY)
    }*/
    //?}

    private fun handleMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean? {
        val mxi = mouseX.toInt()
        val myi = mouseY.toInt()
        if (button == 0 && isPointInVolumeIcon(mouseX, mouseY)) {
            toggleMuteVolume()
            return true
        }
        if (button == 0 && isPointInVolumeControl(mouseX, mouseY)) {
            draggingVolume = true
            setMusicVolumeFromMouse(mouseY)
            return true
        }
        if (button == 0 && mxi in syncBtnX until syncBtnX + topBtnSize && myi in syncBtnY until syncBtnY + topBtnSize) {
            sendControl(MusicControlPacket.Action.REQUEST_SYNC)
            return true
        }
        if (button == 0 && mxi in hotloadBtnX until hotloadBtnX + topBtnSize && myi in hotloadBtnY until hotloadBtnY + topBtnSize) {
            sendControl(MusicControlPacket.Action.HOTLOAD_TRACKS)
            return true
        }
        if (button == 0 && prevBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h && it.active } == true) {
            sendControl(MusicControlPacket.Action.PREVIOUS); return true
        }
        if (button == 0 && stopBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h && it.active } == true) {
            sendControl(MusicControlPacket.Action.STOP); return true
        }
        if (button == 0 && pauseBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h && it.active } == true) {
            if (pauseIsPlaying) sendControl(MusicControlPacket.Action.PAUSE) else sendControl(MusicControlPacket.Action.RESUME)
            return true
        }
        if (button == 0 && skipBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h && it.active } == true) {
            sendControl(MusicControlPacket.Action.SKIP); return true
        }
        if (button == 0 && applyBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h && it.active } == true) {
            applyDelay(); return true
        }
        if (button == 0 && resetBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h && it.active } == true) {
            sendControl(MusicControlPacket.Action.SET_DELAY, trackId = "reset")
            minDelayField!!.value = ""; maxDelayField!!.value = ""
            return true
        }
        if (button == 0 && playlistNavBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h } == true) {
            Minecraft.getInstance().setScreen(PlaylistScreen()); return true
        }
        if (button == 0 && tracksNavBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h } == true) {
            Minecraft.getInstance().setScreen(TrackBrowserScreen()); return true
        }
        if (button == 0 && cacheBtnBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h } == true) {
            ClientTrackManager.setCacheEnabled(!ClientTrackManager.cacheEnabled); return true
        }
        if (button == 0 && repeatBtnBounds?.let { mxi in it.x until it.x + it.w && myi in it.y until it.y + it.h } == true) {
            sendControl(MusicControlPacket.Action.CYCLE_REPEAT_MODE)
            return true
        }
        if (isOp && button == 0 && mouseX >= barX && mouseX <= barX + barW && mouseY >= barY - 4 && mouseY <= barY + barH + 4) {
            if (displayedHasTrack && displayedDuration > 0) {
                draggingSeekBar = true
                dragSeekProgress = ((mouseX - barX) / barW).toFloat().coerceIn(0f, 1f)
                return true
            }
        }
        if (button == 0 && dimFlyoutOpen) {
            val status3 = effectiveStatus()
            val flyoutDims3 = status3?.activeDimensions?.filter { it.players.isNotEmpty() && it.id != viewedDimId }.orEmpty()
            val gap = 2
            for ((i, dim) in flyoutDims3.withIndex()) {
                val bx = dimBtnX - gap - (i + 1) * (topBtnSize + gap) + gap
                if (mxi in bx until bx + topBtnSize && myi in dimBtnY until dimBtnY + topBtnSize) {
                    viewedDimId = dim.id
                    dimFlyoutOpen = false
                    sendControl(MusicControlPacket.Action.SELECT_DIMENSION)
                    return true
                }
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
        if (draggingVolume && button == 0) {
            setMusicVolumeFromMouse(mouseY)
            return true
        }
        if (draggingSeekBar && button == 0) {
            dragSeekProgress = ((mouseX - barX) / barW).toFloat().coerceIn(0f, 1f)
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

    private fun handleMouseReleased(button: Int): Boolean? {
        if (button == 0 && draggingVolume) {
            draggingVolume = false
            return true
        }
        if (button == 0 && draggingSeekBar) {
            draggingSeekBar = false
            seekHoldProgress = dragSeekProgress
            seekHoldUntilMs = System.currentTimeMillis() + 1500L
            if (displayedHasTrack && displayedDuration > 0) {
                val seekMs = (dragSeekProgress * displayedDuration).toLong()
                if (ClientOnlyController.isActive) {
                    ClientOnlyController.seek(seekMs)
                } else {
                    val packet = MusicControlPacket(
                        action = MusicControlPacket.Action.SEEK,
                        trackId = null,
                        queuePosition = null,
                        seekMs = seekMs,
                        targetDim = viewedDimId
                    )
                    PacketHandler.sendToServer(packet)
                }
            }
            return true
        }
        return null
    }

    //? if >=1.21.11 {
    /*override fun mouseReleased(event: net.minecraft.client.input.MouseButtonEvent): Boolean {
        return handleMouseReleased(event.button()) ?: super.mouseReleased(event)
    }*/
    //?} else {
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return handleMouseReleased(button) ?: super.mouseReleased(mouseX, mouseY, button)
    }
    //?}

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

    private fun applyDelay() {
        val minStr = minDelayField?.value ?: return
        val maxStr = maxDelayField?.value ?: return
        val min = minStr.toIntOrNull()
        val max = maxStr.toIntOrNull()
        if (min == null || max == null || min < 0 || max < min) return
        sendControl(MusicControlPacket.Action.SET_DELAY, trackId = "$min:$max")
    }

    private fun syncDelayFields(status: MusicStatusPacket?) {
        val minField = minDelayField ?: return
        val maxField = maxDelayField ?: return
        if (minField.isFocused || maxField.isFocused) return
        val minValue = if (status != null && status.customMinDelay >= 0) status.customMinDelay.toString() else ""
        val maxValue = if (status != null && status.customMaxDelay >= 0) status.customMaxDelay.toString() else ""
        if (minField.value != minValue) minField.value = minValue
        if (maxField.value != maxValue) maxField.value = maxValue
    }

    private fun sendControl(action: MusicControlPacket.Action, trackId: String? = null) {
        if (ClientOnlyController.isActive) {
            when (action) {
                MusicControlPacket.Action.PREVIOUS -> ClientOnlyController.previous()
                MusicControlPacket.Action.STOP -> ClientOnlyController.stop()
                MusicControlPacket.Action.PAUSE -> ClientOnlyController.pause()
                MusicControlPacket.Action.RESUME -> ClientOnlyController.resume()
                MusicControlPacket.Action.SKIP -> ClientOnlyController.skip()
                MusicControlPacket.Action.CYCLE_REPEAT_MODE -> ClientOnlyController.cycleRepeatMode()
                MusicControlPacket.Action.SET_DELAY -> {
                    if (trackId == "reset") {
                        ClientOnlyController.resetDelay()
                    } else if (trackId != null && trackId.contains(":")) {
                        val parts = trackId.split(":")
                        val min = parts[0].toIntOrNull() ?: return
                        val max = parts[1].toIntOrNull() ?: return
                        ClientOnlyController.setDelay(min, max)
                    }
                }
                MusicControlPacket.Action.SELECT_DIMENSION -> {}
                else -> {}
            }
            return
        }
        val packet = MusicControlPacket(action = action, trackId = trackId, queuePosition = null, targetDim = viewedDimId)
        PacketHandler.sendToServer(packet)
    }

    //? if >=1.20 {
    private fun drawTopBtnFrame(graphics: GuiGraphics, x: Int, y: Int, hovered: Boolean) {
        val bg = if (hovered) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
        graphics.fill(x, y, x + topBtnSize, y + topBtnSize, bg)
        graphics.fill(x, y, x + topBtnSize, y + 1, 0xFF00CC66.toInt())
        graphics.fill(x, y, x + 1, y + topBtnSize, 0xFF00CC66.toInt())
        graphics.fill(x + topBtnSize - 1, y, x + topBtnSize, y + topBtnSize, 0xFF00CC66.toInt())
        graphics.fill(x, y + topBtnSize - 1, x + topBtnSize, y + topBtnSize, 0xFF00CC66.toInt())
    }

    private fun drawCustomTopBtn(graphics: GuiGraphics, x: Int, y: Int, label: String, hovered: Boolean) {
        drawTopBtnFrame(graphics, x, y, hovered)
        graphics.drawString(font, label, x + (topBtnSize - font.width(label)) / 2, y + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())
    }

    private fun drawHotloadTopBtn(graphics: GuiGraphics, x: Int, y: Int, hovered: Boolean) {
        drawTopBtnFrame(graphics, x, y, hovered)
        drawHotloadIcon(graphics, x, y, 0xFFFFFFFF.toInt())
    }

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

    //? if >=1.20 {
    private fun drawRepeatBtn(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, hovered: Boolean) {
        val mode = effectiveRepeatMode()
        val active = mode != MusicStatusPacket.RepeatMode.OFF
        val bg = when { hovered -> 0xFF334433.toInt(); active -> 0xFF1C2C1C.toInt(); else -> 0xFF1C1C2A.toInt() }
        val border = if (active) 0xFF00CC66.toInt() else 0xFF333355.toInt()
        graphics.fill(x, y, x + w, y + h, bg)
        graphics.fill(x, y, x + w, y + 1, border)
        graphics.fill(x, y, x + 1, y + h, border)
        graphics.fill(x + w - 1, y, x + w, y + h, border)
        graphics.fill(x, y + h - 1, x + w, y + h, border)
        val iconColor = if (active) 0xFF00EE88.toInt() else 0xFF888899.toInt()
        val cx = x + w / 2
        val cy = y + h / 2
        when (mode) {
            MusicStatusPacket.RepeatMode.OFF -> {
                // Right arrow: normal "play next" behavior
                graphics.fill(cx - 3, cy - 1, cx + 2, cy + 2, iconColor)
                graphics.fill(cx + 2, cy - 2, cx + 3, cy + 3, iconColor)
                graphics.fill(cx + 3, cy - 1, cx + 4, cy + 2, iconColor)
                graphics.fill(cx + 4, cy, cx + 5, cy + 1, iconColor)
            }
            MusicStatusPacket.RepeatMode.REPEAT_TRACK -> {
                // Loop arrow with "1" dot inside
                graphics.fill(cx - 4, cy - 3, cx + 3, cy - 2, iconColor)
                graphics.fill(cx + 3, cy - 3, cx + 4, cy + 1, iconColor)
                graphics.fill(cx - 3, cy + 2, cx + 4, cy + 3, iconColor)
                graphics.fill(cx - 4, cy - 1, cx - 3, cy + 3, iconColor)
                graphics.fill(cx - 5, cy - 2, cx - 4, cy - 1, iconColor)
                graphics.fill(cx - 3, cy - 4, cx - 2, cy - 3, iconColor)
                // "1" indicator
                graphics.fill(cx, cy - 1, cx + 1, cy + 1, iconColor)
                graphics.fill(cx - 1, cy - 1, cx, cy, iconColor)
            }
            MusicStatusPacket.RepeatMode.REPEAT_PLAYLIST -> {
                // Loop arrow without dot
                graphics.fill(cx - 4, cy - 3, cx + 3, cy - 2, iconColor)
                graphics.fill(cx + 3, cy - 3, cx + 4, cy + 1, iconColor)
                graphics.fill(cx - 3, cy + 2, cx + 4, cy + 3, iconColor)
                graphics.fill(cx - 4, cy - 1, cx - 3, cy + 3, iconColor)
                graphics.fill(cx - 5, cy - 2, cx - 4, cy - 1, iconColor)
                graphics.fill(cx - 3, cy - 4, cx - 2, cy - 3, iconColor)
            }
            MusicStatusPacket.RepeatMode.SHUFFLE -> {
                // Crossed arrows
                graphics.fill(cx - 4, cy - 3, cx - 3, cy - 2, iconColor)
                graphics.fill(cx - 3, cy - 2, cx - 2, cy - 1, iconColor)
                graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, iconColor)
                graphics.fill(cx + 1, cy + 1, cx + 2, cy + 2, iconColor)
                graphics.fill(cx + 2, cy + 2, cx + 4, cy + 3, iconColor)
                graphics.fill(cx - 4, cy + 2, cx - 3, cy + 3, iconColor)
                graphics.fill(cx - 3, cy + 1, cx - 2, cy + 2, iconColor)
                graphics.fill(cx + 1, cy - 2, cx + 2, cy - 1, iconColor)
                graphics.fill(cx + 2, cy - 3, cx + 4, cy - 2, iconColor)
                graphics.fill(cx + 4, cy - 4, cx + 5, cy - 1, iconColor)
                graphics.fill(cx + 4, cy + 1, cx + 5, cy + 4, iconColor)
            }
            MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT -> {
                // Crossed arrows + center dot
                graphics.fill(cx - 4, cy - 3, cx - 3, cy - 2, iconColor)
                graphics.fill(cx - 3, cy - 2, cx - 2, cy - 1, iconColor)
                graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, iconColor)
                graphics.fill(cx + 1, cy + 1, cx + 2, cy + 2, iconColor)
                graphics.fill(cx + 2, cy + 2, cx + 4, cy + 3, iconColor)
                graphics.fill(cx - 4, cy + 2, cx - 3, cy + 3, iconColor)
                graphics.fill(cx - 3, cy + 1, cx - 2, cy + 2, iconColor)
                graphics.fill(cx + 1, cy - 2, cx + 2, cy - 1, iconColor)
                graphics.fill(cx + 2, cy - 3, cx + 4, cy - 2, iconColor)
                graphics.fill(cx + 4, cy - 4, cx + 5, cy - 1, iconColor)
                graphics.fill(cx + 4, cy + 1, cx + 5, cy + 4, iconColor)
                graphics.fill(cx, cy - 1, cx + 1, cy + 1, iconColor)
            }
        }
    }
    //?}

    private fun drawHotloadIcon(graphics: GuiGraphics, x: Int, y: Int, color: Int) {
        graphics.fill(x + 6, y + 2, x + 8, y + 8, color)
        graphics.fill(x + 4, y + 6, x + 10, y + 8, color)
        graphics.fill(x + 3, y + 9, x + 11, y + 10, color)
        graphics.fill(x + 4, y + 10, x + 10, y + 11, color)
    }

    private fun drawSpeakerIcon(graphics: GuiGraphics, x: Int, y: Int, color: Int) {
        graphics.fill(x, y + 3, x + 2, y + 7, color)
        graphics.fill(x + 2, y + 2, x + 3, y + 8, color)
        graphics.fill(x + 3, y + 1, x + 5, y + 9, color)
        graphics.fill(x + 6, y + 2, x + 7, y + 4, color)
        graphics.fill(x + 7, y + 3, x + 8, y + 7, color)
        graphics.fill(x + 6, y + 6, x + 7, y + 8, color)
    }

    private fun drawMutedSpeakerIcon(graphics: GuiGraphics, x: Int, y: Int, color: Int) {
        drawSpeakerIcon(graphics, x, y, color)
        graphics.fill(x + 6, y + 1, x + 7, y + 2, color)
        graphics.fill(x + 5, y + 2, x + 6, y + 3, color)
        graphics.fill(x + 4, y + 3, x + 5, y + 4, color)
        graphics.fill(x + 3, y + 4, x + 4, y + 5, color)
        graphics.fill(x + 2, y + 5, x + 3, y + 6, color)
        graphics.fill(x + 1, y + 6, x + 2, y + 7, color)
        graphics.fill(x + 6, y + 7, x + 7, y + 8, color)
        graphics.fill(x + 5, y + 6, x + 6, y + 7, color)
        graphics.fill(x + 4, y + 5, x + 5, y + 6, color)
        graphics.fill(x + 3, y + 4, x + 4, y + 5, color)
        graphics.fill(x + 2, y + 3, x + 3, y + 4, color)
        graphics.fill(x + 1, y + 2, x + 2, y + 3, color)
    }

    private fun drawCacheIndicator(graphics: GuiGraphics, x: Int, y: Int, size: Int, filled: Boolean, color: Int) {
        graphics.fill(x, y, x + size, y + 1, color)
        graphics.fill(x, y, x + 1, y + size, color)
        graphics.fill(x + size - 1, y, x + size, y + size, color)
        graphics.fill(x, y + size - 1, x + size, y + size, color)
        if (filled && size > 2) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, color)
        }
    }

    private fun drawVolumeBar(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        val volume = getMusicVolume()
        val filled = (height * volume).toInt()
        graphics.fill(x, y, x + width, y + height, 0xFF1A1F28.toInt())
        if (filled > 0) {
            graphics.fill(x, y + height - filled, x + width, y + height, 0xFF3FBF7F.toInt())
            graphics.fill(x, y + height - filled, x + width, y + height - filled + 1, 0xFF79E6A7.toInt())
        }
        graphics.fill(x - 1, y - 1, x + width + 1, y, 0xFF335544.toInt())
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFF335544.toInt())
        graphics.fill(x - 1, y, x, y + height, 0xFF335544.toInt())
        graphics.fill(x + width, y, x + width + 1, y + height, 0xFF335544.toInt())
        if (volume <= 0f) {
            drawMutedSpeakerIcon(graphics, x - 1, y + height + 4, 0xFFFF8866.toInt())
        } else {
            drawSpeakerIcon(graphics, x - 1, y + height + 4, 0xFF88CC99.toInt())
        }
    }

    private fun renderVolumeTooltip(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val iconBottom = volumeBarY + volumeBarH + 13
        if (mouseX in (volumeBarX - 2) until (volumeBarX + volumeBarW + 9) && mouseY in volumeBarY until iconBottom) {
            val pctText = if (isPointInVolumeIcon(mouseX.toDouble(), mouseY.toDouble())) {
                if (getMusicVolume() <= 0f) "Restore music volume" else "Mute music"
            } else {
                "Music volume: ${(getMusicVolume() * 100f).toInt()}%"
            }
            graphics.renderTooltipCompat(font, listOf(Component.literal(pctText).visualOrderText), mouseX, mouseY)
        }
    }

    private fun renderDimensionOverlay(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        status: MusicStatusPacket?
    ) {
        val flyoutDims = status?.activeDimensions?.filter { it.players.isNotEmpty() && it.id != viewedDimId }.orEmpty()
        if (flyoutDims.isEmpty()) {
            dimFlyoutOpen = false
            return
        }
        val gap = 2
        val totalW = topBtnSize + gap + flyoutDims.size * topBtnSize + (flyoutDims.size - 1).coerceAtLeast(0) * gap
        val flyoutStartX = dimBtnX - gap - flyoutDims.size * topBtnSize - (flyoutDims.size - 1).coerceAtLeast(0) * gap
        val inCombined = mouseX in flyoutStartX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
        if (mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize) dimFlyoutOpen = true
        if (!inCombined) dimFlyoutOpen = false
        if (!dimFlyoutOpen) return

        for ((i, dim) in flyoutDims.withIndex()) {
            val bx = dimBtnX - gap - (i + 1) * (topBtnSize + gap) + gap
            val isHovered = mouseX in bx until bx + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
            drawCustomTopBtn(graphics, bx, dimBtnY, getDimLabel(dim.id), isHovered)
            if (isHovered) {
                val lines = buildList {
                    add(Component.literal(getDimDisplayName(dim.id)).visualOrderText)
                    add(Component.literal("Players: ${dim.players.joinToString(", ")}").visualOrderText)
                }
                graphics.renderTooltipCompat(font, lines, mouseX, mouseY)
            }
        }
    }

    private fun renderTopButtonTooltips(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        status: MusicStatusPacket?
    ) {
        if (mouseX in syncBtnX until syncBtnX + topBtnSize && mouseY in syncBtnY until syncBtnY + topBtnSize) {
            graphics.renderTooltipCompat(font, listOf(Component.literal("Sync with server").visualOrderText), mouseX, mouseY)
        }
        if (mouseX in hotloadBtnX until hotloadBtnX + topBtnSize && mouseY in hotloadBtnY until hotloadBtnY + topBtnSize) {
            graphics.renderTooltipCompat(font, listOf(Component.literal("Hotload custom tracks").visualOrderText), mouseX, mouseY)
        }
        repeatBtnBounds?.let { b ->
            if (mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) {
                val label = repeatModeLabel(effectiveRepeatMode())
                graphics.renderTooltipCompat(font, listOf(Component.literal(label).visualOrderText), mouseX, mouseY)
            }
        }
        if (mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize) {
            val dimSt = status?.activeDimensions?.find { it.id == viewedDimId }
            val players = dimSt?.players ?: emptyList()
            val lines = buildList {
                add(Component.literal(getDimDisplayName(viewedDimId)).visualOrderText)
                if (players.isNotEmpty()) {
                    add(Component.literal("Players: ${players.joinToString(", ")}").visualOrderText)
                }
            }
            graphics.renderTooltipCompat(font, lines, mouseX, mouseY)
        }
    }
    //?} else {
    /*private fun renderDimensionOverlay(
        poseStack: PoseStack,
        mouseX: Int,
        mouseY: Int,
        status: MusicStatusPacket?
    ) {
        val flyoutDims = status?.activeDimensions?.filter { it.players.isNotEmpty() && it.id != viewedDimId }.orEmpty()
        if (flyoutDims.isEmpty()) {
            dimFlyoutOpen = false
            return
        }
        val gap = 2
        val flyoutStartX = dimBtnX - gap - flyoutDims.size * topBtnSize - (flyoutDims.size - 1).coerceAtLeast(0) * gap
        val inCombined = mouseX in flyoutStartX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
        if (mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize) dimFlyoutOpen = true
        if (!inCombined) dimFlyoutOpen = false
        if (!dimFlyoutOpen) return

        for ((i, dim) in flyoutDims.withIndex()) {
            val bx = dimBtnX - gap - (i + 1) * (topBtnSize + gap) + gap
            val isHovered = mouseX in bx until bx + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
            val lbl = getDimLabel(dim.id)
            val bg = if (isHovered) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
            GuiComponent.fill(poseStack, bx, dimBtnY, bx + topBtnSize, dimBtnY + topBtnSize, bg)
            GuiComponent.fill(poseStack, bx, dimBtnY, bx + topBtnSize, dimBtnY + 1, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, bx, dimBtnY, bx + 1, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, bx + topBtnSize - 1, dimBtnY, bx + topBtnSize, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, bx, dimBtnY + topBtnSize - 1, bx + topBtnSize, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
            GuiComponent.drawString(poseStack, font, lbl, bx + (topBtnSize - font.width(lbl)) / 2, dimBtnY + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())
            if (isHovered) {
                renderComponentTooltip(poseStack, listOf(
                    Component.literal(getDimDisplayName(dim.id)),
                    Component.literal("Players: ${dim.players.joinToString(", ")}")
                ), mouseX, mouseY)
            }
        }
    }

    private fun drawTopBtnFrame1919(poseStack: PoseStack, x: Int, y: Int, hovered: Boolean) {
        val bg = if (hovered) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
        GuiComponent.fill(poseStack, x, y, x + topBtnSize, y + topBtnSize, bg)
        GuiComponent.fill(poseStack, x, y, x + topBtnSize, y + 1, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, x, y, x + 1, y + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, x + topBtnSize - 1, y, x + topBtnSize, y + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, x, y + topBtnSize - 1, x + topBtnSize, y + topBtnSize, 0xFF00CC66.toInt())
    }

    private fun drawCustomTopBtn1919(poseStack: PoseStack, x: Int, y: Int, label: String, hovered: Boolean) {
        drawTopBtnFrame1919(poseStack, x, y, hovered)
        GuiComponent.drawString(poseStack, font, label, x + (topBtnSize - font.width(label)) / 2, y + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())
    }

    private fun drawHotloadTopBtn1919(poseStack: PoseStack, x: Int, y: Int, hovered: Boolean) {
        drawTopBtnFrame1919(poseStack, x, y, hovered)
        drawHotloadIcon1919(poseStack, x, y, 0xFFFFFFFF.toInt())
    }

    private fun drawHotloadIcon1919(poseStack: PoseStack, x: Int, y: Int, color: Int) {
        GuiComponent.fill(poseStack, x + 6, y + 2, x + 8, y + 8, color)
        GuiComponent.fill(poseStack, x + 4, y + 6, x + 10, y + 8, color)
        GuiComponent.fill(poseStack, x + 3, y + 9, x + 11, y + 10, color)
        GuiComponent.fill(poseStack, x + 4, y + 10, x + 10, y + 11, color)
    }

    private fun drawSpeakerIcon1919(poseStack: PoseStack, x: Int, y: Int, color: Int) {
        GuiComponent.fill(poseStack, x, y + 3, x + 2, y + 7, color)
        GuiComponent.fill(poseStack, x + 2, y + 2, x + 3, y + 8, color)
        GuiComponent.fill(poseStack, x + 3, y + 1, x + 5, y + 9, color)
        GuiComponent.fill(poseStack, x + 6, y + 2, x + 7, y + 4, color)
        GuiComponent.fill(poseStack, x + 7, y + 3, x + 8, y + 7, color)
        GuiComponent.fill(poseStack, x + 6, y + 6, x + 7, y + 8, color)
    }

    private fun drawMutedSpeakerIcon1919(poseStack: PoseStack, x: Int, y: Int, color: Int) {
        drawSpeakerIcon1919(poseStack, x, y, color)
        GuiComponent.fill(poseStack, x + 6, y + 1, x + 7, y + 2, color)
        GuiComponent.fill(poseStack, x + 5, y + 2, x + 6, y + 3, color)
        GuiComponent.fill(poseStack, x + 4, y + 3, x + 5, y + 4, color)
        GuiComponent.fill(poseStack, x + 3, y + 4, x + 4, y + 5, color)
        GuiComponent.fill(poseStack, x + 2, y + 5, x + 3, y + 6, color)
        GuiComponent.fill(poseStack, x + 1, y + 6, x + 2, y + 7, color)
        GuiComponent.fill(poseStack, x + 6, y + 7, x + 7, y + 8, color)
        GuiComponent.fill(poseStack, x + 5, y + 6, x + 6, y + 7, color)
        GuiComponent.fill(poseStack, x + 4, y + 5, x + 5, y + 6, color)
        GuiComponent.fill(poseStack, x + 3, y + 4, x + 4, y + 5, color)
        GuiComponent.fill(poseStack, x + 2, y + 3, x + 3, y + 4, color)
        GuiComponent.fill(poseStack, x + 1, y + 2, x + 2, y + 3, color)
    }

    private fun drawCacheIndicator1919(poseStack: PoseStack, x: Int, y: Int, size: Int, filled: Boolean, color: Int) {
        GuiComponent.fill(poseStack, x, y, x + size, y + 1, color)
        GuiComponent.fill(poseStack, x, y, x + 1, y + size, color)
        GuiComponent.fill(poseStack, x + size - 1, y, x + size, y + size, color)
        GuiComponent.fill(poseStack, x, y + size - 1, x + size, y + size, color)
        if (filled && size > 2) {
            GuiComponent.fill(poseStack, x + 1, y + 1, x + size - 1, y + size - 1, color)
        }
    }

    private fun drawRepeatBtn1919(poseStack: PoseStack, x: Int, y: Int, w: Int, h: Int, hovered: Boolean) {
        val rMode = ClientOnlyController.repeatMode
        val active = rMode != ClientOnlyController.RepeatMode.OFF
        val bg = when { hovered -> 0xFF334433.toInt(); active -> 0xFF1C2C1C.toInt(); else -> 0xFF1C1C2A.toInt() }
        val border = if (active) 0xFF00CC66.toInt() else 0xFF333355.toInt()
        GuiComponent.fill(poseStack, x, y, x + w, y + h, bg)
        GuiComponent.fill(poseStack, x, y, x + w, y + 1, border)
        GuiComponent.fill(poseStack, x, y, x + 1, y + h, border)
        GuiComponent.fill(poseStack, x + w - 1, y, x + w, y + h, border)
        GuiComponent.fill(poseStack, x, y + h - 1, x + w, y + h, border)
        val iconColor = if (active) 0xFF00EE88.toInt() else 0xFF888899.toInt()
        val cx = x + w / 2
        val cy = y + h / 2
        when (rMode) {
            ClientOnlyController.RepeatMode.OFF -> {
                GuiComponent.fill(poseStack, cx - 3, cy - 1, cx + 2, cy + 2, iconColor)
                GuiComponent.fill(poseStack, cx + 2, cy - 2, cx + 3, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx + 3, cy - 1, cx + 4, cy + 2, iconColor)
                GuiComponent.fill(poseStack, cx + 4, cy, cx + 5, cy + 1, iconColor)
            }
            ClientOnlyController.RepeatMode.REPEAT_TRACK -> {
                GuiComponent.fill(poseStack, cx - 4, cy - 3, cx + 3, cy - 2, iconColor)
                GuiComponent.fill(poseStack, cx + 3, cy - 3, cx + 4, cy + 1, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy + 2, cx + 4, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 4, cy - 1, cx - 3, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 5, cy - 2, cx - 4, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy - 4, cx - 2, cy - 3, iconColor)
                GuiComponent.fill(poseStack, cx, cy - 1, cx + 1, cy + 1, iconColor)
                GuiComponent.fill(poseStack, cx - 1, cy - 1, cx, cy, iconColor)
            }
            ClientOnlyController.RepeatMode.REPEAT_PLAYLIST -> {
                GuiComponent.fill(poseStack, cx - 4, cy - 3, cx + 3, cy - 2, iconColor)
                GuiComponent.fill(poseStack, cx + 3, cy - 3, cx + 4, cy + 1, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy + 2, cx + 4, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 4, cy - 1, cx - 3, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 5, cy - 2, cx - 4, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy - 4, cx - 2, cy - 3, iconColor)
            }
            ClientOnlyController.RepeatMode.SHUFFLE -> {
                GuiComponent.fill(poseStack, cx - 4, cy - 3, cx - 3, cy - 2, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy - 2, cx - 2, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx - 1, cy - 1, cx + 1, cy + 1, iconColor)
                GuiComponent.fill(poseStack, cx + 1, cy + 1, cx + 2, cy + 2, iconColor)
                GuiComponent.fill(poseStack, cx + 2, cy + 2, cx + 4, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 4, cy + 2, cx - 3, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy + 1, cx - 2, cy + 2, iconColor)
                GuiComponent.fill(poseStack, cx + 1, cy - 2, cx + 2, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx + 2, cy - 3, cx + 4, cy - 2, iconColor)
                GuiComponent.fill(poseStack, cx + 4, cy - 4, cx + 5, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx + 4, cy + 1, cx + 5, cy + 4, iconColor)
            }
            ClientOnlyController.RepeatMode.SHUFFLE_REPEAT -> {
                GuiComponent.fill(poseStack, cx - 4, cy - 3, cx - 3, cy - 2, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy - 2, cx - 2, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx - 1, cy - 1, cx + 1, cy + 1, iconColor)
                GuiComponent.fill(poseStack, cx + 1, cy + 1, cx + 2, cy + 2, iconColor)
                GuiComponent.fill(poseStack, cx + 2, cy + 2, cx + 4, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 4, cy + 2, cx - 3, cy + 3, iconColor)
                GuiComponent.fill(poseStack, cx - 3, cy + 1, cx - 2, cy + 2, iconColor)
                GuiComponent.fill(poseStack, cx + 1, cy - 2, cx + 2, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx + 2, cy - 3, cx + 4, cy - 2, iconColor)
                GuiComponent.fill(poseStack, cx + 4, cy - 4, cx + 5, cy - 1, iconColor)
                GuiComponent.fill(poseStack, cx + 4, cy + 1, cx + 5, cy + 4, iconColor)
                GuiComponent.fill(poseStack, cx - 1, cy - 1, cx, cy, iconColor)
                GuiComponent.fill(poseStack, cx, cy - 1, cx + 1, cy + 1, iconColor)
            }
        }
    }

    private fun drawCustomBtn1919(poseStack: PoseStack, x: Int, y: Int, w: Int, h: Int, label: String, hovered: Boolean, active: Boolean = true) {
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

    //? if <1.20 {
    /*private fun drawVolumeBar1919(poseStack: PoseStack, x: Int, y: Int, width: Int, height: Int) {
        val volume = getMusicVolume()
        val filled = (height * volume).toInt()
        GuiComponent.fill(poseStack, x, y, x + width, y + height, 0xFF1A1F28.toInt())
        if (filled > 0) {
            GuiComponent.fill(poseStack, x, y + height - filled, x + width, y + height, 0xFF3FBF7F.toInt())
            GuiComponent.fill(poseStack, x, y + height - filled, x + width, y + height - filled + 1, 0xFF79E6A7.toInt())
        }
        GuiComponent.fill(poseStack, x - 1, y - 1, x + width + 1, y, 0xFF335544.toInt())
        GuiComponent.fill(poseStack, x - 1, y + height, x + width + 1, y + height + 1, 0xFF335544.toInt())
        GuiComponent.fill(poseStack, x - 1, y, x, y + height, 0xFF335544.toInt())
        GuiComponent.fill(poseStack, x + width, y, x + width + 1, y + height, 0xFF335544.toInt())
        if (volume <= 0f) {
            drawMutedSpeakerIcon1919(poseStack, x - 1, y + height + 4, 0xFFFF8866.toInt())
        } else {
            drawSpeakerIcon1919(poseStack, x - 1, y + height + 4, 0xFF88CC99.toInt())
        }
    }

    private fun renderVolumeTooltip1919(poseStack: PoseStack, mouseX: Int, mouseY: Int) {
        val iconBottom = volumeBarY + volumeBarH + 13
        if (mouseX in (volumeBarX - 2) until (volumeBarX + volumeBarW + 9) && mouseY in volumeBarY until iconBottom) {
            val tooltip = if (isPointInVolumeIcon(mouseX.toDouble(), mouseY.toDouble())) {
                if (getMusicVolume() <= 0f) "Restore music volume" else "Mute music"
            } else {
                "Music volume: ${(getMusicVolume() * 100f).toInt()}%"
            }
            renderTooltip(poseStack, Component.literal(tooltip), mouseX, mouseY)
        }
    }*/
    //?} else {
    private fun drawVolumeBar1919(unused: Any, x: Int, y: Int, width: Int, height: Int) {}
    private fun renderVolumeTooltip1919(unused: Any, mouseX: Int, mouseY: Int) {}
    //?}

    private fun isPointInVolumeControl(mouseX: Double, mouseY: Double): Boolean {
        val minX = volumeBarX - 3
        val maxX = volumeBarX + volumeBarW + 3
        val minY = volumeBarY - 2
        val maxY = volumeBarY + volumeBarH + 2
        return mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY
    }

    private fun isPointInVolumeIcon(mouseX: Double, mouseY: Double): Boolean {
        val iconX = volumeBarX - 1
        val iconY = volumeBarY + volumeBarH + 4
        return mouseX >= iconX && mouseX <= iconX + 8 && mouseY >= iconY && mouseY <= iconY + 9
    }

    private fun setMusicVolumeFromMouse(mouseY: Double) {
        val progress = ((volumeBarY + volumeBarH - mouseY) / volumeBarH.toDouble()).coerceIn(0.0, 1.0)
        setMusicVolume(progress.toFloat())
    }

    private fun toggleMuteVolume() {
        val currentVolume = getMusicVolume()
        if (currentVolume > 0f) {
            previousMusicVolume = currentVolume
            setMusicVolume(0f)
        } else {
            setMusicVolume(previousMusicVolume.coerceIn(0.05f, 1f))
        }
    }

    private fun setMusicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        val mc = Minecraft.getInstance()
        if (clamped > 0f) {
            previousMusicVolume = clamped
        }
        dev.mcrib884.musync.setMusicVolume(mc, clamped)
        mc.options.save()
    }

    private fun getDimLabel(dimId: String): String = when (dimId) {
        "minecraft:overworld" -> "O"
        "minecraft:the_nether" -> "N"
        "minecraft:the_end" -> "E"
        else -> dimId.substringAfterLast(":").firstOrNull()?.uppercase() ?: "?"
    }

    private fun getDimDisplayName(dimId: String): String = when (dimId) {
        "minecraft:overworld" -> "Overworld"
        "minecraft:the_nether" -> "Nether"
        "minecraft:the_end" -> "End"
        else -> dimId.substringAfterLast(":").replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private fun formatOggName(path: String): String {
        return dev.mcrib884.musync.TrackNames.formatOggName(path)
    }

    private fun formatSoundEvent(id: String): String {
        return if (id.startsWith("custom:")) {
            dev.mcrib884.musync.TrackNames.formatTrack(id)
        } else {
            dev.mcrib884.musync.TrackNames.formatPoolName(id)
        }
    }

    private fun getMusicVolume(): Float {
        return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC).coerceIn(0f, 1f)
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
