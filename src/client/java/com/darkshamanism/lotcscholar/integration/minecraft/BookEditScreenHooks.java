package com.darkshamanism.lotcscholar.integration.minecraft;

import com.darkshamanism.lotcscholar.core.LotcScholarClient;
import com.darkshamanism.lotcscholar.modules.archive.ArchiveBrowserScreen;
import com.darkshamanism.lotcscholar.modules.archive.EditScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adds Upload/Pull entry points to the Book & Quill edit screen so users can
 * submit or pull archive content while writing.
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenHooks extends Screen {

    protected BookEditScreenHooks() { super(Text.empty()); }

    @Inject(method = "init", at = @At("TAIL"))
    private void scholar_init(CallbackInfo ci) {
        int btnW   = 84;
        int gap    = 4;
        int totalW = btnW * 2 + gap;
        int startX = 8;
        int btnY   = 8;

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

    private void openEditScreen() {
        if (this.client == null) return;
        var ctx = LotcScholarClient.INSTANCE.getContext();
        if (ctx == null) return;

        ItemStack found = findWritableBook();

        String title = "Untitled";
        if (found != null) {
            String itemName = found.getName().getString();
            if (itemName != null && !itemName.isBlank() && !itemName.equalsIgnoreCase("Book and Quill")) {
                title = itemName.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
            }
        }

        String author = this.client.player != null
            ? this.client.player.getName().getString()
            : "Unknown";

        String content = "";
        List<String> pages = extractPagesFromEditScreen();
        if (!pages.isEmpty()) {
            content = String.join("\n\n", pages);
        } else if (found != null) {
            List<String> stackPages = extractPagesFromWritableStack(found);
            if (!stackPages.isEmpty()) content = String.join("\n\n", stackPages);
        }

        this.client.setScreen(new EditScreen((Screen)(Object)this, title, author, content, ctx));
    }

    private void openBrowser() {
        if (this.client == null) return;
        var ctx = LotcScholarClient.INSTANCE.getContext();
        if (ctx == null) return;
        this.client.setScreen(new ArchiveBrowserScreen((Screen)(Object)this, true, ctx));
    }

    private ItemStack findWritableBook() {
        if (this.client == null || this.client.player == null) return null;

        ItemStack main = this.client.player.getMainHandStack();
        if (main.isOf(Items.WRITABLE_BOOK)) return main;

        ItemStack off = this.client.player.getOffHandStack();
        if (off.isOf(Items.WRITABLE_BOOK)) return off;

        for (int i = 0; i < this.client.player.getInventory().size(); i++) {
            ItemStack s = this.client.player.getInventory().getStack(i);
            if (s.isOf(Items.WRITABLE_BOOK)) return s;
        }
        return null;
    }

    private List<String> extractPagesFromEditScreen() {
        try {
            Class<?> cls = this.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (!List.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object raw = f.get(this);
                    if (!(raw instanceof List<?> list) || list.isEmpty()) continue;

                    List<String> pages = new ArrayList<>(list.size());
                    boolean convertible = true;
                    for (Object entry : list) {
                        String page = pageText(entry);
                        if (page == null) {
                            convertible = false;
                            break;
                        }
                        pages.add(page);
                    }
                    if (convertible) return pages;
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    private List<String> extractPagesFromWritableStack(ItemStack stack) {
        try {
            WritableBookContentComponent writable = stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
            if (writable == null) return Collections.emptyList();

            Method pagesMethod = writable.getClass().getMethod("pages");
            Object rawPages = pagesMethod.invoke(writable);
            if (!(rawPages instanceof List<?> list) || list.isEmpty()) return Collections.emptyList();

            List<String> pages = new ArrayList<>(list.size());
            for (Object entry : list) {
                String page = pageText(entry);
                pages.add(page != null ? page : "");
            }
            return pages;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String pageText(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        if (value instanceof Text t) return t.getString();

        for (String methodName : new String[] {"raw", "getRaw", "string", "asString", "getString"}) {
            try {
                Method m = value.getClass().getMethod(methodName);
                if (m.getParameterCount() != 0) continue;
                Object result = m.invoke(value);
                if (result instanceof String s) return s;
                if (result instanceof Text t) return t.getString();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
