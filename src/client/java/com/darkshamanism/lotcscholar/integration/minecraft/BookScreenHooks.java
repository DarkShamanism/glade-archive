package com.darkshamanism.lotcscholar.integration.minecraft;

import com.darkshamanism.lotcscholar.core.LotcScholarClient;
import com.darkshamanism.lotcscholar.modules.archive.ArchiveBrowserScreen;
import com.darkshamanism.lotcscholar.modules.archive.EditScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Version-sensitive hook isolated in integration/minecraft/.
 *
 * Injects two buttons into the vanilla BookScreen:
 *   [Upload] - opens the Edit/Submit screen
 *   [Pull]   - opens the Archive Browser in copy-into-book mode
 *
 * The ClientContext is fetched from [LotcScholarClient.context] so this class
 * has no direct dependency on service singletons; if MC internals change, only
 * this file needs to be updated.
 */
@Mixin(BookScreen.class)
public abstract class BookScreenHooks extends Screen {

    @Shadow private BookScreen.Contents contents;

    protected BookScreenHooks() { super(Text.empty()); }

    @Inject(method = "init", at = @At("TAIL"))
    private void scholar_init(CallbackInfo ci) {
        int btnW     = 84;
        int gap      = 4;
        int totalW   = btnW * 2 + gap;
        int startX   = 8;
        int btnY     = 8;

        if (startX + totalW > this.width - 8) {
            startX = Math.max(4, this.width - totalW - 4);
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2b06 Upload"), btn -> openEditScreen())
            .dimensions(startX, btnY, btnW, 18).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2b07 Pull"), btn -> openBrowser())
            .dimensions(startX + btnW + gap, btnY, btnW, 18).build());
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void openEditScreen() {
        if (this.client == null || this.contents == null) return;
        var ctx = LotcScholarClient.INSTANCE.getContext();
        if (ctx == null) return;

        StringBuilder sb = new StringBuilder();
        int pages = this.contents.getPageCount();
        for (int i = 0; i < pages; i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(this.contents.getPage(i).getString());
        }

        String title  = "Untitled";
        String author = this.client.player != null
            ? this.client.player.getName().getString() : "Unknown";

        if (this.client.player != null) {
            ItemStack found = findBook();
            if (found != null) {
                WrittenBookContentComponent written = found.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                if (written != null) {
                    String rawTitle = written.title().raw();
                    if (rawTitle != null && !rawTitle.isBlank())
                        title = rawTitle.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                    if (written.author() != null && !written.author().isBlank())
                        author = written.author().replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                } else {
                    WritableBookContentComponent writable = found.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                    if (writable != null) {
                        String itemName = found.getName().getString();
                        if (itemName != null && !itemName.isBlank()
                                && !itemName.equalsIgnoreCase("Book and Quill"))
                            title = itemName.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                    }
                }
            }
        }
        this.client.setScreen(new EditScreen((Screen)(Object)this, title, author, sb.toString(), ctx));
    }

    private void openBrowser() {
        if (this.client == null) return;
        var ctx = LotcScholarClient.INSTANCE.getContext();
        if (ctx == null) return;
        this.client.setScreen(new ArchiveBrowserScreen((Screen)(Object)this, true, ctx));
    }

    private ItemStack findBook() {
        if (this.client.player == null) return null;
        ItemStack main = this.client.player.getMainHandStack();
        if (main.isOf(Items.WRITTEN_BOOK) || main.isOf(Items.WRITABLE_BOOK)) return main;
        ItemStack off = this.client.player.getOffHandStack();
        if (off.isOf(Items.WRITTEN_BOOK) || off.isOf(Items.WRITABLE_BOOK)) return off;
        for (int i = 0; i < this.client.player.getInventory().size(); i++) {
            ItemStack s = this.client.player.getInventory().getStack(i);
            if (s.isOf(Items.WRITTEN_BOOK) || s.isOf(Items.WRITABLE_BOOK)) return s;
        }
        return null;
    }
}
