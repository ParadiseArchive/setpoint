package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.client.Handbook.Chapter;
import io.hookdev.setpoint.client.Handbook.Cook;
import io.hookdev.setpoint.client.Handbook.Craft;
import io.hookdev.setpoint.client.Handbook.Element;
import io.hookdev.setpoint.client.Handbook.Heading;
import io.hookdev.setpoint.client.Handbook.ItemRow;
import io.hookdev.setpoint.client.Handbook.Paragraph;
import io.hookdev.setpoint.client.Handbook.Tip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Setpoint Handbook: a field manual with a contents page, chapters, real item icons and crafting grids.
 * The content is laid out into pages here, so editing the text never breaks the layout.
 */
public class HandbookScreen extends Screen {
    private static final int W = 220, H = 196;
    private static final int CX = 16, CW = 188, TOP = 22, BOTTOM = 160; // the paper ends at 167; keep the last line well inside it
    private static final int COVER = 0xFF1E222A, PAPER = 0xFFECE6D6, PAPER_EDGE = 0xFFCFC7B2, TEXT = 0xFF2A2622, HEAD = 0xFF10656B,
            ACCENT = 0xFFC2306A, DIM = 0xFF7A7264, TIP_BG = 0xFFDCEFE8, TIP_BAR = 0xFF2DE2E6, SLOT = 0xFF8B8B8B, SLOT_DARK = 0xFF373737,
            SLOT_LIGHT = 0xFFFFFFFF, LIGHT_TEXT = 0xFFD9DEEA, CYAN = 0xFF2DE2E6;

    /** One element placed on a page, with its wrapped lines for text. */
    private record Placed(Element element, int y, List<FormattedCharSequence> lines) {}

    /** A page: the contents page (chapter -1) or part of a chapter. */
    private record Page(int chapter, List<Placed> placed) {}

    private final Handbook book;
    private final List<Page> pages = new ArrayList<>();
    private final List<Integer> chapterStart = new ArrayList<>();
    private int page;
    private int left, top;
    private Button prev, next, contents;

