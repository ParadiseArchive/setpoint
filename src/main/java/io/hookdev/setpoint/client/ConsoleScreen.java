package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.console.Reading;
import io.hookdev.setpoint.control.AlarmList;
import io.hookdev.setpoint.menu.ConsoleMenu;
import io.hookdev.setpoint.network.ConsoleSnapshot;
import io.hookdev.setpoint.pid.PidDrawing;
import io.hookdev.setpoint.pid.PidLive;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * The control room screen, in the calm high-performance HMI style: grey for normal, amber for
 * attention, magenta for alarms. Four tabs: an overview of all equipment, the trend of one tag,
 * the alarm list, and the P&ID in the console drawn live (running equipment cyan, alarms flashing,
 * tanks filled to their real level).
 */
public class ConsoleScreen extends AbstractContainerScreen<ConsoleMenu> {
    private static final int W = 320, H = 214;
    private static final int FRAME = 0xFF3A404C, PANEL = 0xFF1E222A, WELL = 0xFF12151B, GRID = 0xFF2B2F38, HOVER = 0xFF262B35,
            TEXT = 0xFFD9DEEA, DIM = 0xFF7C869D, CYAN = 0xFF2DE2E6, AMBER = 0xFFFFB547, MAGENTA = 0xFFFF4F8B, WHITE = 0xFFFFFFFF;
    private static final int[] SERIES = {CYAN, WHITE, AMBER};
    private static final int TOP = 30, ROW = 12, ROWS = 12;
    private static final int CHART_X = 30, CHART_Y = 44, CHART_W = 278, CHART_H = 128;
    private static final int AREA_X = 8, AREA_Y = TOP + 12, AREA_W = W - 16, AREA_H = H - 32 - AREA_Y;

    private enum Tab { OVERVIEW, TRENDS, ALARMS, PID }

    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    private final Button[] tabs = new Button[4];
    private Button ackButton, faceplateButton, valuesButton, ejectButton;
    // P&ID tab: zoom (0 = fit), how far the drawing is dragged, and whether the values are written on it
    private int cell, panX, panY;
    private boolean showValues, pressedOnDrawing;
    private double dragged;
    private Map<String, ConsoleSnapshot.Row> byTag = Map.of();

    public ConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        String[] names = {"OVERVIEW", "TRENDS", "ALARMS", "P&ID"};
        int[] widths = {54, 44, 58, 34};
        int x = leftPos + 70;
        for (int i = 0; i < 4; i++) {
            Tab target = Tab.values()[i];
            tabs[i] = addRenderableWidget(Button.builder(Component.literal(names[i]), b -> { tab = target; scroll = 0; refresh(); })
                    .bounds(x, topPos + 5, widths[i], 18).build());
            x += widths[i] + 2;
        }
        ackButton = addRenderableWidget(Button.builder(Component.literal("ACK"), b -> press(ConsoleMenu.BUTTON_ACK))
                .bounds(leftPos + W - 50, topPos + 5, 42, 18).build());
        faceplateButton = addRenderableWidget(Button.builder(Component.literal("FACEPLATE"), b -> openFaceplate())
                .bounds(leftPos + W - 82, topPos + TOP - 2, 74, 14).build());
        valuesButton = addRenderableWidget(Button.builder(Component.literal("VALUES"), b -> { showValues = !showValues; refresh(); })
                .bounds(leftPos + W - 124, topPos + TOP - 2, 70, 14).build());
        ejectButton = addRenderableWidget(Button.builder(Component.literal("EJECT"), b -> eject())
                .bounds(leftPos + W - 50, topPos + TOP - 2, 42, 14).build());
        refresh();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private ConsoleSnapshot data() {
        return menu.snapshot();
    }

