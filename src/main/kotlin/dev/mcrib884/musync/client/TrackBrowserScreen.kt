package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.MusicControlPacket
import dev.mcrib884.musync.network.PacketHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.Sound
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent*/
//?}
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.RandomSource
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}

class TrackBrowserScreen : Screen(Component.literal("MuSync - Tracks")) {

    private data class RuntimeTrackPick(
        val eventId: String,
        val specificPath: String,
        val title: String
    )

    private fun trackGroup(key: String): Int {
        if (key.startsWith("custom:")) return 2
        val eventId = key.substringBefore("|")
        return if (eventId.contains("music_disc")) 1 else 0
    }

    private fun trackSortName(displayName: String): String {
        return displayName
            .substringBefore("[")
            .trim()
            .removePrefix("[Custom] ")
            .removePrefix("[Local] ")
            .lowercase()
    }

    private fun sortTrackEntries(items: List<Pair<String, String>>): List<Pair<String, String>> {
        return items.sortedWith(
            compareBy<Pair<String, String>>(
                { trackGroup(it.first) },
                { trackSortName(it.second) },
                { it.first.lowercase() }
            )
        )
    }

    private fun renderedRowCount(visibleTracks: List<Pair<String, String>>): Int {
        return visibleTracks.size.coerceAtMost(visibleRows)
    }

    private fun listHeight(visibleTracks: List<Pair<String, String>>): Int {
        val rows = renderedRowCount(visibleTracks)
        return if (rows > 0) rows * rowH else rowH
    }

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

    private val tracks: List<Pair<String, String>> by lazy {
        val base = buildRuntimeTrackCatalog().toMutableList()
        if (ClientOnlyController.isActive) {
            for (name in ClientOnlyController.getLocalCustomTracks()) {
                val key = "custom:$name"
                if (base.none { it.first == key }) {
                    val displayName = "[Local] " + dev.mcrib884.musync.TrackNames.formatCustomTrackName(name)
                    base.add(key to displayName)
                }
            }
        }
        sortTrackEntries(base)
    }

    private fun normalizeSearchText(value: String): String {
        return value.lowercase()
            .replace('_', ' ')
            .replace('|', ' ')
            .replace(':', ' ')
            .replace('[', ' ')
            .replace(']', ' ')
            .replace("  ", " ")
            .trim()
    }

    private val searchIndexByKey: Map<String, String> by lazy {
        tracks.associate { (key, display) ->
            val eventId = key.substringBefore("|")
            val specific = key.substringAfter("|", "")
            val title = display.substringBefore("[").trim()
            key to normalizeSearchText("$display $key $eventId $specific $title")
        }
    }

    private fun buildRuntimeTrackCatalog(): List<Pair<String, String>> {
        val mc = Minecraft.getInstance()
        val random = RandomSource.create()
        val bestBySpecific = linkedMapOf<String, RuntimeTrackPick>()

        fun eventSpecificity(eventId: String): Int {
            return when {
                eventId.startsWith("minecraft:music.overworld.") -> 100
                eventId.startsWith("minecraft:music.nether.") -> 95
                eventId == "minecraft:music.end" -> 90
                eventId == "minecraft:music.dragon" -> 89
                eventId == "minecraft:music.credits" -> 88
                eventId.startsWith("minecraft:music.under_water") -> 87
                eventId.startsWith("minecraft:music.creative") -> 70
                eventId.startsWith("minecraft:music.menu") -> 60
                eventId.startsWith("minecraft:music.game") -> 50
                eventId.contains("music_disc") -> 40
                else -> 10
            }
        }

        fun collectEventFiles(eventId: ResourceLocation, visited: MutableSet<String>, out: MutableSet<ResourceLocation>) {
            val eventKey = eventId.toString()
            if (!visited.add(eventKey)) return

            val weighed = mc.soundManager.getSoundEvent(eventId) ?: return
            for (weighted in weighed.list) {
                val sound = weighted.getSound(random)
                if (sound.type == Sound.Type.FILE) {
                    out.add(sound.location)
                } else {
                    collectEventFiles(sound.location, visited, out)
                }
            }
        }

        for (event in dev.mcrib884.musync.soundEventKeys()) {
            val eventId = event.toString()
            val path = event.path
            if (!(path.contains("music") || path.startsWith("music_disc."))) continue

            val files = linkedSetOf<ResourceLocation>()
            collectEventFiles(event, mutableSetOf(), files)

            if (files.isEmpty()) continue

            for (file in files.sortedBy { "${it.namespace}:${it.path}" }) {
                val specific = "${file.namespace}:${file.path}"
                val key = "$eventId|$specific"
                val title = dev.mcrib884.musync.TrackNames.formatTrack(key)
                val candidate = RuntimeTrackPick(eventId = eventId, specificPath = specific, title = title)

                val existing = bestBySpecific[specific]
                if (existing == null) {
                    bestBySpecific[specific] = candidate
                } else {
                    val existingScore = eventSpecificity(existing.eventId)
                    val candidateScore = eventSpecificity(candidate.eventId)
                    if (candidateScore > existingScore ||
                        (candidateScore == existingScore && candidate.eventId < existing.eventId)
                    ) {
                        bestBySpecific[specific] = candidate
                    }
                }
            }
        }

        val entries = linkedMapOf<String, String>()
        for (pick in bestBySpecific.values) {
            val key = "${pick.eventId}|${pick.specificPath}"
            entries[key] = "${pick.title} [${pick.specificPath}]"
        }

        dev.mcrib884.musync.client.ClientTrackManager.getServerCustomTrackNames().forEach { name ->
            val key = "custom:$name"
            val display = "[Custom] " + dev.mcrib884.musync.TrackNames.formatCustomTrackName(name)
            entries.putIfAbsent(key, display)
        }

        return sortTrackEntries(entries.map { it.key to it.value })
    }

