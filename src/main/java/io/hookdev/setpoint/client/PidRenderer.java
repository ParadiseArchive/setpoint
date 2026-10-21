package io.hookdev.setpoint.client;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.pid.PidDrawing;

/**
 * Draws a P&ID on blueprint paper: the grid, the pipes, the symbols and the dashed signal lines. Used by the P&ID
 * item's own screen, by the console and by the P&ID board, which colour the symbols with live plant data through a
 * {@link Style}. Small cells (a big drawing on a small board) get plain squares instead of symbols.
 */
public final class PidRenderer {
    public static final int BLUEPRINT = 0xFF10294D, GRID = 0xFF16345F, LINE = 0xFFE8F1FF, ACCENT = 0xFF2DE2E6, SIGNAL = 0xFFFFB547;
    public static final int MIN_CELL = 8, MAX_CELL = 32;

    private PidRenderer() {}

    /** Where the drawing sits on screen: the top-left of cell (0, 0), the size of one cell and of one symbol pixel. */
    public record Layout(int originX, int originY, int cell, int scale) {
        public static Layout of(int originX, int originY, int cell) {
            int padding = Math.max(2, cell / 4); // room around each symbol, so pipe runs show between symbols
            return new Layout(originX, originY, cell, Math.max(1, (cell - padding) / 8));
        }

        public int centreX(PidDrawing.Cell c) { return originX + c.x() * cell + cell / 2; }
        public int centreY(PidDrawing.Cell c) { return originY + c.y() * cell + cell / 2; }
    }

    /** How each symbol is coloured. The plain P&ID draws every symbol the same; the console colours them by state. */
    public interface Style {
        Style PLAIN = new Style() {};

        default int line(PidDrawing.Cell cell) { return LINE; }
        default int accent(PidDrawing.Cell cell) { return ACCENT; }
        /** For a tank: how full it is, 0 to 1, drawn as liquid inside it. Below 0 draws the symbol as it is. */
        default float fill(PidDrawing.Cell cell) { return -1; }
    }

    /** The biggest cell (up to {@code maxCell}, at least {@link #MIN_CELL}) that fits the drawing into the area. */
    public static int fitCell(PidDrawing drawing, int areaW, int areaH, int maxCell) {
        int fit = Math.min(areaW / Math.max(1, drawing.width()), areaH / Math.max(1, drawing.height()));
        return Math.max(MIN_CELL, Math.min(maxCell, fit));
    }

    /** The layout that puts the drawing in the middle of the area. */
    public static Layout centred(PidDrawing drawing, int x, int y, int areaW, int areaH, int cell) {
        return Layout.of(x + (areaW - drawing.width() * cell) / 2, y + (areaH - drawing.height() * cell) / 2, cell);
    }

    /** Below this cell size the symbols do not fit: they are drawn as plain squares. */
    public static final int SMALL_CELL = 10;

    public static void draw(PidCanvas g, PidDrawing drawing, Layout l, Style style) {
        if (l.cell() < SMALL_CELL) {
            drawSmall(g, drawing, l, style);
            return;
        }
        int cell = l.cell(), w = drawing.width() * cell, h = drawing.height() * cell;
        // the paper's grid, one line per cell
        for (int x = l.originX(); x <= l.originX() + w; x += cell) g.fill(x, l.originY(), x + 1, l.originY() + h, GRID);
        for (int y = l.originY(); y <= l.originY() + h; y += cell) g.fill(l.originX(), y, l.originX() + w, y + 1, GRID);

        int thick = Math.max(1, l.scale());
        // pipes first, then the symbols on top, then the signal lines
        for (PidDrawing.Cell c : drawing.cells()) {
            int cx = l.centreX(c), cy = l.centreY(c);
            int x0 = l.originX() + c.x() * cell, y0 = l.originY() + c.y() * cell;
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
            int size = 8 * l.scale();
            int sx = l.originX() + c.x() * cell + (cell - size) / 2, sy = l.originY() + c.y() * cell + (cell - size) / 2;
            g.fill(sx, sy, sx + size, sy + size, BLUEPRINT);
            PidSymbols.draw(g, c.symbol(), c.rotation(), sx, sy, l.scale(), style.line(c), style.accent(c), style.fill(c));
        }
        for (PidDrawing.Link link : drawing.links()) {
            if (link.from() >= drawing.cells().size() || link.to() >= drawing.cells().size()) continue;
            PidDrawing.Cell from = drawing.cells().get(link.from()), to = drawing.cells().get(link.to());
            dashed(g, l.centreX(from), l.centreY(from), l.centreX(to), l.centreY(to));
        }
    }

    /** A drawing too big for its space: pipes as thin lines, every piece of equipment as a square in its colour. */
    private static void drawSmall(PidCanvas g, PidDrawing drawing, Layout l, Style style) {
        int cell = l.cell();
        for (PidDrawing.Cell c : drawing.cells()) {
            int x0 = l.originX() + c.x() * cell, y0 = l.originY() + c.y() * cell, cx = l.centreX(c), cy = l.centreY(c);
            if ((c.pipes() & PidDrawing.NORTH) != 0) g.fill(cx, y0, cx + 1, cy + 1, LINE);
            if ((c.pipes() & PidDrawing.SOUTH) != 0) g.fill(cx, cy, cx + 1, y0 + cell, LINE);
            if ((c.pipes() & PidDrawing.WEST) != 0) g.fill(x0, cy, cx + 1, cy + 1, LINE);
            if ((c.pipes() & PidDrawing.EAST) != 0) g.fill(cx, cy, x0 + cell, cy + 1, LINE);
        }
        for (PidDrawing.Cell c : drawing.cells()) {
            if (c.symbol() == PidDrawing.PIPE) continue;
            int x0 = l.originX() + c.x() * cell, y0 = l.originY() + c.y() * cell, size = Math.max(1, cell - 1);
            g.fill(x0, y0, x0 + size, y0 + size, style.line(c));
            float fill = style.fill(c);
            if (fill > 0 && size > 2) { // a tank: its level as liquid inside
                int inner = size - 2, height = Math.round(Math.min(1, fill) * inner);
                g.fill(x0 + 1, y0 + 1, x0 + size - 1, y0 + size - 1, BLUEPRINT);
                g.fill(x0 + 1, y0 + size - 1 - height, x0 + size - 1, y0 + size - 1, style.accent(c));
            }
        }
    }

    public static PidDrawing.@Nullable Cell cellAt(PidDrawing drawing, Layout l, int mouseX, int mouseY) {
        if (mouseX < l.originX() || mouseY < l.originY()) return null;
        int x = (mouseX - l.originX()) / l.cell(), y = (mouseY - l.originY()) / l.cell();
        for (PidDrawing.Cell c : drawing.cells()) if (c.x() == x && c.y() == y) return c;
        return null;
    }

    /** A dashed line (2 on, 2 off), like signal wiring on a P&ID. */
    private static void dashed(PidCanvas g, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int step = 0; ; step++) {
            if ((step / 2) % 2 == 0) g.fill(x0, y0, x0 + 1, y0 + 1, SIGNAL);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
}
