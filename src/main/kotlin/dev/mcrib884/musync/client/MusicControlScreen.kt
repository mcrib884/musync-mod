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

    private data class BtnBounds(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, var active: Boolean = true)

    private var stopBounds: BtnBounds? = null
    private var pauseBounds: BtnBounds? = null
    private var skipBounds: BtnBounds? = null
    private var pauseIsPlaying = false
    private var applyBounds: BtnBounds? = null
    private var resetBounds: BtnBounds? = null
    private var playlistNavBounds: BtnBounds? = null
    private var tracksNavBounds: BtnBounds? = null
    private var cacheBtnBounds: BtnBounds? = null
    private var minDelayField: EditBox? = null
    private var maxDelayField: EditBox? = null
    private var minFieldX = 0
    private var maxFieldX = 0
    private var fieldBoxY = 0
    private var syncBtnX = 0
    private var syncBtnY = 0
    private var dimBtnX = 0
    private var dimBtnY = 0
    private val topBtnSize = 14
    private var viewedDimId: String = "minecraft:overworld"
    private var dimFlyoutOpen: Boolean = false
    private var displayedDuration: Long = 0
    private var displayedHasTrack: Boolean = false

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
        val btnH = 16
        val btnSpacing = 6
        val totalBtnW = btnW * 3 + btnSpacing * 2
        val btnStartX = panelX + (panelW - totalBtnW) / 2
        val btnY = panelY + 102

        stopBounds = BtnBounds(btnStartX, btnY, btnW, btnH, "\u25A0 Stop", op)
        pauseBounds = BtnBounds(btnStartX + btnW + btnSpacing, btnY, btnW, btnH, "\u23F8 Pause", op)
        skipBounds = BtnBounds(btnStartX + (btnW + btnSpacing) * 2, btnY, btnW, btnH, "\u23ED Skip", op)

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

        val status = ClientMusicPlayer.getCurrentStatus()
        if (status != null && status.customMinDelay >= 0) {
            minDelayField!!.value = status.customMinDelay.toString()
            maxDelayField!!.value = status.customMaxDelay.toString()
        }

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
        dimBtnX = panelX + panelW - topBtnSize - 4
        dimBtnY = panelY + 4
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

        val syncingNow = ClientMusicPlayer.getCurrentStatus()?.syncOverworld == true
        val dimBtnLabel = if (syncingNow) "\u00A7aO" else getDimLabel(viewedDimId)
        val syncBtnHovered = mouseX in syncBtnX until syncBtnX + topBtnSize && mouseY in syncBtnY until syncBtnY + topBtnSize
        val dimBtnHovered = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
        drawCustomTopBtn(graphics, syncBtnX, syncBtnY, "\u21C4", syncBtnHovered)
        drawCustomTopBtn(graphics, dimBtnX, dimBtnY, dimBtnLabel, dimBtnHovered)

        val cx = panelX + panelW / 2

        graphics.drawCenteredString(font, "\u266B MuSync \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = ClientMusicPlayer.getCurrentStatus()
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

        graphics.drawCenteredString(font, trackText, cx, panelY + 26, 0xFFFFFF)

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

        val progress = if (displayDuration > 0) (displayPosition.toFloat() / displayDuration.toFloat()).coerceIn(0f, 1f) else 0f
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

        if (filledW > 0 && displayDuration > 0) {
            val headX = barX + filledW
            graphics.fill(headX - 1, barY - 1, headX + 1, barY + barH + 1, 0xFFFFFFFF.toInt())
        }

        val timeText = if (displayTrack != null) {
            val posStr = formatTime(displayPosition)
            val durStr = if (displayDuration > 0) formatTime(displayDuration) else "--:--"
            "$posStr / $durStr"
        } else {
            "0:00 / 0:00"
        }
        graphics.drawCenteredString(font, timeText, cx, barY + barH + 3, 0xFF999999.toInt())

        pauseIsPlaying = displayIsPlaying
        val pauseLabel = if (localLoading) "\u23F3 Wait" else if (pauseIsPlaying) "\u23F8 Pause" else "\u25B6 Play"
        pauseBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, pauseLabel, hov, b.active)
        }
        stopBounds?.let { b ->
            val hov = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, hov, b.active)
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
            val cacheLabel = if (ClientTrackManager.cacheEnabled) "\u26BF Cache: ON" else "\u26BF Cache: OFF"
            val cacheLabelColor = if (ClientTrackManager.cacheEnabled) 0xFF00FF88.toInt() else 0xFFFF6655.toInt()
            val bg = if (hov) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
            graphics.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg)
            graphics.fill(b.x, b.y, b.x + b.w, b.y + 1, 0xFF00CC66.toInt())
            graphics.fill(b.x, b.y, b.x + 1, b.y + b.h, 0xFF00CC66.toInt())
            graphics.fill(b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            graphics.fill(b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            graphics.drawString(font, cacheLabel, b.x + (b.w - font.width(cacheLabel)) / 2, b.y + (b.h - 8) / 2, cacheLabelColor)
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
    }
    //?} else {
    /*override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(poseStack)

        GuiComponent.fill(poseStack, panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF1A1A2E.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101020.toInt())
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelW, panelY + 2, 0xFF00CC66.toInt())

        val syncHov1919 = mouseX in syncBtnX until syncBtnX + topBtnSize && mouseY in syncBtnY until syncBtnY + topBtnSize
        val dimHov1919 = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize
        val syncing1919 = ClientMusicPlayer.getCurrentStatus()?.syncOverworld == true
        val dimBtnLbl1919 = if (syncing1919) "\u00A7aO" else getDimLabel(viewedDimId)
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY, syncBtnX + topBtnSize, syncBtnY + topBtnSize, if (syncHov1919) 0xFF334433.toInt() else 0xFF1C1C2A.toInt())
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY, syncBtnX + topBtnSize, syncBtnY + 1, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY, syncBtnX + 1, syncBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, syncBtnX + topBtnSize - 1, syncBtnY, syncBtnX + topBtnSize, syncBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, syncBtnX, syncBtnY + topBtnSize - 1, syncBtnX + topBtnSize, syncBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.drawString(poseStack, font, "\u21C4", syncBtnX + (topBtnSize - font.width("\u21C4")) / 2, syncBtnY + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY, dimBtnX + topBtnSize, dimBtnY + topBtnSize, if (dimHov1919) 0xFF334433.toInt() else 0xFF1C1C2A.toInt())
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY, dimBtnX + topBtnSize, dimBtnY + 1, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY, dimBtnX + 1, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, dimBtnX + topBtnSize - 1, dimBtnY, dimBtnX + topBtnSize, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.fill(poseStack, dimBtnX, dimBtnY + topBtnSize - 1, dimBtnX + topBtnSize, dimBtnY + topBtnSize, 0xFF00CC66.toInt())
        GuiComponent.drawString(poseStack, font, dimBtnLbl1919, dimBtnX + (topBtnSize - font.width(dimBtnLbl1919)) / 2, dimBtnY + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())

        val cx = panelX + panelW / 2

        GuiComponent.drawCenteredString(poseStack, font, "\u266B MuSync \u266B", cx, panelY + 8, 0xFF00CC66.toInt())

        val status = ClientMusicPlayer.getCurrentStatus()
        val ownPosition = if (status != null && status.isPlaying && status.currentTrack != null) {
            ClientMusicPlayer.getCurrentPositionMs()
        } else {
            status?.currentPositionMs ?: 0
        }
        val playerDimId = Minecraft.getInstance().player?.entityLevel()?.dimension()?.location()?.toString() ?: "minecraft:overworld"
        val localLoading = ClientMusicPlayer.isLoading() && viewedDimId == playerDimId
        val localLoadingTrack = if (localLoading) ClientMusicPlayer.getLoadingTrack() else null
        val displayDimSt = if (viewedDimId != playerDimId) status?.activeDimensions?.find { it.id == viewedDimId } else null
        val displayTrack = localLoadingTrack ?: displayDimSt?.currentTrack ?: status?.currentTrack
        val displayResolved = displayDimSt?.resolvedName ?: status?.resolvedName ?: ""
        val displayIsPlaying = if (localLoading) false else (displayDimSt?.isPlaying ?: (status?.isPlaying == true))
        val displayDuration: Long = displayDimSt?.durationMs ?: (status?.durationMs ?: 0)
        val rawDisplayPosition: Long = displayDimSt?.currentPositionMs ?: ownPosition
        val displayPosition: Long = if (displayDimSt != null && displayDimSt.isPlaying)
            (rawDisplayPosition + ClientMusicPlayer.getStatusAge()).coerceAtMost(displayDimSt.durationMs.coerceAtLeast(rawDisplayPosition))
        else rawDisplayPosition
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

        GuiComponent.drawCenteredString(poseStack, font, trackText, cx, panelY + 26, 0xFFFFFF)

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

        val progress = if (displayDuration > 0) (displayPosition.toFloat() / displayDuration.toFloat()).coerceIn(0f, 1f) else 0f
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

        if (filledW > 0 && displayDuration > 0) {
            val headX = barX + filledW
            GuiComponent.fill(poseStack, headX - 1, barY - 1, headX + 1, barY + barH + 1, 0xFFFFFFFF.toInt())
        }

        val timeText = if (displayTrack != null) {
            val posStr = formatTime(displayPosition)
            val durStr = if (displayDuration > 0) formatTime(displayDuration) else "--:--"
            "$posStr / $durStr"
        } else {
            "0:00 / 0:00"
        }
        GuiComponent.drawCenteredString(poseStack, font, timeText, cx, barY + barH + 3, 0xFF999999.toInt())

        pauseIsPlaying = displayIsPlaying
        val pauseLbl1919 = if (localLoading) "\u23F3 Wait" else if (pauseIsPlaying) "\u23F8 Pause" else "\u25B6 Play"
        pauseBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, pauseLbl1919, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, b.active) }
        stopBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, b.active) }
        skipBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, b.active) }
        applyBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, b.active) }
        resetBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, b.active) }
        playlistNavBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, b.active) }
        tracksNavBounds?.let { b -> drawCustomBtn1919(poseStack, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, b.active) }
        cacheBtnBounds?.let { b ->
            val hovC = mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h
            val cacheLbl = if (ClientTrackManager.cacheEnabled) "\u26BF Cache: ON" else "\u26BF Cache: OFF"
            val cacheTxtColor = if (ClientTrackManager.cacheEnabled) 0xFF00FF88.toInt() else 0xFFFF6655.toInt()
            val bgC = if (hovC) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
            GuiComponent.fill(poseStack, b.x, b.y, b.x + b.w, b.y + b.h, bgC)
            GuiComponent.fill(poseStack, b.x, b.y, b.x + b.w, b.y + 1, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, b.x, b.y, b.x + 1, b.y + b.h, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, b.x + b.w - 1, b.y, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, b.x, b.y + b.h - 1, b.x + b.w, b.y + b.h, 0xFF00CC66.toInt())
            GuiComponent.drawString(poseStack, font, cacheLbl, b.x + (b.w - font.width(cacheLbl)) / 2, b.y + (b.h - 8) / 2, cacheTxtColor)
        }

        val modeText = when (status?.mode) {
            MusicStatusPacket.PlayMode.AUTONOMOUS -> "Auto"
            MusicStatusPacket.PlayMode.PLAYLIST -> "Playlist"
            MusicStatusPacket.PlayMode.SINGLE_TRACK -> "Single"
            else -> "---"
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
    }*/
    //?}

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mxi = mouseX.toInt()
        val myi = mouseY.toInt()
        if (button == 0 && mxi in syncBtnX until syncBtnX + topBtnSize && myi in syncBtnY until syncBtnY + topBtnSize) {
            sendControl(MusicControlPacket.Action.REQUEST_SYNC)
            return true
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
        if (isOp && button == 0 && mouseX >= barX && mouseX <= barX + barW && mouseY >= barY - 2 && mouseY <= barY + barH + 2) {
            val now = System.currentTimeMillis()
            if (now - lastSeekTimeMs < SEEK_COOLDOWN_MS) return true
            if (displayedHasTrack && displayedDuration > 0) {
                val clickProgress = ((mouseX - barX) / barW).coerceIn(0.0, 1.0)
                val seekMs = (clickProgress * displayedDuration).toLong()
                val packet = MusicControlPacket(
                    action = MusicControlPacket.Action.SEEK,
                    trackId = null,
                    queuePosition = null,
                    seekMs = seekMs,
                    targetDim = viewedDimId
                )
                PacketHandler.sendToServer(packet)
                lastSeekTimeMs = now
                return true
            }
        }
        if (button == 0 && dimFlyoutOpen) {
            val status3 = ClientMusicPlayer.getCurrentStatus()
            val flyoutDims3 = status3?.activeDimensions?.filter { it.players.isNotEmpty() && it.id != viewedDimId }.orEmpty()
            val gap = 2
            for ((i, dim) in flyoutDims3.withIndex()) {
                val by = dimBtnY + topBtnSize + gap + i * (topBtnSize + gap)
                if (mxi in dimBtnX until dimBtnX + topBtnSize && myi in by until by + topBtnSize) {
                    viewedDimId = dim.id
                    dimFlyoutOpen = false
                    sendControl(MusicControlPacket.Action.REQUEST_SYNC)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        //? if neoforge {
        /*if (dev.mcrib884.musync.MuSyncNeoForge.MUSIC_GUI_KEY.matches(keyCode, scanCode)) {*/
        //?} else {
        if (dev.mcrib884.musync.MuSyncForge.MUSIC_GUI_KEY.matches(keyCode, scanCode)) {
        //?}
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
        val packet = MusicControlPacket(action = action, trackId = trackId, queuePosition = null, targetDim = viewedDimId)
        PacketHandler.sendToServer(packet)
    }

    //? if >=1.20 {
    private fun drawCustomTopBtn(graphics: GuiGraphics, x: Int, y: Int, label: String, hovered: Boolean) {
        val bg = if (hovered) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
        graphics.fill(x, y, x + topBtnSize, y + topBtnSize, bg)
        graphics.fill(x, y, x + topBtnSize, y + 1, 0xFF00CC66.toInt())
        graphics.fill(x, y, x + 1, y + topBtnSize, 0xFF00CC66.toInt())
        graphics.fill(x + topBtnSize - 1, y, x + topBtnSize, y + topBtnSize, 0xFF00CC66.toInt())
        graphics.fill(x, y + topBtnSize - 1, x + topBtnSize, y + topBtnSize, 0xFF00CC66.toInt())
        graphics.drawString(font, label, x + (topBtnSize - font.width(label)) / 2, y + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())
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
        val totalH = topBtnSize + gap + flyoutDims.size * topBtnSize + (flyoutDims.size - 1).coerceAtLeast(0) * gap
        val inCombined = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + totalH
        if (mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize) dimFlyoutOpen = true
        if (!inCombined) dimFlyoutOpen = false
        if (!dimFlyoutOpen) return

        for ((i, dim) in flyoutDims.withIndex()) {
            val by = dimBtnY + topBtnSize + gap + i * (topBtnSize + gap)
            val isHovered = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in by until by + topBtnSize
            drawCustomTopBtn(graphics, dimBtnX, by, getDimLabel(dim.id), isHovered)
            if (isHovered) {
                val lines = buildList {
                    add(Component.literal(getDimDisplayName(dim.id)).visualOrderText)
                    add(Component.literal("Players: ${dim.players.joinToString(", ")}").visualOrderText)
                }
                graphics.renderTooltip(font, lines, mouseX, mouseY)
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
            graphics.renderTooltip(font, listOf(Component.literal("Sync with server").visualOrderText), mouseX, mouseY)
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
            graphics.renderTooltip(font, lines, mouseX, mouseY)
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
        val totalH = topBtnSize + gap + flyoutDims.size * topBtnSize + (flyoutDims.size - 1).coerceAtLeast(0) * gap
        val inCombined = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + totalH
        if (mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in dimBtnY until dimBtnY + topBtnSize) dimFlyoutOpen = true
        if (!inCombined) dimFlyoutOpen = false
        if (!dimFlyoutOpen) return

        for ((i, dim) in flyoutDims.withIndex()) {
            val by = dimBtnY + topBtnSize + gap + i * (topBtnSize + gap)
            val isHovered = mouseX in dimBtnX until dimBtnX + topBtnSize && mouseY in by until by + topBtnSize
            val lbl = getDimLabel(dim.id)
            val bg = if (isHovered) 0xFF334433.toInt() else 0xFF1C1C2A.toInt()
            GuiComponent.fill(poseStack, dimBtnX, by, dimBtnX + topBtnSize, by + topBtnSize, bg)
            GuiComponent.fill(poseStack, dimBtnX, by, dimBtnX + topBtnSize, by + 1, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, dimBtnX, by, dimBtnX + 1, by + topBtnSize, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, dimBtnX + topBtnSize - 1, by, dimBtnX + topBtnSize, by + topBtnSize, 0xFF00CC66.toInt())
            GuiComponent.fill(poseStack, dimBtnX, by + topBtnSize - 1, dimBtnX + topBtnSize, by + topBtnSize, 0xFF00CC66.toInt())
            GuiComponent.drawString(poseStack, font, lbl, dimBtnX + (topBtnSize - font.width(lbl)) / 2, by + (topBtnSize - 8) / 2, 0xFFFFFFFF.toInt())
            if (isHovered) {
                renderComponentTooltip(poseStack, listOf(
                    Component.literal(getDimDisplayName(dim.id)),
                    Component.literal("Players: ${dim.players.joinToString(", ")}")
                ), mouseX, mouseY)
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
        return dev.mcrib884.musync.TrackNames.formatPoolName(id)
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