    private fun filteredTracks(): List<Pair<String, String>> {
        val query = normalizeSearchText(searchField?.value?.trim().orEmpty())
        if (query.isEmpty()) return tracks
        return tracks.filter { (key, displayName) ->
            val indexed = searchIndexByKey[key]
            indexed?.contains(query) == true ||
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

        val searchBoxY = panelY + 23
        val searchTextY = searchBoxY + ((18 - font.lineHeight + 1).coerceAtLeast(0) / 2)
        searchField = EditBox(font, panelX + 10, searchTextY, panelW - 20, 14, Component.literal("Search tracks"))
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
        //? if >=1.21 {
        /*super.renderBackground(graphics, mouseX, mouseY, partialTick)*/
        //?} else {
        renderBackground(graphics)
        //?}

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, theme.frameColor)
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, theme.panelColor)
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, theme.accentColor)

        val cx = panelX + panelW / 2
        val visibleTracks = filteredTracks()

        drawCenteredThemeText(graphics, "\u266B Track Browser \u266B", cx, panelY + 8, theme.accentColor)
        drawThemeText(graphics, "Search", panelX + 8, panelY + 12, theme.statusIdleColor)

        val sfY = panelY + 23
        val sfFocused = searchField?.isFocused == true
        val sfBc = if (sfFocused) theme.accentStrongColor else theme.accentColor
        graphics.fill(panelX + 9, sfY, panelX + panelW - 9, sfY + 18, theme.buttonColor)
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
        drawCenteredThemeText(graphics, countLabel, cx, panelY + 44, theme.statusIdleColor)

        val listY = panelY + 56
        val listH = listHeight(visibleTracks)
        val drawRows = renderedRowCount(visibleTracks)

        graphics.fill(panelX + 4, listY - 2, panelX + panelW - 4, listY + listH + 2, 0x30000000)

        val maxScroll = (visibleTracks.size - visibleRows).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        if (visibleTracks.isEmpty()) {
            drawCenteredThemeText(graphics, "No matching tracks", cx, listY + (listH - font.lineHeight) / 2, theme.statusIdleColor)
        }

        val selectedIndex = currentSelectionIndex(visibleTracks)
        for (i in 0 until drawRows) {
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
                isSelected -> theme.listSelectedColor
                rowHovered -> theme.listHoverColor
                hasTrack && i % 2 == 0 -> theme.listEvenColor
                hasTrack -> theme.listOddColor
                else -> 0x22151726.toInt()
            }
            val rowBorder = if (isSelected) theme.accentColor else if (hasTrack) theme.listBorderColor else theme.progressBorderColor

            graphics.fill(rowLeft, rowTop, rowRight, rowBottom, rowFill)
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + 1, rowBorder)
            graphics.fill(rowLeft, rowBottom - 1, rowRight, rowBottom, rowBorder)
            graphics.fill(rowLeft, rowTop, rowLeft + 1, rowBottom, rowBorder)
            graphics.fill(rowRight - 1, rowTop, rowRight, rowBottom, rowBorder)

            if (!hasTrack) continue

            val (_, displayName) = visibleTracks[idx]
            val textColor = if (isSelected) theme.accentStrongColor else theme.buttonTextColor
            val maxNameW = panelW - 34
            val trimmed = if (font.width(displayName) > maxNameW) {
                font.plainSubstrByWidth(displayName, maxNameW) + "..."
            } else displayName
            val textY = rowTop + ((rowBottom - rowTop) - font.lineHeight) / 2 + 1
            drawThemeText(graphics, trimmed, rowLeft + 6, textY, textColor)
        }

        if (visibleTracks.size > visibleRows) {
            val barX = panelX + panelW - 10
            val barW = 6
            val barTotalH = listH
            val thumbH = ((visibleRows.toFloat() / visibleTracks.size) * barTotalH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (barTotalH - thumbH)).toInt()

            graphics.fill(barX, listY, barX + barW, listY + barTotalH, 0xFF151726.toInt())
            graphics.fill(barX, listY, barX + barW, listY + 1, theme.progressBorderColor)
            graphics.fill(barX, listY + barTotalH - 1, barX + barW, listY + barTotalH, theme.progressBorderColor)
            val thumbColor = if (draggingScrollbar) theme.accentStrongColor else theme.accentColor
            graphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor)
        }

        val hasSelection = selectedIndex >= 0 && selectedIndex < visibleTracks.size
        val canAct = hasSelection && isOp
        backBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h) }
        playBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canAct) }
        queueBounds?.let { b -> drawCustomBtn(graphics, b.x, b.y, b.w, b.h, b.label, mouseX in b.x until b.x + b.w && mouseY in b.y until b.y + b.h, canAct) }

        if (hasSelection) {
            val (_, selDisplay) = visibleTracks[selectedIndex]
            drawCenteredThemeText(graphics, "Selected: $selDisplay", cx, panelY + panelH - 44, theme.accentColor)
        }

        if (!isOp && !ClientOnlyController.isActive) {
            drawCenteredThemeText(graphics, "\u26A0 View only (OP required)", cx, panelY + panelH - 44, 0xFFFF5555.toInt())
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
        val listH = listHeight(visibleTracks)
        val drawRows = renderedRowCount(visibleTracks)

        GuiComponent.fill(poseStack, panelX + 4, listY - 2, panelX + panelW - 4, listY + listH + 2, 0x30000000)

        val maxScroll = (visibleTracks.size - visibleRows).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        if (visibleTracks.isEmpty()) {
            GuiComponent.drawCenteredString(poseStack, font, "No matching tracks", cx, listY + (listH - font.lineHeight) / 2, 0xFF666666.toInt())
        }

        val selectedIndex = currentSelectionIndex(visibleTracks)

        for (i in 0 until drawRows) {
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

    private fun handleMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean? {
        if (button == 0) {
            val visibleTracks = filteredTracks()
            val listY = panelY + 56
            val listH = listHeight(visibleTracks)
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
        if (draggingScrollbar && button == 0) {
            val visibleTracks = filteredTracks()
            val listY = panelY + 56
            val listH = listHeight(visibleTracks)
            val maxScroll = (visibleTracks.size - visibleRows).coerceAtLeast(0)
            val ratio = ((mouseY - listY) / listH).toFloat().coerceIn(0f, 1f)
            scrollOffset = (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
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
        if (event.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(event)
    }*/
    //?} else {
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false
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
        val visibleTracks = filteredTracks()
        val maxScroll = (visibleTracks.size - visibleRows).coerceAtLeast(0)
        scrollOffset = (scrollOffset - delta.toInt()).coerceIn(0, maxScroll)
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
        val bg = when { !active -> theme.buttonDisabledColor; hovered -> theme.buttonHoverColor; else -> theme.buttonColor }
        val borderColor = if (active) theme.accentColor else theme.buttonDisabledBorderColor
        val textColor = if (active) theme.buttonTextColor else theme.buttonDisabledTextColor
        graphics.fill(x, y, x + w, y + h, bg)
        graphics.fill(x, y, x + w, y + 1, borderColor)
        graphics.fill(x, y, x + 1, y + h, borderColor)
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor)
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor)
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor, useTextShadow())
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
