package com.darkshamanism.lotcscholar.modules.archive

import com.darkshamanism.lotcscholar.core.ClientContext
import com.darkshamanism.lotcscholar.core.HasScreen
import com.darkshamanism.lotcscholar.core.ModuleManager
import com.darkshamanism.lotcscholar.integration.minecraft.MinecraftBookFormatter
import com.darkshamanism.lotcscholar.integration.minecraft.WritableBookHelper
import com.darkshamanism.lotcscholar.services.ReadableEntry
import com.darkshamanism.lotcscholar.services.ArchiveBook
import com.darkshamanism.lotcscholar.services.StaticBook
import com.darkshamanism.lotcscholar.services.StaticBooks
import com.darkshamanism.lotcscholar.services.StaticItem
import com.darkshamanism.lotcscholar.services.StaticItems
import com.darkshamanism.lotcscholar.services.StaticSign
import com.darkshamanism.lotcscholar.services.StaticSigns
import com.darkshamanism.lotcscholar.ui.anim.Easing
import com.darkshamanism.lotcscholar.ui.components.*
import com.darkshamanism.lotcscholar.ui.widgets.TabButton
import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.BookEditScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.WritableBookContentComponent
import net.minecraft.item.Items
import net.minecraft.text.RawFilteredPair
import net.minecraft.text.Text
import net.minecraft.util.Hand

/**
 * Archive browser screen - two-pane layout with category + world tab rows.
 *
 * Category row: [Books] [Items] [Signs]
 *
 * World rows (for Books):
 *   Row 0: [Submissions] [Aegis] [Anthos] [Athera] [Vailor]
 *   Row 1: [Atlas] [Axios/Tahn] [Axios/Asul] [Axios/Ceru]
 *
 * World rows (for Items/Signs - no Submissions tab):
 *   Row 0: [Aegis] [Anthos] [Athera] [Vailor]
 *   Row 1: [Atlas] [Axios/Tahn] [Axios/Asul] [Axios/Ceru]
 */
