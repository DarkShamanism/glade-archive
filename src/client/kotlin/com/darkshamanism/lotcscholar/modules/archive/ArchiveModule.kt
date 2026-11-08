package com.darkshamanism.lotcscholar.modules.archive

import com.darkshamanism.lotcscholar.core.*
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.item.Items
import org.lwjgl.glfw.GLFW

/**
 * Archive module — browse and submit books to the Far Glade archive.
 *
 * Keybinds registered:
 *   B (default) — open archive browser when holding a Book & Quill in-world
 *   F8          — open archive reader (view-only, anywhere)
 */
class ArchiveModule : GladeModuleProvider, TickableModule, HasScreen, HasKeybinds, HasSettings {

    override val id       = "archive"
    override val title    = "Archive"
    override val category = ModuleCategory.ARCHIVE

    private lateinit var ctx: ClientContext

    // keybind handles (set in registerKeybinds)
    private var pullKey:   net.minecraft.client.option.KeyBinding? = null
    private var readerKey: net.minecraft.client.option.KeyBinding? = null

    override fun register(context: ClientContext) {
        ctx = context
        ArchiveStaticProviders.register()
    }

    override fun registerKeybinds(context: ClientContext) {
        pullKey = context.keybinds.register(
            "key.lotcscholar.archive.pull",
            GLFW.GLFW_KEY_B,
        )
        readerKey = context.keybinds.register(
            "key.lotcscholar.archive.reader",
            GLFW.GLFW_KEY_F8,
        )
    }

    /** Called each client tick by [LotcScholarClient]. */
    override fun handleTick(client: MinecraftClient) {
        while (pullKey?.wasPressed() == true) {
            if (client.currentScreen == null) {
                val player = client.player
                val hasWritable = player?.mainHandStack?.isOf(Items.WRITABLE_BOOK) == true ||
                                  player?.offHandStack?.isOf(Items.WRITABLE_BOOK)  == true
                if (hasWritable) {
                    client.setScreen(ArchiveBrowserScreen(null, copyIntoBook = true, ctx = ctx))
                }
            }
        }
        while (readerKey?.wasPressed() == true) {
            val cur = client.currentScreen
            if (cur !is ArchiveBrowserScreen) {
                client.setScreen(ArchiveBrowserScreen(cur, copyIntoBook = false, ctx = ctx))
            }
        }
    }

    override fun openScreen(parent: Screen?) {
        MinecraftClient.getInstance().setScreen(
            ArchiveBrowserScreen(parent, copyIntoBook = false, ctx = ctx)
        )
    }

    // ── HasSettings ───────────────────────────────────────────────────────────

    override val settingsSectionTitle = "Archive"

    override fun buildSettingsRows(context: ClientContext): List<SettingsRow> = listOf(
        SettingsRow(
            label  = "Archive API URL",
            description = "Far Glade archive endpoint for browsing and submission",
            widget = SettingsWidget.TextField(
                get         = { context.config.current.archive.apiUrl },
                set         = { v -> context.config.update { archive.apiUrl = v } },
                placeholder = "https://farglade.com/Lore/api.php",
            ),
        ),
        SettingsRow(
            label  = "Archive Email",
            description = "Shown as the submitter when you upload a book",
            widget = SettingsWidget.TextField(
                get         = { context.config.current.archive.email },
                set         = { v -> context.config.update { archive.email = v } },
                placeholder = "your@email.com",
            ),
        ),
        SettingsRow(
            label  = "Archive Password",
            description = "Reserved for archive-side auth flows if they are restored later",
            widget = SettingsWidget.TextField(
                get         = { context.config.current.archive.password },
                set         = { v -> context.config.update { archive.password = v } },
            placeholder = "********",
            ),
        ),
        SettingsRow(
            label  = "Page size",
            description = "Results per search page",
            widget = SettingsWidget.TextField(
                get         = { context.config.current.archive.pageSize.toString() },
                set         = { v -> v.toIntOrNull()?.let { context.config.update { archive.pageSize = it } } },
                placeholder = "20",
            ),
        ),
        SettingsRow(
            label  = "Show previews",
            widget = SettingsWidget.Toggle(
                get = { context.config.current.archive.showPreviews },
                set = { v -> context.config.update { archive.showPreviews = v } },
            ),
        ),
        SettingsRow(
            label  = "Auto-open on copy",
            widget = SettingsWidget.Toggle(
                get = { context.config.current.archive.autoOpenOnCopy },
                set = { v -> context.config.update { archive.autoOpenOnCopy = v } },
            ),
        ),
    )
}
