package io.hookdev.setpoint.client;

import java.util.List;

import io.hookdev.setpoint.console.Reading;
import io.hookdev.setpoint.control.AlarmList;
import io.hookdev.setpoint.menu.ConsoleMenu;
import io.hookdev.setpoint.network.ConsoleSnapshot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * The control room screen, in the calm high-performance HMI style: grey for normal, amber for
 * attention, magenta for alarms. Three tabs: an overview of all equipment, the trend of one tag,
 * and the alarm list.
 */
public class ConsoleScreen extends AbstractContainerScreen<ConsoleMenu> {
    private static final int W = 320, H = 214;
    private static final int FRAME = 0xFF3A404C, PANEL = 0xFF1E222A, WELL = 0xFF12151B, GRID = 0xFF2B2F38, HOVER = 0xFF262B35,
            TEXT = 0xFFD9DEEA, DIM = 0xFF7C869D, CYAN = 0xFF2DE2E6, AMBER = 0xFFFFB547, MAGENTA = 0xFFFF4F8B, WHITE = 0xFFFFFFFF;
    private static final int[] SERIES = {CYAN, WHITE, AMBER};
    private static final int TOP = 30, ROW = 12, ROWS = 12;
    private static final int CHART_X = 30, CHART_Y = 44, CHART_W = 278, CHART_H = 128;

    private enum Tab { OVERVIEW, TRENDS, ALARMS }

    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    private final Button[] tabs = new Button[3];
    private Button ackButton, faceplateButton;

    public ConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        String[] names = {"OVERVIEW", "TRENDS", "ALARMS"};
        int[] widths = {62, 52, 58};
        int x = leftPos + 70;
        for (int i = 0; i < 3; i++) {
            Tab target = Tab.values()[i];
            tabs[i] = addRenderableWidget(Button.builder(Component.literal(names[i]), b -> { tab = target; scroll = 0; refresh(); })
                    .bounds(x, topPos + 5, widths[i], 18).build());
            x += widths[i] + 2;
        }
        ackButton = addRenderableWidget(Button.builder(Component.literal("ACK"), b -> press(ConsoleMenu.BUTTON_ACK))
                .bounds(leftPos + W - 50, topPos + 5, 42, 18).build());
        faceplateButton = addRenderableWidget(Button.builder(Component.literal("FACEPLATE"), b -> openFaceplate())
                .bounds(leftPos + W - 82, topPos + TOP - 2, 74, 14).build());
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
        return row >= 0 && data().rows().get(row).kind().equals("LIC");
    }

    private void openFaceplate() {
        int row = rowOf(data().trendTag());
        if (row >= 0) press(ConsoleMenu.BUTTON_FACEPLATE + row);
    }

    private void refresh() {
        for (int i = 0; i < 3; i++) tabs[i].active = tab.ordinal() != i;
        int unacked = data().unacknowledged();
        tabs[2].setMessage(Component.literal(unacked > 0 ? "ALARMS " + unacked : "ALARMS"));
        ackButton.active = unacked > 0;
        faceplateButton.visible = tab == Tab.TRENDS && trendIsController();
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
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.TRENDS) return super.mouseScrolled(x, y, scrollX, scrollY);
        scroll -= (int) Math.signum(scrollY);
        if (scroll < 0) scroll = 0;
        return true;
    }
}