    private int rowOf(String tag) {
        List<ConsoleSnapshot.Row> rows = data().rows();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).tag().equals(tag)) return i;
        return -1;
    }

    private boolean trendIsController() {
        int row = rowOf(data().trendTag());
        return row >= 0 && List.of("LIC", "FIC", "PIC").contains(data().rows().get(row).kind());
    }

    private void openFaceplate() {
        int row = rowOf(data().trendTag());
        if (row >= 0) press(ConsoleMenu.BUTTON_FACEPLATE + row);
    }

    private void eject() {
        press(ConsoleMenu.BUTTON_EJECT);
        if (minecraft != null && minecraft.player != null) menu.clickMenuButton(minecraft.player, ConsoleMenu.BUTTON_EJECT);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < 4; i++) tabs[i].active = tab.ordinal() != i;
        int unacked = data().unacknowledged();
        tabs[2].setMessage(Component.literal(unacked > 0 ? "ALARMS " + unacked : "ALARMS"));
        ackButton.active = unacked > 0;
        faceplateButton.visible = tab == Tab.TRENDS && trendIsController();
        valuesButton.visible = ejectButton.visible = tab == Tab.PID && menu.drawing() != null;
        valuesButton.setMessage(Component.literal(showValues ? "VALUES ON" : "VALUES OFF"));
        Map<String, ConsoleSnapshot.Row> map = new HashMap<>();
        for (ConsoleSnapshot.Row r : data().rows()) map.put(r.tag(), r);
        byTag = map;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private static boolean blinkOn() {
        return (Util.getMillis() / 400) % 2 == 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, FRAME);
        g.fill(x + 2, y + 2, x + W - 2, y + H - 2, PANEL);
        g.fill(x + 6, y + TOP - 4, x + W - 6, y + H - 18, WELL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, "DCS", 10, 10, TEXT, false);
        g.text(font, data().rows().size() + " tags", 30, 10, DIM, false);
        switch (tab) {
            case OVERVIEW -> overview(g, mouseX - leftPos, mouseY - topPos);
            case TRENDS -> trends(g);
            case ALARMS -> alarmList(g);
            case PID -> pid(g, mouseX - leftPos, mouseY - topPos);
        }
        banner(g);
    }

    // ---------- overview ----------
    private void overview(GuiGraphicsExtractor g, int mx, int my) {
        List<ConsoleSnapshot.Row> rows = data().rows();
        g.text(font, "TAG", 10, TOP, DIM, false);
        g.text(font, "TYPE", 74, TOP, DIM, false);
        g.text(font, "STATUS", 120, TOP, DIM, false);
        g.text(font, "VALUE", 196, TOP, DIM, false);
        if (rows.isEmpty()) {
            g.text(font, "No equipment within " + 32 + " blocks.", 10, TOP + 16, DIM, false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            ConsoleSnapshot.Row r = rows.get(scroll + i);
            int ry = TOP + 12 + i * ROW;
            boolean hover = mx >= 8 && mx < W - 8 && my >= ry - 2 && my < ry + ROW - 2;
            if (r.tag().equals(data().trendTag())) g.fill(8, ry - 2, W - 8, ry + ROW - 2, GRID);
            else if (hover) g.fill(8, ry - 2, W - 8, ry + ROW - 2, HOVER);
            int colour = r.severity() == Reading.ALARM ? MAGENTA : r.severity() == Reading.ATTENTION ? AMBER : TEXT;
            g.text(font, r.tag(), 10, ry, TEXT, false);
            g.text(font, r.kind(), 74, ry, DIM, false);
            g.text(font, r.status(), 120, ry, colour, false);
            g.text(font, font.plainSubstrByWidth(r.value(), W - 8 - 196 - 4), 196, ry, r.severity() == Reading.ALARM ? MAGENTA : TEXT, false);
        }
        if (rows.size() > ROWS) {
            int barH = Math.max(8, (ROWS * ROW) * ROWS / rows.size());
            int barY = TOP + 10 + (ROWS * ROW - barH) * scroll / (rows.size() - ROWS);
            g.fill(W - 9, barY, W - 7, barY + barH, DIM);
        }
        g.text(font, "click a tag to trend it", 10, H - 30, DIM, false);
    }

    // ---------- trends ----------
    private void trends(GuiGraphicsExtractor g) {
        ConsoleSnapshot d = data();
        if (d.trendTag().isEmpty() || d.series().isEmpty()) {
            g.text(font, "Pick a tag on the OVERVIEW to see its trend.", 10, TOP + 4, DIM, false);
            return;
        }
        g.text(font, d.trendTag(), 10, TOP, TEXT, false);
        g.text(font, "last 5 min", 10 + font.width(d.trendTag()) + 8, TOP, DIM, false);
        // grid
        for (int p = 0; p <= 100; p += 25) {
            int gy = CHART_Y + CHART_H - p * CHART_H / 100;
            g.fill(CHART_X, gy, CHART_X + CHART_W, gy + 1, p == 0 || p == 100 ? DIM : GRID);
            if (p % 50 == 0) g.text(font, String.valueOf(p), CHART_X - 4 - font.width(String.valueOf(p)), gy - 4, DIM, false);
        }
        for (int m = 1; m < 5; m++) {
            int gx = CHART_X + m * CHART_W / 5;
            g.fill(gx, CHART_Y, gx + 1, CHART_Y + CHART_H, GRID);
        }
        // series, newest at the right edge
        for (int s = d.series().size() - 1; s >= 0; s--) {
            float[] values = d.series().get(s);
            int colour = SERIES[s % SERIES.length];
            int prevY = Integer.MIN_VALUE;
            for (int i = 0; i < values.length; i++) {
                if (Float.isNaN(values[i])) { prevY = Integer.MIN_VALUE; continue; }
                int px = CHART_X + (300 - values.length + i) * (CHART_W - 1) / 299;
                int py = CHART_Y + CHART_H - Math.round(Math.max(0, Math.min(100, values[i])) * CHART_H / 100f);
                if (prevY == Integer.MIN_VALUE) prevY = py;
                g.fill(px, Math.min(py, prevY), px + 1, Math.max(py, prevY) + 1, colour);
                prevY = py;
            }
        }
        // legend with the latest values
        int lx = 10, ly = CHART_Y + CHART_H + 8;
        for (int s = 0; s < d.series().size() && s < d.seriesNames().size(); s++) {
            float[] values = d.series().get(s);
            float last = values.length == 0 ? Float.NaN : values[values.length - 1];
            String label = d.seriesNames().get(s) + " " + (Float.isNaN(last) ? "----" : String.format("%.1f", last));
            g.fill(lx, ly + 2, lx + 8, ly + 5, SERIES[s % SERIES.length]);
            g.text(font, label, lx + 11, ly, TEXT, false);
            lx += font.width(label) + 24;
        }
    }

    // ---------- alarms ----------
    private void alarmList(GuiGraphicsExtractor g) {
        List<ConsoleSnapshot.AlarmRow> alarms = data().alarms();
        g.text(font, "TIME", 10, TOP, DIM, false);
        g.text(font, "TAG", 46, TOP, DIM, false);
        g.text(font, "ALARM", 110, TOP, DIM, false);
        g.text(font, "STATE", 250, TOP, DIM, false);
        if (alarms.isEmpty()) {
            g.text(font, "No alarms. All quiet.", 10, TOP + 16, DIM, false);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, alarms.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < alarms.size(); i++) {
            ConsoleSnapshot.AlarmRow a = alarms.get(scroll + i);
            int ry = TOP + 12 + i * ROW;
            AlarmList.State state = AlarmList.State.values()[a.state()];
            boolean active = state != AlarmList.State.CLEARED_UNACK;
            int colour = active ? MAGENTA : DIM;
            g.text(font, a.time(), 10, ry, DIM, false);
            g.text(font, a.tag(), 46, ry, active ? TEXT : DIM, false);
            g.text(font, a.message(), 110, ry, colour, false);
            String label = switch (state) { case ACTIVE_UNACK -> "NEW"; case ACTIVE_ACK -> "ACK"; case CLEARED_UNACK -> "RTN"; };
            if (state != AlarmList.State.ACTIVE_UNACK || blinkOn()) g.text(font, label, 250, ry, colour, false);
        }
        g.text(font, "NEW: not acknowledged   ACK: acknowledged   RTN: back to normal", 10, H - 30, DIM, false);
    }

    // ---------- P&ID ----------
    /** Where the drawing sits in the tab right now: fitted, or zoomed and dragged by the player. */
    private PidRenderer.Layout layout(PidDrawing drawing) {
        int c = cell == 0 ? PidRenderer.fitCell(drawing, AREA_W, AREA_H, 24) : cell;
        int w = drawing.width() * c, h = drawing.height() * c;
        panX = w <= AREA_W ? (AREA_W - w) / 2 : Math.max(AREA_W - w, Math.min(0, panX));
        panY = h <= AREA_H ? (AREA_H - h) / 2 : Math.max(AREA_H - h, Math.min(0, panY));
        return PidRenderer.Layout.of(AREA_X + panX, AREA_Y + panY, c);
    }

    /** The console colours its P&ID like a mimic panel, from the overview rows. */
    private PidRenderer.Style live() {
        Map<String, PidLive> map = new HashMap<>();
        for (ConsoleSnapshot.Row r : byTag.values()) map.put(r.tag(), PidLive.of(r.tag(), r.kind(), r.severity(), r.status(), r.value(), r.gauge()));
        for (PidLive v : data().valves()) map.put(v.tag(), v);
        return new LiveStyle(map, blinkOn());
    }

    private void pid(GuiGraphicsExtractor g, int mx, int my) {
        PidDrawing drawing = menu.drawing();
        if (drawing == null) {
            g.text(font, "No P&ID in this console.", 10, TOP + 4, TEXT, false);
            g.text(font, "Draw your plant with a Blank P&ID, then right-click", 10, TOP + 20, DIM, false);
            g.text(font, "the console with it: the drawing shows here, live.", 10, TOP + 32, DIM, false);
            return;
        }
        g.text(font, font.plainSubstrByWidth(drawing.name(), W - 134 - 10), 10, TOP, TEXT, false);
        PidRenderer.Layout l = layout(drawing);
        g.fill(AREA_X - 1, AREA_Y - 1, AREA_X + AREA_W + 1, AREA_Y + AREA_H + 1, 0xFF000000);
        g.fill(AREA_X, AREA_Y, AREA_X + AREA_W, AREA_Y + AREA_H, PidRenderer.BLUEPRINT);
        g.enableScissor(AREA_X, AREA_Y, AREA_X + AREA_W, AREA_Y + AREA_H);
        PidRenderer.draw(g::fill, drawing, l, live());
        PidDrawing.Cell hovered = inArea(mx, my) ? PidRenderer.cellAt(drawing, l, mx, my) : null;
        if (hovered != null) { // a thin frame around the symbol under the mouse
            int x0 = l.originX() + hovered.x() * l.cell(), y0 = l.originY() + hovered.y() * l.cell(), cw = hovered.spanW() * l.cell(), ch = hovered.spanH() * l.cell();
            g.fill(x0, y0, x0 + cw, y0 + 1, CYAN); g.fill(x0, y0 + ch - 1, x0 + cw, y0 + ch, CYAN);
            g.fill(x0, y0, x0 + 1, y0 + ch, CYAN); g.fill(x0 + cw - 1, y0, x0 + cw, y0 + ch, CYAN);
        }
        if (showValues) {
            for (PidDrawing.Cell c : drawing.cells()) {
                ConsoleSnapshot.Row r = byTag.get(c.tag());
                if (r == null || c.symbol() == PidDrawing.PIPE) continue;
                String text = brief(r);
                int tx = l.centreX(c) + 4 * l.scale() + 2, ty = l.centreY(c) - 4;
                g.fill(tx - 1, ty - 1, tx + font.width(text) + 1, ty + 9, 0xD0000000);
                g.text(font, text, tx, ty, r.severity() == Reading.ALARM ? MAGENTA : r.severity() == Reading.ATTENTION ? AMBER : TEXT, false);
            }
        }
        g.disableScissor();
        g.text(font, "scroll: zoom   drag: move   click a symbol: its trend", 10, H - 30, DIM, false);
    }

    /** The main value in a few characters, like "62.0 %" or "84 mB/t". */
    private static String brief(ConsoleSnapshot.Row r) {
        int cut = r.value().indexOf("  ");
        return cut < 0 ? r.value() : r.value().substring(0, cut);
    }

    private static boolean inArea(double mx, double my) {
        return mx >= AREA_X && mx < AREA_X + AREA_W && my >= AREA_Y && my < AREA_Y + AREA_H;
    }

    private PidDrawing.@Nullable Cell hoveredCell(int mouseX, int mouseY) {
        PidDrawing drawing = menu.drawing();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (tab != Tab.PID || drawing == null || !inArea(mx, my)) return null;
        return PidRenderer.cellAt(drawing, layout(drawing), mx, my);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        PidDrawing.Cell hovered = hoveredCell(mouseX, mouseY);
        if (hovered == null) return;
        List<Component> lines = new ArrayList<>();
        ConsoleSnapshot.Row r = byTag.get(hovered.tag());
        if (!hovered.tag().isEmpty()) lines.add(Component.literal(hovered.tag()));
        lines.add(Component.literal(PidDrawing.describe(hovered.symbol())));
        if (r != null) {
            lines.add(Component.literal(r.status()));
            lines.add(Component.literal(r.value()));
        } else if (!hovered.tag().isEmpty()) {
            lines.add(Component.literal("Not seen: gone, or out of this console's range"));
        }
        g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** The newest unacknowledged alarm along the bottom, flashing, like the alarm banner of a real DCS. */
    private void banner(GuiGraphicsExtractor g) {
        ConsoleSnapshot.AlarmRow newest = data().alarms().stream()
                .filter(a -> a.state() != AlarmList.State.ACTIVE_ACK.ordinal()).findFirst().orElse(null);
        if (newest == null) {
            g.text(font, "no unacknowledged alarms", 10, H - 13, DIM, false);
            return;
        }
        if (blinkOn()) g.fill(8, H - 15, 12, H - 6, MAGENTA);
        g.text(font, newest.time() + "  " + newest.tag() + "  " + newest.message(), 16, H - 13, MAGENTA, false);
    }

    // ---------- mouse ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The table first: the container screen's own handler claims every click.
        int mx = (int) event.x() - leftPos, my = (int) event.y() - topPos;
        if (tab == Tab.PID && event.button() == 0 && menu.drawing() != null && inArea(mx, my)) {
            pressedOnDrawing = true;
            dragged = 0;
            return true;
        }
        int i = Math.floorDiv(my - (TOP + 10), ROW);
        int row = scroll + i;
        if (tab != Tab.OVERVIEW || event.button() != 0 || mx < 8 || mx >= W - 8 || i < 0 || i >= ROWS || row >= data().rows().size()) {
            return super.mouseClicked(event, doubleClick);
        }
        press(ConsoleMenu.BUTTON_TREND + row);
        tab = Tab.TRENDS;
        refresh();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!pressedOnDrawing) return super.mouseDragged(event, dx, dy);
        panX += (int) Math.round(dx);
        panY += (int) Math.round(dy);
        dragged += Math.abs(dx) + Math.abs(dy);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!pressedOnDrawing) return super.mouseReleased(event);
        pressedOnDrawing = false;
        PidDrawing.Cell clicked = dragged < 3 ? hoveredCell((int) event.x(), (int) event.y()) : null;
        int row = clicked == null || clicked.tag().isEmpty() ? -1 : rowOf(clicked.tag());
        if (row >= 0) {
            press(ConsoleMenu.BUTTON_TREND + row);
            tab = Tab.TRENDS;
            refresh();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.TRENDS) return super.mouseScrolled(x, y, scrollX, scrollY);
        if (tab == Tab.PID) {
            PidDrawing drawing = menu.drawing();
            if (drawing == null) return true;
            // zoom in steps of 4 pixels a cell, keeping the spot under the mouse where it is
            PidRenderer.Layout before = layout(drawing);
            int next = Math.max(PidRenderer.MIN_CELL, Math.min(PidRenderer.MAX_CELL, before.cell() + 4 * (int) Math.signum(scrollY)));
            if (next == before.cell()) return true;
            double mx = x - leftPos, my = y - topPos;
            double fx = (mx - before.originX()) / before.cell(), fy = (my - before.originY()) / before.cell();
            cell = next;
            panX = (int) Math.round(mx - fx * next) - AREA_X;
            panY = (int) Math.round(my - fy * next) - AREA_Y;
            return true;
        }
        scroll -= (int) Math.signum(scrollY);
        if (scroll < 0) scroll = 0;
        return true;
    }
}
