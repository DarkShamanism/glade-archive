package com.darkshamanism.lotcscholar.integration.minecraft;

import com.darkshamanism.lotcscholar.core.LotcScholarClient;
import com.darkshamanism.lotcscholar.modules.archive.ArchiveBrowserScreen;
import com.darkshamanism.lotcscholar.modules.archive.EditScreen;
import io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen;
import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen;
import io.github.mortuusars.scholar.client.gui.screen.view.SpreadBookViewScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adds LotC Scholar Pull/Upload buttons to Scholar's spread-based book GUI.
 */
@Mixin(SpreadBookScreen.class)
public abstract class ScholarSpreadBookScreenHooks extends Screen {
    @Shadow protected int textColor;

    protected ScholarSpreadBookScreenHooks() { super(Text.empty()); }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void scholar_init(CallbackInfo ci) {
        applyVanillaTextColorFix();

        int btnW = 84;
        int gap = 4;
        int totalW = btnW * 2 + gap;
        int startX = 8;
        int btnY = 8;

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

    private void applyVanillaTextColorFix() {
        if (!(((Object) this) instanceof SpreadBookViewScreen viewScreen)) {
            return;
        }

        try {
            Field pageTextStyle = SpreadBookViewScreen.class.getDeclaredField("pageTextStyle");
            pageTextStyle.setAccessible(true);
            // Use Scholar's configured text color, but disable shadow like vanilla BookScreen.
            pageTextStyle.set(viewScreen, Style.EMPTY.withoutShadow().withColor(this.textColor));

            Field cachedSpread = SpreadBookViewScreen.class.getDeclaredField("cachedSpread");
            cachedSpread.setAccessible(true);
            cachedSpread.setInt(viewScreen, -1);
        } catch (Exception ignored) {
        }
    }

    private void openEditScreen() {
        if (this.client == null) return;
        var ctx = LotcScholarClient.INSTANCE.getContext();
        if (ctx == null) return;

        String title = "Untitled";
        String author = this.client.player != null
                ? this.client.player.getName().getString() : "Unknown";

        ItemStack found = findBook();
        if (found != null) {
            WrittenBookContentComponent written = found.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
            if (written != null) {
                String rawTitle = written.title().raw();
                if (rawTitle != null && !rawTitle.isBlank()) {
                    title = rawTitle.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                }
                if (written.author() != null && !written.author().isBlank()) {
                    author = written.author().replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                }
            } else {
                WritableBookContentComponent writable = found.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                if (writable != null) {
                    String itemName = found.getName().getString();
                    if (itemName != null && !itemName.isBlank()
                            && !itemName.equalsIgnoreCase("Book and Quill")) {
                        title = itemName.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                    }
                }
            }
        }

        List<String> pages = extractPagesFromSpreadEditScreen();
        if (pages.isEmpty()) {
            pages = extractPagesFromSpreadViewScreen();
        }
        if (pages.isEmpty() && found != null) {
            pages = extractPagesFromBookStack(found);
        }
        String content = pages.isEmpty() ? "" : String.join("\n\n", pages);

        this.client.setScreen(new EditScreen((Screen)(Object)this, title, author, content, ctx));
    }

    private void openBrowser() {
        if (this.client == null) return;
        var ctx = LotcScholarClient.INSTANCE.getContext();
        if (ctx == null) return;
        this.client.setScreen(new ArchiveBrowserScreen((Screen)(Object)this, true, ctx));
    }

    private ItemStack findBook() {
        if (this.client == null || this.client.player == null) return null;

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

    private List<String> extractPagesFromSpreadEditScreen() {
        if (!(((Object) this) instanceof SpreadBookEditScreen)) {
            return Collections.emptyList();
        }

        try {
            Class<?> cls = this.getClass();
            while (cls != null && cls != Object.class) {
                try {
                    Field pagesField = cls.getDeclaredField("pages");
                    pagesField.setAccessible(true);
                    Object raw = pagesField.get(this);
                    if (raw instanceof List<?> list) {
                        return convertListToPages(list);
                    }
                } catch (NoSuchFieldException ignored) {
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception ignored) {
        }

        return Collections.emptyList();
    }

    private List<String> extractPagesFromSpreadViewScreen() {
        if (!(((Object) this) instanceof SpreadBookViewScreen viewScreen)) {
            return Collections.emptyList();
        }

        try {
            var access = viewScreen.getBookAccess();
            if (access == null) return Collections.emptyList();

            int pageCount = access.getPageCount();
            if (pageCount <= 0) return Collections.emptyList();

            List<String> pages = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                Text page = access.getPageRaw(i);
                pages.add(page != null ? page.getString() : "");
            }
            trimTrailingEmptyPages(pages);
            return pages;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> extractPagesFromBookStack(ItemStack stack) {
        Object written = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            List<String> pages = extractPagesFromContentComponent(written);
            if (!pages.isEmpty()) return pages;
        }

        Object writable = stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
        if (writable != null) {
            return extractPagesFromContentComponent(writable);
        }

        return Collections.emptyList();
    }

    private List<String> extractPagesFromContentComponent(Object contentComponent) {
        Object rawPages = invokePagesMethod(contentComponent);
        if (!(rawPages instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }

        return convertListToPages(list);
    }

    private Object invokePagesMethod(Object contentComponent) {
        for (String methodName : new String[] {"pages", "getPages"}) {
            try {
                Method m = contentComponent.getClass().getMethod(methodName);
                if (m.getParameterCount() == 0) {
                    return m.invoke(contentComponent);
                }
            } catch (Exception ignored) {
            }
        }

        for (String methodName : new String[] {"pages", "getPages"}) {
            try {
                Method m = contentComponent.getClass().getMethod(methodName, boolean.class);
                if (m.getParameterCount() == 1) {
                    return m.invoke(contentComponent, false);
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private List<String> convertListToPages(List<?> list) {
        List<String> pages = new ArrayList<>(list.size());
        for (Object entry : list) {
            String page = pageText(entry);
            pages.add(page != null ? page : "");
        }
        trimTrailingEmptyPages(pages);
        return pages;
    }

    private void trimTrailingEmptyPages(List<String> pages) {
        while (!pages.isEmpty() && pages.get(pages.size() - 1).isEmpty()) {
            pages.remove(pages.size() - 1);
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
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