class ArchiveBrowserScreen(
    private val parent: Screen?,
    private val copyIntoBook: Boolean,
    private val ctx: ClientContext,
) : com.darkshamanism.lotcscholar.ui.compat.LegacyScreen(Text.literal("Far Glade Archive")) {

    // ── Category ─────────────────────────────────────────────────────────────
    private enum class Category { BOOKS, ITEMS, SIGNS }
    private val categoryLabels = listOf("Books", "Items", "Signs")
    private var activeCategory = Category.BOOKS

    // ── World tabs ───────────────────────────────────────────────────────────
    private val bookTabLabels = listOf("Submissions") + StaticBooks.WORLDS
    private val worldTabLabels = StaticBooks.WORLDS // Items/Signs: no Submissions
    private val tabLabels: List<String> get() = if (activeCategory == Category.BOOKS) bookTabLabels else worldTabLabels
    private var activeTab = 0
    private val bookTabRow0Count = 5
    private val worldTabRow0Count = 4 // Aegis, Anthos, Athera, Vailor

    // ── Submission (API) state - Books only ──────────────────────────────────
    private val apiBooks  = mutableListOf<ArchiveBook>()
    private var apiTotal  = 0
    private var apiOffset = 0
    private val pageSize get() = ctx.config.current.archive.pageSize
    private var apiLoading = false
    private lateinit var searchField: TextFieldWidget

    // ── Static book state ────────────────────────────────────────────────────
    private lateinit var filterField: TextFieldWidget
    private var staticFiltered = listOf<StaticBook>()

    // ── Static item state ────────────────────────────────────────────────────
    private var itemsFiltered = listOf<StaticItem>()

    // ── Static sign state ────────────────────────────────────────────────────
    private var signsFiltered = listOf<StaticSign>()

    // ── Filter field for items/signs ──────────────────────────────────────────
    private lateinit var filterFieldIS: TextFieldWidget

    // ── Scroll / selection ────────────────────────────────────────────────────
    private var selectedApiIdx    = -1
    private var selectedStaticIdx = -1
    private var contentScroll     = 0
    private var listScroll        = 0

    // ── Status ────────────────────────────────────────────────────────────────
    private var statusMsg = ""
    private var isError   = false

    // ── Layout ────────────────────────────────────────────────────────────────
    private val tabH      = 14
    private val tabRowGap = 2
    private val catH      = 16
    private val lineH     = 10

    private var openedAt: Long = 0L

    private var catBarY  = 0; private var tabBarY = 0; private var bodyTop = 0
    private var listLeft = 0; private var listTop = 0; private var listW = 0; private var listH = 0
    private var detLeft  = 0; private var detTop  = 0; private var detW  = 0; private var detH  = 0

    // ── Init ──────────────────────────────────────────────────────────────────

    override fun init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis()
        val pw  = minOf(920, width - 20)
        val pl  = width / 2 - pw / 2

        // Layout: world tabs -> filter/search -> category tabs -> body
        tabBarY = 26
        val tabRows = 2
        val searchY = tabBarY + tabRows * (tabH + tabRowGap) + 2
        catBarY = searchY + 20
        bodyTop = catBarY + catH + 4

        // Search field (for API / Submissions tab under Books)
        searchField = TextFieldWidget(textRenderer, pl, searchY, pw - 88, 18, Text.literal("Search")).also {
            it.setMaxLength(120)
            it.setPlaceholder(Text.literal("Search titles, authors, content..."))
            addDrawableChild(it)
        }
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Search")) { doApiSearch() }
                .dimensions(pl + pw - 84, searchY, 84, 18).build()
        )

        // Filter field (for static books in world tabs)
        filterField = TextFieldWidget(textRenderer, pl, searchY, pw, 18, Text.literal("Filter")).also {
            it.setMaxLength(80)
            it.setPlaceholder(Text.literal("Filter current world mode..."))
            addDrawableChild(it)
        }

        // Shared filter field for Items/Signs
        filterFieldIS = TextFieldWidget(textRenderer, pl, searchY, pw, 18, Text.literal("Filter")).also {
            it.setMaxLength(80)
            it.setPlaceholder(Text.literal("Filter current world mode..."))
            addDrawableChild(it)
        }

        val bodyH = height - bodyTop - 26
        listLeft = pl;          listTop = bodyTop
        listW    = pw * 2 / 5;  listH   = bodyH
        detLeft  = listLeft + listW + 6; detTop = bodyTop
        detW     = pw - listW - 6;       detH   = bodyH

        val btnY = height - 22
        if (copyIntoBook) {
            addDrawableChild(
                ButtonWidget.builder(Text.literal("\u2726 Copy into Book")) { doCopyIntoBook() }
                    .dimensions(detLeft, btnY, detW / 2 - 2, 18).build()
            )
        }
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Open in Reader")) { openInReader() }
                .dimensions(detLeft + detW - 88, btnY - 22, 86, 16).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Close [Esc]")) { close() }
                .dimensions(
                    if (copyIntoBook) detLeft + detW / 2 + 2 else detLeft, btnY,
                    if (copyIntoBook) detW / 2 - 2 else detW, 18
                ).build()
        )

        updateFieldVisibility()
        if (activeCategory == Category.BOOKS && apiBooks.isEmpty()) loadApiBooks()
        rebuildFilter()
    }

    private fun updateFieldVisibility() {
        searchField.visible = activeCategory == Category.BOOKS && activeTab == 0
        filterField.visible = activeCategory == Category.BOOKS && activeTab != 0
        filterFieldIS.visible = activeCategory != Category.BOOKS
    }

    // ── Tab row geometry (world tabs) ────────────────────────────────────────

    private val tabRow0Count: Int get() = if (activeCategory == Category.BOOKS) bookTabRow0Count else worldTabRow0Count

    private fun tabYForRow(row: Int) = tabBarY + row * (tabH + tabRowGap)
    private fun tabsInRow(row: Int): List<Int> {
        val all = tabLabels.indices.toList()
        return if (row == 0) all.take(tabRow0Count) else all.drop(tabRow0Count)
    }
    private fun tabWidthForRow(row: Int, pw: Int): Int {
        val count = tabsInRow(row).size
        return if (count == 0) 80 else (pw / count).coerceIn(40, 200)
    }

    // ── API (Books → Submissions) ────────────────────────────────────────────

    private fun doApiSearch() {
        apiOffset = 0; apiBooks.clear(); selectedApiIdx = -1; contentScroll = 0; listScroll = 0
        loadApiBooks()
    }

    private fun loadApiBooks() {
        if (apiLoading) return
        apiLoading = true; statusMsg = "Loading\u2026"; isError = false
        ctx.archive.fetchBooks(
            query  = searchField.text.trim(),
            limit  = pageSize,
            offset = apiOffset,
            onSuccess = { fetched, tot ->
                client?.execute {
                    apiBooks.addAll(fetched); apiTotal = tot; apiLoading = false
                    statusMsg = if (apiBooks.isEmpty()) "No submissions found." else ""
                }
            },
            onError = { err -> client?.execute { apiLoading = false; statusMsg = err; isError = true } }
        )
    }

    private fun loadMoreApi() { apiOffset += pageSize; loadApiBooks() }

    // ── Unified filter rebuild ────────────────────────────────────────────────

    private fun rebuildFilter() {
        when (activeCategory) {
            Category.BOOKS -> rebuildBookFilter()
            Category.ITEMS -> rebuildItemFilter()
            Category.SIGNS -> rebuildSignFilter()
        }
    }

    private fun rebuildBookFilter() {
        if (activeTab == 0) return // Submissions use API search
        val world = tabLabels[activeTab]
        val src   = StaticBooks.booksForWorld(world)
        val q     = filterField.text.trim().lowercase()
        staticFiltered = if (q.isBlank()) src
            else src.filter { it.title.lowercase().contains(q) || it.author.lowercase().contains(q) }
        selectedStaticIdx = -1; contentScroll = 0; listScroll = 0
        statusMsg = if (staticFiltered.isEmpty()) "No entries match." else ""
        isError = false
    }

    private fun rebuildItemFilter() {
        val world = tabLabels[activeTab]
        val src   = StaticItems.itemsForWorld(world)
        val q     = filterFieldIS.text.trim().lowercase()
        itemsFiltered = if (q.isBlank()) src
            else src.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) }
        selectedStaticIdx = -1; contentScroll = 0; listScroll = 0
        statusMsg = if (itemsFiltered.isEmpty()) "No entries match." else ""
        isError = false
    }

    private fun rebuildSignFilter() {
        val world = tabLabels[activeTab]
        val src   = StaticSigns.signsForWorld(world)
        val q     = filterFieldIS.text.trim().lowercase()
        signsFiltered = if (q.isBlank()) src
            else src.filter { it.title.lowercase().contains(q) || it.content.lowercase().contains(q) }
        selectedStaticIdx = -1; contentScroll = 0; listScroll = 0
        statusMsg = if (signsFiltered.isEmpty()) "No entries match." else ""
        isError = false
    }

    // ── List count helper ────────────────────────────────────────────────────

    private fun currentListCount(): Int = when (activeCategory) {
        Category.BOOKS -> if (activeTab == 0) apiBooks.size else staticFiltered.size
        Category.ITEMS -> itemsFiltered.size
        Category.SIGNS -> signsFiltered.size
    }

    // ── Copy into book ────────────────────────────────────────────────────────

    private fun doCopyIntoBook() {
        val rawContent: String; val titleForMsg: String
        when (activeCategory) {
            Category.BOOKS -> {
                if (activeTab == 0) {
                    val b = apiBooks.getOrNull(selectedApiIdx) ?: run { setCopyStatus("Select an entry first.", true); return }
                    rawContent = b.content; titleForMsg = b.title
                } else {
                    val b = staticFiltered.getOrNull(selectedStaticIdx) ?: run { setCopyStatus("Select an entry first.", true); return }
                    rawContent = b.content; titleForMsg = b.title
                }
            }
            Category.ITEMS -> {
                val item = itemsFiltered.getOrNull(selectedStaticIdx) ?: run { setCopyStatus("Select an entry first.", true); return }
                rawContent = item.description; titleForMsg = item.title
            }
            Category.SIGNS -> {
                val sign = signsFiltered.getOrNull(selectedStaticIdx) ?: run { setCopyStatus("Select an entry first.", true); return }
                rawContent = sign.content; titleForMsg = sign.title
            }
        }
        val player = client?.player ?: run { setCopyStatus("Join a world before copying into a book.", true); return }
        val hand = when {
            player.mainHandStack.isOf(Items.WRITABLE_BOOK) -> Hand.MAIN_HAND
            player.offHandStack.isOf(Items.WRITABLE_BOOK)  -> Hand.OFF_HAND
            else -> null
        }
        if (hand == null) { setCopyStatus("Hold a Book & Quill to copy.", true); return }
        val clean = rawContent.stripFormatting()
        val formatted = MinecraftBookFormatter.format(clean, textRenderer)
        val err = WritableBookHelper.setHeldPages(
            player = player,
            hand = hand,
            pages = formatted.pages,
            maxPages = MinecraftBookFormatter.MAX_PAGES,
        )
        if (err != null) { setCopyStatus("Copy failed: $err", true) }
        else {
            val truncationNote = if (formatted.truncated) " (first ${MinecraftBookFormatter.MAX_PAGES} pages)" else ""
            setCopyStatus("\u2713 Copied \"${titleForMsg.take(28)}\" into your book$truncationNote!", false)
            reopenWrittenBook(player, hand)
        }
    }

    private fun setCopyStatus(message: String, error: Boolean) {
        statusMsg = message
        isError = error
        client?.player?.sendMessage(Text.literal(message), true)
    }

    private fun reopenWrittenBook(player: net.minecraft.client.network.ClientPlayerEntity, hand: Hand) {
        val stack = if (hand == Hand.MAIN_HAND) player.mainHandStack else player.offHandStack
        val scholarScreen = runCatching { SpreadBookEditScreen(stack, hand) }.getOrNull()
        if (scholarScreen != null) {
            client?.setScreen(scholarScreen)
            return
        }

        val content = stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT)
            ?: WritableBookContentComponent(listOf(RawFilteredPair.of("")))
        client?.setScreen(BookEditScreen(player, stack, hand, content))
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    override fun keyPressed(kc: Int, sc: Int, mods: Int): Boolean {
        if (kc == 257 || kc == 335) { // Enter / numpad Enter
            when (activeCategory) {
                Category.BOOKS -> if (activeTab == 0) doApiSearch() else rebuildFilter()
                else -> rebuildFilter()
            }
            return true
        }
        return super.keyPressed(kc, sc, mods)
    }

    override fun charTyped(chr: Char, mods: Int): Boolean {
        val result = super.charTyped(chr, mods)
        // Live-filter for items/signs as user types
        if (activeCategory != Category.BOOKS || activeTab != 0) {
            rebuildFilter()
        }
        return result
    }

    override fun mouseScrolled(mx: Double, my: Double, hA: Double, vA: Double): Boolean {
        if (mx >= listLeft && mx <= listLeft + listW && my >= listTop && my <= listTop + listH) {
            val count = currentListCount()
            val vis   = maxOf(1, listH / LIST_ROW_H)
            listScroll = (listScroll - vA.toInt()).coerceIn(0, maxOf(0, count - vis))
            return true
        }
        if (mx >= detLeft && mx <= detLeft + detW && my >= detTop && my <= detTop + detH) {
            val lines = currentDetailLines(detW - 22)
            val vis   = maxOf(1, (detH - 52) / lineH)
            contentScroll = (contentScroll - vA.toInt()).coerceIn(0, maxOf(0, lines.size - vis))
            return true
        }
        return super.mouseScrolled(mx, my, hA, vA)
    }

    override fun mouseClicked(mx: Double, my: Double, btn: Int): Boolean {
        if (btn == 0 && copyIntoBook && mx >= detLeft && mx <= detLeft + detW / 2 - 2
            && my >= height - 22 && my <= height - 4) {
            doCopyIntoBook()
            return true
        }
        if (super.mouseClicked(mx, my, btn)) return true

        val pw = minOf(920, width - 20)
        val pl = width / 2 - pw / 2

        // Category bar click
        val catW = pw / 3
        if (my >= catBarY.toDouble() && my <= (catBarY + catH).toDouble()) {
            val catIdx = ((mx - pl) / catW).toInt().coerceIn(0, 2)
            val newCat = Category.entries[catIdx]
            if (newCat != activeCategory) switchCategory(newCat)
            return true
        }

        // World tab rows
        for (row in 0 until 2) {
            val ty = tabYForRow(row).toDouble()
            val tw = tabWidthForRow(row, pw)
            if (my >= ty && my <= ty + tabH) {
                val ids = tabsInRow(row)
                val clickedPos = ((mx - pl) / tw).toInt().coerceIn(0, ids.size - 1)
                val clicked = ids.getOrNull(clickedPos) ?: return true
                if (clicked != activeTab) switchTab(clicked)
                return true
            }
        }

        // List
        if (mx >= listLeft && mx <= listLeft + listW && my >= listTop && my <= listTop + listH) {
            val rel = my - listTop - 2
            val row = (rel / LIST_ROW_H).toInt() + listScroll
            if (activeCategory == Category.BOOKS && activeTab == 0) {
                if (row in apiBooks.indices) { selectedApiIdx = row; contentScroll = 0; return true }
                if (!apiLoading && apiBooks.size < apiTotal) {
                    if ((rel / LIST_ROW_H).toInt() == apiBooks.size - listScroll) { loadMoreApi(); return true }
                }
            } else {
                val count = currentListCount()
                if (row in 0 until count) { selectedStaticIdx = row; contentScroll = 0; return true }
            }
        }
        return false
    }

    private fun switchCategory(cat: Category) {
        activeCategory = cat
        activeTab = if (cat == Category.BOOKS) 1 else 0 // default to first world
        selectedApiIdx = -1; selectedStaticIdx = -1
        contentScroll = 0; listScroll = 0; statusMsg = ""
        updateFieldVisibility()
        rebuildFilter()
    }

    private fun switchTab(idx: Int) {
        activeTab = idx; updateFieldVisibility()
        contentScroll = 0; listScroll = 0; statusMsg = ""
        selectedApiIdx = -1; selectedStaticIdx = -1
        rebuildFilter()
    }

    // ── Detail content ────────────────────────────────────────────────────────

    private fun currentDetailLines(maxWidthPx: Int): List<String> {
        val raw = when (activeCategory) {
            Category.BOOKS -> {
                if (activeTab == 0) apiBooks.getOrNull(selectedApiIdx)?.content
                else staticFiltered.getOrNull(selectedStaticIdx)?.content
            }
            Category.ITEMS -> itemsFiltered.getOrNull(selectedStaticIdx)?.description
            Category.SIGNS -> signsFiltered.getOrNull(selectedStaticIdx)?.content
        }
        val width = maxOf(20, maxWidthPx)
        val out = mutableListOf<String>()
        for (paragraph in (raw ?: "").stripFormatting().lines()) {
            if (paragraph.isBlank()) { out += ""; continue }
            out += textRenderer.wrapPlain(paragraph, width)
        }
        return out
    }

    // ── Selected entry helpers ────────────────────────────────────────────────

    private fun selectedTitle(): String? = when (activeCategory) {
        Category.BOOKS -> if (activeTab == 0) apiBooks.getOrNull(selectedApiIdx)?.title
                          else staticFiltered.getOrNull(selectedStaticIdx)?.title
        Category.ITEMS -> itemsFiltered.getOrNull(selectedStaticIdx)?.title
        Category.SIGNS -> signsFiltered.getOrNull(selectedStaticIdx)?.title
    }

    private fun selectedSubtitle(): String = when (activeCategory) {
        Category.BOOKS -> {
            if (activeTab == 0) apiBooks.getOrNull(selectedApiIdx)?.author ?: ""
            else staticFiltered.getOrNull(selectedStaticIdx)?.author ?: ""
        }
        Category.ITEMS -> itemsFiltered.getOrNull(selectedStaticIdx)?.itemId ?: ""
        Category.SIGNS -> "" // signs have no author
    }

    private fun selectedWorld(): String = when (activeCategory) {
        Category.BOOKS -> {
            if (activeTab == 0) apiBooks.getOrNull(selectedApiIdx)?.world ?: ""
            else if (activeTab in tabLabels.indices) tabLabels[activeTab] else ""
        }
        else -> if (activeTab in tabLabels.indices) tabLabels[activeTab] else ""
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun renderBackground(dc: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun render(dc: DrawContext, mx: Int, my: Int, delta: Float) {
        val P  = ParchmentTheme
        val pw = minOf(920, width - 20)
        val pl = width / 2 - pw / 2

        val hdr = if (copyIntoBook) "The Ancestral Library  \u2014  Pull from Archive"
                  else              "The Ancestral Library  \u2014  Archive Reader"
        dc.drawCenteredTextWithShadow(textRenderer, Text.literal(hdr), width / 2, 8, P.TEXT_ACCENT)
        dc.drawCenteredTextWithShadow(textRenderer, Text.literal(P.ORNAMENT), width / 2, 18, P.TEXT_SUBTITLE)

        // World tab rows
        for (row in 0 until 2) {
            val ty  = tabYForRow(row)
            val tw  = tabWidthForRow(row, pw)
            tabsInRow(row).forEachIndexed { pos, tabIdx ->
                val tx  = pl + pos * tw
                val sel = tabIdx == activeTab
                val hov = mx >= tx && mx < tx + tw && my >= ty && my <= ty + tabH
                TabButton.render(dc, textRenderer, tx, ty, tw - 1, tabH, tabLabels[tabIdx], sel, hov)
            }
        }

        // Category bar (Books / Items / Signs)
        val catW = pw / 3
        for (ci in 0 until 3) {
            val cx = pl + ci * catW
            val sel = ci == activeCategory.ordinal
            val hov = mx >= cx && mx < cx + catW && my >= catBarY && my <= catBarY + catH
            TabButton.render(dc, textRenderer, cx, catBarY, catW - 1, catH, categoryLabels[ci], sel, hov)
        }

        // List panel
        dc.fill(listLeft, listTop, listLeft + listW, listTop + listH, P.BG_INSET)
        dc.drawStrokedRectangle(listLeft, listTop, listW, listH, P.BORDER_GOLD)
        dc.enableScissor(listLeft + 1, listTop + 1, listLeft + listW - 1, listTop + listH - 1)

        val listCount = currentListCount()
        val visRows   = maxOf(1, listH / LIST_ROW_H)
        listScroll    = listScroll.coerceIn(0, maxOf(0, listCount - visRows))

        when (activeCategory) {
            Category.BOOKS -> {
                if (activeTab == 0) {
                    // API books
                    for (vi in 0 until visRows) {
                        val i = vi + listScroll; if (i >= apiBooks.size) break
                        val b = apiBooks[i]
                        val rowY = listTop + 2 + vi * LIST_ROW_H
                        val hov  = mx >= listLeft && mx <= listLeft + listW && my >= rowY && my < rowY + LIST_ROW_H
                        dc.drawListRow(textRenderer, listLeft, rowY, listW, b.title.stripFormatting(), b.author.stripFormatting(), i == selectedApiIdx, hov)
                    }
                    if (apiBooks.size < apiTotal && !apiLoading) {
                        val vi = apiBooks.size - listScroll
                        if (vi in 0..visRows) {
                            val moreY = listTop + 2 + vi * LIST_ROW_H
                            val hov   = mx >= listLeft && mx <= listLeft + listW && my >= moreY && my < moreY + LIST_ROW_H
                            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("\u25BC Load more\u2026"),
                                listLeft + listW / 2, moreY + 7, if (hov) P.TEXT_ACCENT else P.TEXT_LIGHT)
                        }
                    }
                } else {
                    // Static books
                    for (vi in 0 until visRows) {
                        val i = vi + listScroll; if (i >= staticFiltered.size) break
                        val b = staticFiltered[i]
                        val rowY = listTop + 2 + vi * LIST_ROW_H
                        val hov  = mx >= listLeft && mx <= listLeft + listW && my >= rowY && my < rowY + LIST_ROW_H
                        dc.drawListRow(textRenderer, listLeft, rowY, listW, b.title, b.author, i == selectedStaticIdx, hov)
                    }
                }
            }
            Category.ITEMS -> {
                for (vi in 0 until visRows) {
                    val i = vi + listScroll; if (i >= itemsFiltered.size) break
                    val item = itemsFiltered[i]
                    val rowY = listTop + 2 + vi * LIST_ROW_H
                    val hov  = mx >= listLeft && mx <= listLeft + listW && my >= rowY && my < rowY + LIST_ROW_H
                    val subtitle = item.itemId.removePrefix("minecraft:")
                    dc.drawListRow(textRenderer, listLeft, rowY, listW, item.title, subtitle, i == selectedStaticIdx, hov)
                }
            }
            Category.SIGNS -> {
                for (vi in 0 until visRows) {
                    val i = vi + listScroll; if (i >= signsFiltered.size) break
                    val sign = signsFiltered[i]
                    val rowY = listTop + 2 + vi * LIST_ROW_H
                    val hov  = mx >= listLeft && mx <= listLeft + listW && my >= rowY && my < rowY + LIST_ROW_H
                    // For signs, show the title, and a brief content preview as subtitle
                    val preview = sign.content.replace('\n', ' ').take(40)
                    dc.drawListRow(textRenderer, listLeft, rowY, listW, sign.title, preview, i == selectedStaticIdx, hov)
                }
            }
        }

        dc.drawScrollbar(listLeft, listTop, listW, listH, listCount, visRows, listScroll)
        dc.disableScissor()

        // Detail panel
        dc.fill(detLeft, detTop, detLeft + detW, detTop + detH, P.BG_PANEL)
        dc.drawStrokedRectangle(detLeft, detTop, detW, detH, P.BORDER_GOLD)

        val titleStr  = selectedTitle()
        val authorStr = selectedSubtitle()
        val worldStr  = selectedWorld()

        if (titleStr == null) {
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal("Select an entry from the list"),
                detLeft + detW / 2, detTop + detH / 2, P.TEXT_MUTED)
        } else {
            var dy = detTop + 6
            dc.drawCenteredTextWithShadow(textRenderer, Text.literal(titleStr.stripFormatting()), detLeft + detW / 2, dy, P.TEXT_PRIMARY)
            dy += 11
            if (authorStr.isNotBlank()) {
                val prefix = if (activeCategory == Category.ITEMS) "" else "by "
                dc.drawCenteredTextWithShadow(textRenderer, Text.literal("$prefix${authorStr.stripFormatting()}"), detLeft + detW / 2, dy, P.TEXT_MUTED)
                dy += 10
            }
            if (worldStr.isNotBlank()) { dc.drawCenteredTextWithShadow(textRenderer, Text.literal(worldStr), detLeft + detW / 2, dy, P.TEXT_MUTED); dy += 10 }
            dc.fill(detLeft + 8, dy + 2, detLeft + detW - 8, dy + 3, P.BORDER_MUTED); dy += 7

            val lines   = currentDetailLines(detW - 22)
            val contTop = dy; val contH = detH - (contTop - detTop) - 4
            val visL    = maxOf(1, contH / lineH)
            val maxS    = maxOf(0, lines.size - visL)
            contentScroll = contentScroll.coerceIn(0, maxS)

            dc.enableScissor(detLeft + 4, contTop, detLeft + detW - 10, contTop + contH)
            for (i in 0 until visL) {
                val li = contentScroll + i; if (li >= lines.size) break
                dc.drawText(textRenderer, Text.literal(lines[li]), detLeft + 8, contTop + i * lineH, P.TEXT_PRIMARY, false)
            }
            dc.disableScissor()
            dc.drawScrollbar(detLeft, contTop - 1, detW, contH + 2, lines.size, visL, contentScroll)
        }

        super.render(dc, mx, my, delta)
        dc.drawNoticeBanner(textRenderer, statusMsg, isError, width, (height - 58).coerceAtLeast(bodyTop + 6))

        if (!ctx.config.current.ui.reducedMotion) {
            val a = Easing.easeOutCubic(Easing.progress(openedAt, 180L))
            if (a < 1f) {
                val washAlpha = ((1f - a) * 255f).toInt().coerceIn(0, 255)
                if (washAlpha > 0) dc.fill(0, 0, width, height, washAlpha shl 24)
            }
        }
    }

    private fun openInReader() {
        val title = selectedTitle()
        if (title == null) { statusMsg = "Select an entry first."; return }

        val entry: ReadableEntry = when (activeCategory) {
            Category.BOOKS -> {
                val b = apiBooks.getOrNull(selectedApiIdx)
                val s = staticFiltered.getOrNull(selectedStaticIdx)
                when {
                    activeTab == 0 && b != null ->
                        ReadableEntry(b.id, b.title, b.author, b.world, b.content, "archive")
                    activeTab != 0 && s != null ->
                        ReadableEntry("static:${s.title}:${s.author}", s.title, s.author,
                            if (activeTab < tabLabels.size) tabLabels[activeTab] else "", s.content, "static")
                    else -> { statusMsg = "Select an entry first."; return }
                }
            }
            Category.ITEMS -> {
                val item = itemsFiltered.getOrNull(selectedStaticIdx)
                    ?: run { statusMsg = "Select an entry first."; return }
                ReadableEntry("item:${item.title}:${item.itemId}", item.title, item.itemId,
                    if (activeTab < tabLabels.size) tabLabels[activeTab] else "", item.description, "static")
            }
            Category.SIGNS -> {
                val sign = signsFiltered.getOrNull(selectedStaticIdx)
                    ?: run { statusMsg = "Select an entry first."; return }
                ReadableEntry("sign:${sign.title}", sign.title, "",
                    if (activeTab < tabLabels.size) tabLabels[activeTab] else "", sign.content, "static")
            }
        }
        ctx.readerHistory.record(entry, page = 0)
        val reader = ModuleManager.byId("reader") as? HasScreen
        if (reader == null) {
            statusMsg = "Reader module is not installed."
            isError = true
            return
        }
        reader.openScreen(this)
    }

    override fun close() { client?.setScreen(parent) }
    override fun shouldPause() = false
}
