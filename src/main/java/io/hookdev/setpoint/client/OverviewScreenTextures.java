package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.block.entity.OverviewScreenBlockEntity;
import io.hookdev.setpoint.console.OverviewData;
import io.hookdev.setpoint.console.Reading;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * One texture per video wall, painted like a DCS screen: the overview tiles, the alarm list or the trends, with the
 * colours of the console (cyan running, amber attention, magenta alarm, flashing until acknowledged). The words are not
 * painted in: the renderer writes them over it with the game's font, at the spots {@link Label} gives.
 */
public final class OverviewScreenTextures {
    public static final int PIXELS_PER_BLOCK = 64, MARGIN = 3, TITLE = 10;
    /** Font pixels per texture pixel: a line of text is about 7 texture pixels high. */
    public static final float FONT_SCALE_INVERSE = 1.3f;
    private static final int BEZEL = 0xFF2A2E36, TITLE_BAR = 0xFF1E222A, SCREEN = 0xFF0B0E14, TILE = 0xFF161B24, GRID = 0xFF1C2431,
            TEXT = 0xFFD9DEEA, GREY = 0xFF7C869D, CYAN = LiveStyle.CYAN, AMBER = LiveStyle.AMBER, MAGENTA = LiveStyle.MAGENTA, DIM = 0xFF3A2030;
    private static final int[] SERIES = {CYAN, MAGENTA, AMBER};
    private static final Map<BlockPos, Wall> WALLS = new HashMap<>();

    private OverviewScreenTextures() {}

    /** A word on the wall: where it starts (texture pixels from the top-left), its colour and how wide it may be. */
    public record Label(float x, float y, String text, int colour, float maxWidth) {}

    /** The texture of a wall and the words to write on it. */
    public record Prepared(Identifier texture, List<Label> labels) {}

    private static final class Wall {
        final DynamicTexture texture;
        final Identifier id;
        final int width, height;
        int version = -1, page = -1;
        boolean blink;
        List<Label> labels = List.of();

