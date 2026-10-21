package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.network.PidRename;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A P&ID: the name at the top (type to rename it), the drawing below on blueprint paper. Pipes run between the
 * symbols, dashed amber lines are signal wiring from controllers. Hover a symbol to see its tag.
 */
public class PidScreen extends Screen {
    private static final int MAX_W = 400, MAX_H = 270, PAPER_X = 8, PAPER_Y = 32;
    private static final int FRAME = 0xFF3A404C, PANEL = 0xFF1E222A, BLUEPRINT = 0xFF10294D, GRID = 0xFF16345F,
            LINE = 0xFFE8F1FF, ACCENT = 0xFF2DE2E6, SIGNAL = 0xFFFFB547, DIM = 0xFF7C869D;

    private final PidDrawing drawing;
    private final InteractionHand hand;
    private EditBox name;
    private int W, H, paperW, paperH;
    private int left, top, cell, scale, originX, originY;

    public PidScreen(PidDrawing drawing, InteractionHand hand) {
        super(Component.literal(drawing.name()));
        this.drawing = drawing;
        this.hand = hand;
    }

    @Override
    protected void init() {
        W = Math.min(MAX_W, width - 16);
        H = Math.min(MAX_H, height - 16);
        paperW = W - 2 * PAPER_X;
        paperH = H - PAPER_Y - 26;
        left = (width - W) / 2;
        top = (height - H) / 2;
        name = new EditBox(font, left + 8, top + 8, 214, 18, Component.literal("P&ID name"));
        name.setMaxLength(PidDrawing.MAX_NAME);
        name.setValue(drawing.name().equals(PidDrawing.UNTITLED) ? "" : drawing.name());
        name.setHint(Component.literal("Name this P&ID"));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + W - 70, top + 7, 62, 20).build());
        setInitialFocus(name);

        // each block gets a cell; the symbol sits inside it with room around, so pipe runs show between symbols
        cell = Math.max(8, Math.min(24, Math.min(paperW / Math.max(1, drawing.width()), paperH / Math.max(1, drawing.height()))));
        int padding = Math.max(2, cell / 4);
        scale = Math.max(1, (cell - padding) / 8);
        originX = left + PAPER_X + (paperW - drawing.width() * cell) / 2;
        originY = top + PAPER_Y + (paperH - drawing.height() * cell) / 2;
    }

    /** Send the new name when the screen closes, however it closes. */
    @Override
    public void removed() {
        super.removed();
        String typed = name.getValue();
        String current = drawing.name().equals(PidDrawing.UNTITLED) ? "" : drawing.name();
        if (!typed.strip().equals(current)) {
            ClientPacketDistributor.sendToServer(new PidRename(hand == InteractionHand.OFF_HAND ? 1 : 0, typed));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(left, top, left + W, top + H, FRAME);
        g.fill(left + 2, top + 2, left + W - 2, top + H - 2, PANEL);
        int px = left + PAPER_X, py = top + PAPER_Y;
        g.fill(px - 1, py - 1, px + paperW + 1, py + paperH + 1, 0xFF000000);
        g.fill(px, py, px + paperW, py + paperH, BLUEPRINT);
        // the paper's grid, one line per cell
        for (int x = originX; x <= originX + drawing.width() * cell; x += cell) g.fill(x, originY, x + 1, originY + drawing.height() * cell, GRID);
        for (int y = originY; y <= originY + drawing.height() * cell; y += cell) g.fill(originX, y, originX + drawing.width() * cell, y + 1, GRID);

        int thick = Math.max(1, scale);
        // pipes first, then the symbols on top, then the signal lines
        for (PidDrawing.Cell c : drawing.cells()) {
            int cx = originX + c.x() * cell + cell / 2, cy = originY + c.y() * cell + cell / 2;
            int x0 = originX + c.x() * cell, y0 = originY + c.y() * cell;
            if ((c.pipes() & PidDrawing.NORTH) != 0) g.fill(cx - thick / 2, y0, cx - thick / 2 + thick, cy + 1, LINE);
            if ((c.pipes() & PidDrawing.SOUTH) != 0) g.fill(cx - thick / 2, cy, cx - thick / 2 + thick, y0 + cell, LINE);
            if ((c.pipes() & PidDrawing.WEST) != 0) g.fill(x0, cy - thick / 2, cx + 1, cy - thick / 2 + thick, LINE);
            if ((c.pipes() & PidDrawing.EAST) != 0) g.fill(cx, cy - thick / 2, x0 + cell, cy - thick / 2 + thick, LINE);
            boolean riser = (c.pipes() & (PidDrawing.UP | PidDrawing.DOWN)) != 0;
            if (c.symbol() == PidDrawing.PIPE && riser) { // a pipe going up or down: a small ring
                g.fill(cx - 2, cy - 2, cx + 3, cy + 3, LINE);
                g.fill(cx - 1, cy - 1, cx + 2, cy + 2, BLUEPRINT);
            }
        }
        for (PidDrawing.Cell c : drawing.cells()) {
            if (c.symbol() == PidDrawing.PIPE) continue;
            int size = 8 * scale;
            int sx = originX + c.x() * cell + (cell - size) / 2, sy = originY + c.y() * cell + (cell - size) / 2;
            g.fill(sx, sy, sx + size, sy + size, BLUEPRINT);
            PidSymbols.draw(g, c.symbol(), c.rotation(), sx, sy, scale, LINE, ACCENT);
        }
        for (PidDrawing.Link link : drawing.links()) {
            if (link.from() >= drawing.cells().size() || link.to() >= drawing.cells().size()) continue;
            PidDrawing.Cell from = drawing.cells().get(link.from()), to = drawing.cells().get(link.to());
            dashed(g, originX + from.x() * cell + cell / 2, originY + from.y() * cell + cell / 2,
                    originX + to.x() * cell + cell / 2, originY + to.y() * cell + cell / 2);
        }

        g.text(font, drawing.width() + " x " + drawing.height() + "  ·  " + drawing.cells().size() + " symbols  ·  hover a symbol for its tag",
                left + 8, top + H - 18, DIM, false);
        String stamp = "SETPOINT P&ID";
        g.text(font, stamp, left + W - 8 - font.width(stamp), top + H - 18, ACCENT, false);

        super.extractRenderState(g, mouseX, mouseY, a);

        PidDrawing.Cell hovered = cellAt(mouseX, mouseY);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            if (!hovered.tag().isEmpty()) lines.add(Component.literal(hovered.tag()));
            lines.add(Component.literal(PidDrawing.describe(hovered.symbol())));
            g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private PidDrawing.Cell cellAt(int mouseX, int mouseY) {
        if (mouseX < originX || mouseY < originY) return null;
        int x = (mouseX - originX) / cell, y = (mouseY - originY) / cell;
        for (PidDrawing.Cell c : drawing.cells()) if (c.x() == x && c.y() == y) return c;
        return null;
    }

    /** A dashed line (2 on, 2 off), like signal wiring on a P&ID. */
    private static void dashed(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int step = 0; ; step++) {
            if ((step / 2) % 2 == 0) g.fill(x0, y0, x0 + 1, y0 + 1, SIGNAL);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