    public HandbookScreen(Handbook book) {
        super(Component.literal(book.title()));
        this.book = book;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(2, (height - H - 24) / 2); // room for the Done button below
        layout();
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left + 12, top + H - 24, 22, 16).build());
        contents = addRenderableWidget(Button.builder(Component.literal("Contents"), b -> { page = 0; refresh(); })
                .bounds(left + W / 2 - 32, top + H - 24, 64, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + W - 34, top + H - 24, 22, 16).build());
        // close the book, like the Done button under a vanilla book
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + W / 2 - 40, top + H + 4, 80, 20).build());
        refresh();
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages.size() - 1, page + delta));
        refresh();
    }

    private void refresh() {
        prev.active = page > 0;
        next.active = page < pages.size() - 1;
        contents.active = page > 0;
    }

    // ---------- layout ----------
    private int height(Element e, List<FormattedCharSequence> lines) {
        return switch (e) {
            case Heading h -> 14;
            case Paragraph p -> lines.size() * 10 + 4;
            case Tip t -> lines.size() * 10 + 10;
            case ItemRow r -> 22;
            case Craft c -> 60;
            case Cook c -> 34;
        };
    }

    private void layout() {
        pages.clear();
        chapterStart.clear();
        pages.add(new Page(-1, List.of()));
        for (int c = 0; c < book.chapters().size(); c++) {
            chapterStart.add(pages.size());
            List<Placed> current = new ArrayList<>();
            int y = 0;
            for (Element e : book.chapters().get(c).content()) {
                List<FormattedCharSequence> lines = switch (e) {
                    case Paragraph p -> font.split(p.text(), CW);
                    case Tip t -> font.split(t.text(), CW - 12);
                    default -> List.of();
                };
                int h = height(e, lines);
                if (e instanceof Paragraph p && y + h > BOTTOM - TOP && y + 24 <= BOTTOM - TOP) {
                    // a long paragraph flows on to the next page
                    int fit = (BOTTOM - TOP - y - 4) / 10;
                    current.add(new Placed(e, y, lines.subList(0, fit)));
                    pages.add(new Page(c, current));
                    current = new ArrayList<>();
                    lines = lines.subList(fit, lines.size());
                    y = 0;
                    h = lines.size() * 10 + 4;
                } else if (y + h > BOTTOM - TOP || (e instanceof Heading && y + h + 24 > BOTTOM - TOP && y > 0)) {
                    pages.add(new Page(c, current)); // a heading never sits alone at the bottom of a page
                    current = new ArrayList<>();
                    y = 0;
                }
                current.add(new Placed(e, y, lines));
                y += h;
            }
            pages.add(new Page(c, current));
        }
        page = Math.min(page, pages.size() - 1);
    }

    // ---------- drawing ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(left, top, left + W, top + H, COVER);
        g.fill(left + 1, top + 1, left + 4, top + H - 1, 0xFF178A8D);           // spine
        g.fill(left + 8, top + 18, left + W - 8, top + H - 28, PAPER_EDGE);
        g.fill(left + 8, top + 18, left + W - 9, top + H - 29, PAPER);

        Page p = pages.get(page);
        String head = p.chapter < 0 ? book.title() : book.chapters().get(p.chapter).title();
        g.text(font, head, left + 10, top + 6, LIGHT_TEXT, false);
        String counter = (page + 1) + " / " + pages.size();
        g.text(font, counter, left + W - 10 - font.width(counter), top + 6, CYAN, false);

        ItemStack hovered = ItemStack.EMPTY;
        if (p.chapter < 0) {
            hovered = contentsPage(g, mouseX, mouseY);
        } else {
            for (Placed placed : p.placed()) {
                ItemStack h = draw(g, placed, left + CX, top + TOP + placed.y(), mouseX, mouseY);
                if (!h.isEmpty()) hovered = h;
            }
        }
        super.extractRenderState(g, mouseX, mouseY, a);
        if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    private ItemStack contentsPage(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = left + CX, y = top + TOP;
        g.text(font, "Contents", x, y, HEAD, false);
        g.fill(x, y + 10, x + 40, y + 11, ACCENT);
        for (int c = 0; c < book.chapters().size(); c++) {
            Chapter chapter = book.chapters().get(c);
            int ry = y + 16 + c * 15;
            boolean hover = mouseX >= x && mouseX < x + CW && mouseY >= ry - 1 && mouseY < ry + 14;
            if (hover) g.fill(x - 2, ry - 1, x + CW, ry + 14, PAPER_EDGE);
            g.item(chapter.icon(), x, ry - 2);
            g.text(font, chapter.title(), x + 20, ry + 2, hover ? HEAD : TEXT, false);
            String number = String.valueOf(chapterStart.get(c) + 1);
            g.text(font, number, x + CW - 4 - font.width(number), ry + 2, DIM, false);
        }
        return ItemStack.EMPTY;
    }

    /** Draws one element; returns the item under the mouse, if any. */
    private ItemStack draw(GuiGraphicsExtractor g, Placed placed, int x, int y, int mouseX, int mouseY) {
        switch (placed.element()) {
            case Heading h -> {
                g.text(font, h.text(), x, y + 2, HEAD, false);
                g.fill(x, y + 11, x + Math.min(CW, font.width(h.text())), y + 12, ACCENT);
            }
            case Paragraph p -> {
                for (int i = 0; i < placed.lines().size(); i++) g.text(font, placed.lines().get(i), x, y + i * 10, TEXT, false);
            }
            case Tip t -> {
                int h = placed.lines().size() * 10 + 6;
                g.fill(x, y, x + CW, y + h, TIP_BG);
                g.fill(x, y, x + 2, y + h, TIP_BAR);
                for (int i = 0; i < placed.lines().size(); i++) g.text(font, placed.lines().get(i), x + 7, y + 4 + i * 10, TEXT, false);
            }
            case ItemRow r -> {
                ItemStack hovered = ItemStack.EMPTY;
                for (int i = 0; i < r.items().size(); i++) {
                    ItemStack h = slot(g, r.items().get(i), x + i * 20, y + 1, mouseX, mouseY);
                    if (!h.isEmpty()) hovered = h;
                }
                return hovered;
            }
            case Craft c -> {
                int gx = x + (CW - (3 * 18 + 30 + 26)) / 2, gy = y + 2;
                ItemStack hovered = ItemStack.EMPTY;
                for (int i = 0; i < 9; i++) {
                    ItemStack h = slot(g, c.grid().get(i), gx + (i % 3) * 18, gy + (i / 3) * 18, mouseX, mouseY);
                    if (!h.isEmpty()) hovered = h;
                }
                arrow(g, gx + 3 * 18 + 7, gy + 23);
                ItemStack h = slot(g, c.result(), gx + 3 * 18 + 30, gy + 17, mouseX, mouseY);
                return h.isEmpty() ? hovered : h;
            }
            case Cook c -> {
                int gx = x + (CW - (18 + 22 + 18 + 22 + 18)) / 2;
                ItemStack hovered = slot(g, c.input(), gx, y + 2, mouseX, mouseY);
                arrow(g, gx + 21, y + 7);
                g.item(new ItemStack(Items.FURNACE), gx + 41, y + 3);
                arrow(g, gx + 61, y + 7);
                ItemStack h = slot(g, c.result(), gx + 80, y + 2, mouseX, mouseY);
                String note = "furnace " + Math.round(c.seconds()) + " s, blast furnace " + Math.round(c.seconds() / 2) + " s";
                g.text(font, note, x + (CW - font.width(note)) / 2, y + 23, DIM, false);
                return h.isEmpty() ? hovered : h;
            }
        }
        return ItemStack.EMPTY;
    }

    /** A Minecraft-style slot with an item in it; returns the item if the mouse is on it. */
    private ItemStack slot(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        g.fill(x, y, x + 18, y + 18, SLOT);
        g.fill(x, y, x + 17, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + 17, SLOT_DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, SLOT_LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, SLOT_LIGHT);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        g.item(stack, x + 1, y + 1);
        g.itemDecorations(font, stack, x + 1, y + 1);
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18 ? stack : ItemStack.EMPTY;
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 3, x + 12, y + 5, SLOT);
        for (int i = 0; i < 4; i++) g.fill(x + 12 + i, y + i, x + 13 + i, y + 8 - i, SLOT);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (page == 0 && event.button() == 0) {
            int x = left + CX, y = top + TOP;
            for (int c = 0; c < book.chapters().size(); c++) {
                int ry = y + 16 + c * 15;
                if (event.x() >= x && event.x() < x + CW && event.y() >= ry - 1 && event.y() < ry + 14) {
                    page = chapterStart.get(c);
                    refresh();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        turn(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 262 || event.key() == 267) { turn(1); return true; }   // right arrow, page down
        if (event.key() == 263 || event.key() == 266) { turn(-1); return true; }  // left arrow, page up
        return super.keyPressed(event);
    }
}
