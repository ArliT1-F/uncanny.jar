package dev.uncanny.screen;

import dev.uncanny.net.UncannyPayloads;
import dev.uncanny.util.TextUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The reading screen.
 *
 * Deliberately not the vanilla book screen. The vanilla book is a warm piece of
 * paper; this is a dark page with spaced capitals on it, and it should feel like
 * reading something that was not written for you.
 *
 * It is display-only. There is no editing, no signing, and no way to keep a page.
 */
public class UncannyBookScreen extends Screen {

    private static final int PAGE_WIDTH = 220;
    private static final int PAGE_HEIGHT = 160;

    private final UncannyPayloads.Book book;
    private int page;

    public UncannyBookScreen(UncannyPayloads.Book book) {
        super(Text.literal(book.title()));
        this.book = book;
    }

    @Override
    protected void init() {
        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;
        int bottom = top + PAGE_HEIGHT + 4;

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> turn(-1))
                .dimensions(left, bottom, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> turn(1))
                .dimensions(left + PAGE_WIDTH - 40, bottom, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE"), button -> close())
                .dimensions(left + PAGE_WIDTH / 2 - 30, bottom, 60, 20).build());
    }

    private void turn(int delta) {
        this.page = Math.max(0, Math.min(this.book.pages().size() - 1, this.page + delta));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // No background blur and no dirt texture: the world stays visible behind
        // the page, which matters when the page is telling you about the world.
        renderBackground(context);

        int left = (this.width - PAGE_WIDTH) / 2;
        int top = (this.height - PAGE_HEIGHT) / 2;

        // The page itself.
        context.fill(left, top, left + PAGE_WIDTH, top + PAGE_HEIGHT, 0xF0101014);
        context.fill(left, top, left + PAGE_WIDTH, top + 1, 0xFF2A2A33);
        context.fill(left, top + PAGE_HEIGHT - 1, left + PAGE_WIDTH, top + PAGE_HEIGHT, 0xFF2A2A33);
        context.fill(left, top, left + 1, top + PAGE_HEIGHT, 0xFF2A2A33);
        context.fill(left + PAGE_WIDTH - 1, top, left + PAGE_WIDTH, top + PAGE_HEIGHT, 0xFF2A2A33);

        String heading = TextUtil.spaced(this.book.title());
        context.drawCenteredTextWithShadow(this.textRenderer, heading,
                left + PAGE_WIDTH / 2, top + 10, 0xFF7A7A85);

        List<List<String>> pages = this.book.pages();
        if (this.page >= 0 && this.page < pages.size()) {
            List<String> lines = pages.get(this.page);
            int y = top + 34;
            for (String line : lines) {
                if (y > top + PAGE_HEIGHT - 20) {
                    break;
                }
                context.drawTextWithShadow(this.textRenderer, TextUtil.spaced(line),
                        left + 18, y, lineColor(line));
                y += 12;
            }
        }

        String counter = (this.page + 1) + " / " + Math.max(1, pages.size());
        context.drawCenteredTextWithShadow(this.textRenderer, counter,
                left + PAGE_WIDTH / 2, top + PAGE_HEIGHT - 14, 0xFF55555F);

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Colour per line.
     *
     * Almost everything is the same grey. Two kinds of line are not: a status, and
     * a name. The player learns to notice which is which without being told.
     */
    private static int lineColor(String line) {
        String upper = line.toUpperCase();
        if (upper.startsWith("STATUS:") || upper.contains("VACANT")) {
            return 0xFFB0413E;
        }
        if (upper.startsWith("ENTRY") || upper.startsWith("ANCHOR")) {
            return 0xFF9A9AA6;
        }
        return 0xFF77777F;
    }

    @Override
    public boolean shouldPause() {
        // Reading must not pause the world. On a server it cannot pause anything
        // anyway, and a singleplayer pause here would feel like being protected.
        return false;
    }
}
