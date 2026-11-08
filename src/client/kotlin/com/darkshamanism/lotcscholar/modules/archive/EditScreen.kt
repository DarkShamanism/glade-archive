package com.darkshamanism.lotcscholar.modules.archive

import com.darkshamanism.lotcscholar.core.ClientContext
import com.darkshamanism.lotcscholar.ui.components.ParchmentTheme
import com.darkshamanism.lotcscholar.ui.components.drawNoticeBanner
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/**
 * Review and submit screen - shown after pulling a book from the
 * [BookScreenHooks] mixin.
 *
 * Uses [ClientContext] to submit via [ArchiveService].
 */
class EditScreen(
    private val parent: Screen,
    private val initTitle: String,
    private val initAuthor: String,
    rawContent: String,
    private val ctx: ClientContext,
) : com.darkshamanism.lotcscholar.ui.compat.LegacyScreen(Text.literal("Review Submission")) {

    private val cleanContent = rawContent.replace(Regex("§[0-9a-fk-orA-FK-OR]"), "")
    private val contentLines = cleanContent.lines().toMutableList()

    private lateinit var titleField:   TextFieldWidget
    private lateinit var authorField:  TextFieldWidget
    private lateinit var submitButton: ButtonWidget

    private var scrollOffset = 0
    private var panelLeft = 0; private var panelTop = 0
    private var panelW = 0;    private var panelH = 0
    private val lineH = 10
    private var focusedLine = -1
    private var editField: TextFieldWidget? = null

    private var statusMsg   = ""
    private var statusError = false
    private var uploading   = false

    override fun init() {
        val cx = width / 2
        panelW = minOf(440, width - 40)
        panelLeft = cx - panelW / 2

        val titleLabelY   = 22; val titleY    = titleLabelY + 10
        val authorLabelY  = titleY + 22; val authorY  = authorLabelY + 10
        val contentLabelY = authorY + 22
        panelTop = contentLabelY + 10
        panelH   = height - panelTop - 34
        val btnY = panelTop + panelH + 6

        titleField = TextFieldWidget(textRenderer, panelLeft, titleY, panelW, 18, Text.literal("Title")).also {
            it.setMaxLength(200); it.text = initTitle; addDrawableChild(it)
        }
        authorField = TextFieldWidget(textRenderer, panelLeft, authorY, panelW, 18, Text.literal("Author")).also {
            it.setMaxLength(100); it.text = initAuthor; addDrawableChild(it)
        }
        submitButton = ButtonWidget.builder(Text.literal("^ Submit to Archive [U]")) { doUpload() }
            .dimensions(panelLeft, btnY, panelW - 66, 22).build().also { addDrawableChild(it) }
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel")) { close() }
            .dimensions(panelLeft + panelW - 62, btnY, 62, 22).build())
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    override fun mouseScrolled(mx: Double, my: Double, hA: Double, vA: Double): Boolean {
        if (mx >= panelLeft && mx <= panelLeft + panelW && my >= panelTop && my <= panelTop + panelH) {
            scroll(-vA.toInt()); return true
        }
        return super.mouseScrolled(mx, my, hA, vA)
    }

    override fun mouseClicked(mx: Double, my: Double, btn: Int): Boolean {
        if (mx >= panelLeft && mx <= panelLeft + panelW &&
            my >= panelTop + 2 && my <= panelTop + panelH - 2) {
            val li = scrollOffset + ((my - panelTop - 4) / lineH).toInt()
            if (li in contentLines.indices) { focusLine(li); return true }
        } else commitEdit()
        return super.mouseClicked(mx, my, btn)
    }

    override fun keyPressed(kc: Int, sc: Int, mods: Int): Boolean {
        val typingInTextField = isTypingInTextField()

        if (!typingInTextField) {
            if (kc == 264) { scroll(1);  return true }
            if (kc == 265) { scroll(-1); return true }
        }
        if ((kc == 257 || kc == 335) && editField != null) { commitEdit(); return true }
        if (kc == 256 && editField != null) { commitEdit(); return true }
        if (kc == 85 && !typingInTextField) { doUpload(); return true }
        return super.keyPressed(kc, sc, mods)
    }

    private fun isTypingInTextField(): Boolean {
        if (editField != null) return true
        if (::titleField.isInitialized && titleField.isFocused) return true
        if (::authorField.isInitialized && authorField.isFocused) return true
        return focused is TextFieldWidget
    }

    private fun focusLine(li: Int) {
        commitEdit(); focusedLine = li
        val fieldY = panelTop + 4 + (li - scrollOffset) * lineH - 1
        val field  = TextFieldWidget(textRenderer, panelLeft + 2, fieldY, panelW - 12, lineH + 2, Text.literal("")).also {
            it.setMaxLength(500); it.text = contentLines[li]; it.setFocused(true); it.isFocused = true
        }
        editField = field; addDrawableChild(field); setFocused(field)
    }

    private fun commitEdit() {
        val field = editField ?: return
        if (focusedLine in contentLines.indices) contentLines[focusedLine] = field.text
        remove(field); editField = null; focusedLine = -1
    }

    private fun scroll(delta: Int) {
        commitEdit()
        val maxS = maxOf(0, contentLines.size - visibleLines())
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxS)
    }

    private fun visibleLines() = maxOf(1, (panelH - 8) / lineH)

    // ── Render ────────────────────────────────────────────────────────────────

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun render(context: DrawContext, mx: Int, my: Int, delta: Float) {
        val P = ParchmentTheme; val cx = width / 2
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Review & Submit"), cx, 8, P.TEXT_ACCENT)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(P.ORNAMENT), cx, 18, P.TEXT_SUBTITLE)

        val titleLabelY  = 22; val authorLabelY  = titleLabelY + 10 + 22
        val contentLabelY = authorLabelY + 10 + 22
        context.drawTextWithShadow(textRenderer, Text.literal("Title:"),   panelLeft, titleLabelY,   P.TEXT_LIGHT)
        context.drawTextWithShadow(textRenderer, Text.literal("Author:"),  panelLeft, authorLabelY,  P.TEXT_LIGHT)
        context.drawTextWithShadow(textRenderer, Text.literal("Content:  click a line to edit | scroll with wheel"), panelLeft, contentLabelY, P.TEXT_LIGHT)

        context.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, P.BG_INSET)
        context.drawStrokedRectangle(panelLeft, panelTop, panelW, panelH, P.BORDER_GOLD)
        context.enableScissor(panelLeft + 2, panelTop + 2, panelLeft + panelW - 8, panelTop + panelH - 2)

        val visible = visibleLines()
        for (i in 0 until visible) {
            val li = scrollOffset + i; if (li >= contentLines.size) break
            val lineY = panelTop + 4 + i * lineH
            if (li == focusedLine) context.fill(panelLeft + 2, lineY - 1, panelLeft + panelW - 10, lineY + lineH, P.BG_ROW_SEL)
            if (li != focusedLine) context.drawTextWithShadow(textRenderer, Text.literal(contentLines[li]), panelLeft + 4, lineY, P.TEXT_LIGHT)
        }
        context.disableScissor()

        val total = contentLines.size
        if (total > visible) {
            val trackH = panelH - 6; val thumbH = maxOf(10, trackH * visible / total)
            val maxS   = maxOf(1, total - visible)
            val thumbY = panelTop + 3 + (trackH - thumbH) * scrollOffset / maxS
            val sx = panelLeft + panelW - 6
            context.fill(sx, panelTop + 3, sx + 3, panelTop + panelH - 3, P.BORDER_FAINT)
            context.fill(sx, thumbY, sx + 3, thumbY + thumbH, P.BORDER_GOLD)
        }

        context.drawNoticeBanner(textRenderer, statusMsg, statusError, width, panelTop + panelH + 14)
        super.render(context, mx, my, delta)
        if (!ctx.config.current.ui.reducedMotion) drawOpenFadeWash(context)
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    private fun doUpload() {
        commitEdit(); if (uploading) return
        uploading = true; submitButton.active = false; statusMsg = "Uploading..."; statusError = false
        ctx.archive.submitBook(
            title   = titleField.text.trim().ifBlank { "Untitled" },
            author  = authorField.text.trim().ifBlank { "Unknown" },
            content = contentLines.joinToString("\n"),
            world   = "Azuras", island = null,
            onSuccess = { msg ->
                statusMsg = msg; statusError = false; uploading = false
                Thread { Thread.sleep(1800); client?.execute { client?.setScreen(null) } }.start()
            },
            onError = { err ->
                statusMsg = err; statusError = true; uploading = false; submitButton.active = true
            }
        )
    }

    override fun close() { client?.setScreen(parent) }
    override fun shouldPause() = false
}