        Wall(BlockPos pos, int width, int height) {
            this.width = width;
            this.height = height;
            this.texture = new DynamicTexture(() -> "Overview screen " + pos.toShortString(), width * PIXELS_PER_BLOCK, height * PIXELS_PER_BLOCK, true);
            this.id = Setpoint.id("overview_screen/" + Math.floorMod(pos.asLong(), Long.MAX_VALUE));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    /** The wall whose top-left screen this is, painted up to date. */
    public static Prepared prepare(OverviewScreenBlockEntity screen, int width, int height) {
        BlockPos pos = screen.getBlockPos().immutable();
        Wall w = WALLS.get(pos);
        if (w == null || w.width != width || w.height != height) {
            if (w != null) w.close();
            w = new Wall(pos, width, height);
            WALLS.put(pos, w);
        }
        OverviewData data = screen.data();
        boolean blink = (Util.getMillis() / 400) % 2 == 0;
        boolean flashing = data.unacked() > 0;
        if (w.version != screen.version() || w.page != screen.page() || (flashing && blink != w.blink)) {
            w.labels = paint(w.texture.getPixels(), data, screen.page(), blink, Minecraft.getInstance().font);
            w.texture.upload();
            w.version = screen.version();
            w.page = screen.page();
            w.blink = blink;
        }
        return new Prepared(w.id, w.labels);
    }

    private static List<Label> paint(NativeImage image, OverviewData data, int page, boolean blink, Font font) {
        int w = image.getWidth(), h = image.getHeight(), m = MARGIN;
        Canvas c = new Canvas(image);
        List<Label> labels = new ArrayList<>();
        c.fill(0, 0, w, h, BEZEL);
        c.fill(m, m, w - m, h - m, SCREEN);
        c.fill(m, m, w - m, m + TITLE, TITLE_BAR);
        int x0 = 2 * m, y0 = 2 * m + TITLE, x1 = w - 2 * m, y1 = h - 2 * m;
        labels.add(new Label(x0, m + 2, OverviewScreenBlockEntity.PAGE_NAMES[page], TEXT, (x1 - x0) * 0.6f));
        float clockWidth = width(font, data.clock());
        labels.add(new Label(x1 - clockWidth, m + 2, data.clock(), GREY, clockWidth + 1));
        if (data.unacked() > 0) { // a flashing alarm count next to the clock, on every page
            String count = data.unacked() + " ALM";
            float countWidth = width(font, count);
            float at = x1 - clockWidth - countWidth - 6;
            c.fill((int) at - 2, m + 1, (int) (at + countWidth) + 2, m + TITLE - 1, blink ? MAGENTA : DIM);
            labels.add(new Label(at, m + 2, count, TEXT, countWidth + 1));
        }
        if (!data.linked()) {
            String a = "NO CONSOLE IN RANGE", b = "Put a DCS console within 32 blocks";
            labels.add(new Label((w - width(font, a)) / 2f, h / 2f - 8, a, AMBER, w));
            labels.add(new Label((w - width(font, b)) / 2f, h / 2f + 1, b, GREY, w));
            return labels;
        }
        switch (page) {
            case OverviewScreenBlockEntity.ALARMS -> alarms(c, labels, data, blink, font, x0, y0, x1, y1);
            case OverviewScreenBlockEntity.TRENDS -> trends(c, labels, data, font, x0, y0, x1, y1);
            case OverviewScreenBlockEntity.PID -> pid(c, labels, data, blink, x0, y0, x1, y1);
            case OverviewScreenBlockEntity.POWER -> power(c, labels, data, x0, y0, x1, y1);
            default -> overview(c, labels, data, blink, x0, y0, x1, y1);
        }
        return labels;
    }

    /** The P&ID in the console, live like on the console and the P&ID board. */
    private static void pid(Canvas c, List<Label> labels, OverviewData data, boolean blink, int x0, int y0, int x1, int y1) {
        if (data.pid().isEmpty()) {
            labels.add(new Label(x0 + 2, y0 + 2, "No P&ID in the console: put one in to see it here", GREY, x1 - x0));
            return;
        }
        io.hookdev.setpoint.pid.PidDrawing drawing = data.pid().get();
        java.util.Map<String, io.hookdev.setpoint.pid.PidLive> live = new java.util.HashMap<>();
        for (io.hookdev.setpoint.pid.PidLive l : data.live()) live.put(l.tag(), l);
        c.fill(x0, y0, x1, y1, PidRenderer.BLUEPRINT);
        int cell = PidRenderer.fitCell(drawing, x1 - x0, y1 - y0, PidRenderer.MAX_CELL);
        PidRenderer.draw(c::fill, drawing, PidRenderer.centred(drawing, x0, y0, x1 - x0, y1 - y0, cell), new LiveStyle(live, blink));
    }

    /**
     * The power page: on top what all generators make together and the batteries' charge with a bar; below a line per
     * generator (what it makes), battery (its charge, in or out) and power meter (what its cable carries).
     */
    private static void power(Canvas c, List<Label> labels, OverviewData data, int x0, int y0, int x1, int y1) {
        OverviewData.Power power = data.power();
        if (power.lines().isEmpty()) {
            labels.add(new Label(x0 + 2, y0 + 2, "No generators, batteries or power meters near the console", GREY, x1 - x0));
            return;
        }
        int row = 9, half = (x0 + x1) / 2;
        labels.add(new Label(x0 + 2, y0 + 1, "MADE", GREY, 26));
        labels.add(new Label(x0 + 28, y0 + 1, power.made() + " FE/t", CYAN, half - x0 - 30));
        if (power.capacity() > 0) {
            int percent = (int) (power.stored() * 100 / power.capacity());
            labels.add(new Label(half, y0 + 1, String.format("BATTERY %d %%  %+d FE/t", percent, power.rate()), percent == 0 ? AMBER : TEXT, x1 - half));
            c.fill(half, y0 + row - 2, x1 - 2, y0 + row, TILE);
            c.fill(half, y0 + row - 2, half + (x1 - 2 - half) * percent / 100, y0 + row, percent == 0 ? AMBER : CYAN);
        }
        c.fill(x0, y0 + row + 1, x1, y0 + row + 2, GRID);
        int most = 1;
        for (OverviewData.PowerLine line : power.lines()) most = Math.max(most, Math.abs(line.fe()));
        int rows = Math.max(1, (y1 - y0 - row - 4) / row), kindX = x0 + Math.min(60, (x1 - x0) / 4), valueX = kindX + 46, barX = valueX + 54;
        for (int i = 0; i < Math.min(power.lines().size(), rows); i++) {
            float y = y0 + row + 4 + i * row;
            if (i == rows - 1 && power.lines().size() > rows) {
                labels.add(new Label(x0 + 2, y, "+" + (power.lines().size() - rows + 1) + " more on the console", GREY, x1 - x0));
                break;
            }
            OverviewData.PowerLine line = power.lines().get(i);
            if (i % 2 == 1) c.fill(x0, (int) y - 1, x1, (int) y + row - 1, TILE);
            labels.add(new Label(x0 + 2, y, line.tag(), TEXT, kindX - x0 - 4));
            labels.add(new Label(kindX, y, line.kind(), GREY, valueX - kindX - 2));
            String value = line.percent() >= 0 ? String.format("%d %% %+d", line.percent(), line.fe()) : line.fe() + " FE/t";
            labels.add(new Label(valueX, y, value, line.fe() == 0 && line.percent() < 0 ? GREY : CYAN, barX - valueX - 2));
            if (x1 - 3 - barX > 8) { // a bar: a battery's charge, or what this one carries next to the biggest
                int length = line.percent() >= 0 ? (x1 - 3 - barX) * line.percent() / 100 : (x1 - 3 - barX) * Math.abs(line.fe()) / most;
                c.fill(barX, (int) y + 2, x1 - 3, (int) y + 5, TILE);
                c.fill(barX, (int) y + 2, barX + length, (int) y + 5, line.percent() >= 0 ? CYAN : AMBER);
            }
        }
    }

    /** Every tag as a tile: the tag, and its status in the colour of how it is doing. */
    private static void overview(Canvas c, List<Label> labels, OverviewData data, boolean blink, int x0, int y0, int x1, int y1) {
        List<OverviewData.Tile> tiles = data.tiles();
        if (tiles.isEmpty()) {
            labels.add(new Label(x0 + 2, y0 + 2, "No equipment within 32 blocks of the console", GREY, x1 - x0));
            return;
        }
        int gap = 2, tileH = 20, cols = Math.max(1, (x1 - x0 + gap) / 62), tileW = (x1 - x0 - (cols - 1) * gap) / cols;
        int rows = Math.max(1, (y1 - y0 + gap) / (tileH + gap)), room = cols * rows;
        for (int i = 0; i < Math.min(tiles.size(), room); i++) {
            int x = x0 + (i % cols) * (tileW + gap), y = y0 + (i / cols) * (tileH + gap);
            c.fill(x, y, x + tileW, y + tileH, TILE);
            if (i == room - 1 && tiles.size() > room) {
                labels.add(new Label(x + 5, y + 6, "+" + (tiles.size() - room + 1) + " more", GREY, tileW - 7));
                break;
            }
            OverviewData.Tile t = tiles.get(i);
            int colour = colour(t.severity(), blink);
            c.fill(x, y, x + 2, y + tileH, colour);
            labels.add(new Label(x + 5, y + 3, t.tag() + "  " + t.kind(), TEXT, tileW - 7));
            labels.add(new Label(x + 5, y + 11, t.status() + "  " + t.value(), t.severity() == Reading.NORMAL ? CYAN : colour == DIM ? TEXT : colour, tileW - 7));
        }
    }

    /** The alarm list, newest first as the console keeps it: time, tag, message and state. */
    private static void alarms(Canvas c, List<Label> labels, OverviewData data, boolean blink, Font font, int x0, int y0, int x1, int y1) {
        int row = 9, tagX = x0 + 30, msgX = tagX + Math.min(70, (x1 - x0) / 4), stateX = x1 - 22;
        boolean stateColumn = x1 - x0 >= 150;
        labels.add(new Label(x0 + 2, y0 + 1, "TIME", GREY, 28));
        labels.add(new Label(tagX, y0 + 1, "TAG", GREY, msgX - tagX));
        labels.add(new Label(msgX, y0 + 1, "ALARM", GREY, (stateColumn ? stateX : x1) - msgX));
        if (stateColumn) labels.add(new Label(stateX, y0 + 1, "STATE", GREY, x1 - stateX));
        c.fill(x0, y0 + row, x1, y0 + row + 1, GRID);
        int bottom = data.unacked() > 0 ? y1 - row - 2 : y1;
        if (data.unacked() > 0) {
            c.fill(x0, y1 - row, x1, y1, blink ? MAGENTA : DIM);
            String banner = data.unacked() + " UNACKNOWLEDGED - ACK ON THE CONSOLE";
            labels.add(new Label(Math.max(x0 + 2, (x0 + x1 - width(font, banner)) / 2f), y1 - row + 1, banner, TEXT, x1 - x0 - 4));
        }
        List<OverviewData.AlarmLine> list = data.alarms();
        if (list.isEmpty()) {
            labels.add(new Label(x0 + 2, y0 + row + 3, "NO ALARMS", CYAN, x1 - x0));
            return;
        }
        int rows = Math.max(1, (bottom - y0 - row - 2) / row);
        for (int i = 0; i < Math.min(list.size(), rows); i++) {
            float y = y0 + row + 2 + i * row;
            if (i == rows - 1 && list.size() > rows) {
                labels.add(new Label(x0 + 2, y, "+" + (list.size() - rows + 1) + " more on the console", GREY, x1 - x0));
                break;
            }
            OverviewData.AlarmLine a = list.get(i);
            int colour = switch (a.state()) {
                case 0 -> blink ? MAGENTA : TEXT;
                case 1 -> AMBER;
                default -> TEXT;
            };
            if (i % 2 == 1) c.fill(x0, (int) y - 1, x1, (int) y + row - 1, TILE);
            labels.add(new Label(x0 + 2, y, a.time(), colour, 28));
            labels.add(new Label(tagX, y, a.tag(), colour, msgX - tagX - 2));
            labels.add(new Label(msgX, y, a.message(), colour, (stateColumn ? stateX : x1) - msgX - 2));
            if (stateColumn) labels.add(new Label(stateX, y, a.state() == 0 ? "NEW" : a.state() == 1 ? "ACK" : "RTN", colour, x1 - stateX));
        }
    }

    /** Up to four trend panels: the last few minutes of a tag, every series in its own colour (PV cyan, SP magenta, OP amber). */
    private static void trends(Canvas c, List<Label> labels, OverviewData data, Font font, int x0, int y0, int x1, int y1) {
        List<OverviewData.Trend> trends = data.trends();
        if (trends.isEmpty()) {
            labels.add(new Label(x0 + 2, y0 + 2, "No trends yet: the console needs a few seconds", GREY, x1 - x0));
            return;
        }
        int n = trends.size(), cols = n == 1 ? 1 : n == 2 ? ((x1 - x0) >= (y1 - y0) ? 2 : 1) : 2, rows = (n + cols - 1) / cols, gap = 3;
        int pw = (x1 - x0 - (cols - 1) * gap) / cols, ph = (y1 - y0 - (rows - 1) * gap) / rows;
        for (int i = 0; i < n; i++) {
            OverviewData.Trend t = trends.get(i);
            int px = x0 + (i % cols) * (pw + gap), py = y0 + (i / cols) * (ph + gap);
            c.fill(px, py, px + pw, py + ph, TILE);
            float lx = px + 3;
            labels.add(new Label(lx, py + 2, t.tag(), TEXT, pw - 6));
            lx += width(font, t.tag()) + 6;
            for (int s = 0; s < t.names().size() && s < SERIES.length; s++) {
                if (lx >= px + pw - 4) break;
                labels.add(new Label(lx, py + 2, t.names().get(s), SERIES[s], px + pw - 3 - lx));
                lx += width(font, t.names().get(s)) + 4;
            }
            int gx0 = px + 3, gy0 = py + 11, gx1 = px + pw - 3, gy1 = py + ph - 3;
            if (gy1 - gy0 < 6 || gx1 - gx0 < 6) continue;
            for (int q = 0; q <= 4; q++) c.fill(gx0, gy0 + (gy1 - gy0) * q / 4, gx1, gy0 + (gy1 - gy0) * q / 4 + 1, GRID);
            int samples = io.hookdev.setpoint.control.TrendBuffer.SAMPLES / OverviewData.STEP;
            for (int s = t.series().size() - 1; s >= 0; s--) { // PV last, so it is drawn on top
                List<Float> values = t.series().get(s);
                int colour = SERIES[Math.min(s, SERIES.length - 1)];
                int lastX = Integer.MIN_VALUE, lastY = 0;
                for (int k = 0; k < values.size(); k++) {
                    float v = values.get(k);
                    if (Float.isNaN(v)) { lastX = Integer.MIN_VALUE; continue; }
                    int x = gx1 - 1 - (int) ((long) (values.size() - 1 - k) * (gx1 - gx0 - 1) / Math.max(1, samples - 1)); // newest at the right
                    int y = gy1 - 1 - Math.round(Math.max(0, Math.min(100, v)) / 100f * (gy1 - gy0 - 1));
                    if (lastX != Integer.MIN_VALUE) c.line(lastX, lastY, x, y, colour);
                    else c.fill(x, y, x + 1, y + 1, colour);
                    lastX = x;
                    lastY = y;
                }
            }
        }
    }

    private static int colour(int severity, boolean blink) {
        return switch (severity) {
            case Reading.ALARM -> blink ? MAGENTA : DIM;
            case Reading.ATTENTION -> AMBER;
            default -> CYAN;
        };
    }

    /** How wide a word is, in texture pixels. */
    private static float width(Font font, String text) {
        return font.width(text) / FONT_SCALE_INVERSE;
    }

    /** Filled rectangles and one-pixel lines on the texture, clipped to it. */
    private record Canvas(NativeImage image) {
        void fill(int x0, int y0, int x1, int y1, int colour) {
            int ax = Math.max(0, x0), ay = Math.max(0, y0), bx = Math.min(image.getWidth(), x1), by = Math.min(image.getHeight(), y1);
            if (bx > ax && by > ay) image.fillRect(ax, ay, bx - ax, by - ay, colour);
        }

        void line(int xa, int ya, int xb, int yb, int colour) {
            int dx = Math.abs(xb - xa), dy = -Math.abs(yb - ya), sx = xa < xb ? 1 : -1, sy = ya < yb ? 1 : -1, err = dx + dy;
            while (true) {
                fill(xa, ya, xa + 1, ya + 1, colour);
                if (xa == xb && ya == yb) break;
                int e2 = 2 * err;
                if (e2 >= dy) { err += dy; xa += sx; }
                if (e2 <= dx) { err += dx; ya += sy; }
            }
        }
    }

    /** The wall is gone: free its texture. */
    public static void remove(BlockPos pos) {
        Wall w = WALLS.remove(pos);
        if (w != null) w.close();
    }

    /** Leaving a world: free every wall texture. */
    public static void clear() {
        WALLS.values().forEach(Wall::close);
        WALLS.clear();
    }
}
