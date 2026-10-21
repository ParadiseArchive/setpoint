package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.control.PidController;
import io.hookdev.setpoint.menu.FaceplateMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * DCS faceplate in a high-performance HMI style: calm greys, colour only when something is wrong.
 * Left: vertical PV bar with SP marker and alarm limits. Middle: numbers, OP and valve bars. Right: buttons.
 */
public class FaceplateScreen extends AbstractContainerScreen<FaceplateMenu> {
    private static final int W = 248, H = 190;
    // HMI palette
    private static final int FRAME = 0xFF3A404C, PANEL = 0xFF1E222A, WELL = 0xFF12151B, TEXT = 0xFFD9DEEA, DIM = 0xFF7C869D,
            BAR = 0xFF8D95A5, SP = 0xFFFFFFFF, LIMIT = 0xFFFFB547, ALARM = 0xFFFF4F8B, VALVE = 0xFF2DE2E6;
    private static final int BAR_X = 12, BAR_Y = 34, BAR_W = 22, BAR_H = 120;

    private final List<Button> opButtons = new ArrayList<>();
    private Button tuningButton, actionButton;
    private final Button[] modeButtons = new Button[3];

    public FaceplateScreen(FaceplateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        opButtons.clear();
        int x = leftPos + 146, y = topPos + 26;
        String[] modes = {"MAN", "AUTO", "CAS"};
        for (int i = 0; i < 3; i++) {
            modeButtons[i] = addButton(modes[i], x + i * 32, y, 31, ControllerBlockEntity.BUTTON_MAN + i);
        }
        stepRow(x, y + 24, ControllerBlockEntity.BUTTON_SP_DOWN_10, null);
        stepRow(x, y + 46, ControllerBlockEntity.BUTTON_OP_DOWN_10, opButtons);
        tuningButton = addButton("", x, y + 68, 95, ControllerBlockEntity.BUTTON_TUNING);
        actionButton = addButton("", x, y + 90, 95, ControllerBlockEntity.BUTTON_ACTION);
        addButton("-", x + 16, y + 112, 15, ControllerBlockEntity.BUTTON_HI_DOWN);
        addButton("+", x + 32, y + 112, 15, ControllerBlockEntity.BUTTON_HI_UP);
        addButton("-", x + 64, y + 112, 15, ControllerBlockEntity.BUTTON_LO_DOWN);
        addButton("+", x + 80, y + 112, 15, ControllerBlockEntity.BUTTON_LO_UP);
        refreshButtons();
    }

    private void stepRow(int x, int y, int firstButton, List<Button> into) {
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            Button b = addButton(labels[i], x + i * 24, y, 23, firstButton + i);
            if (into != null) into.add(b);
        }
    }

    private Button addButton(String label, int x, int y, int width, int id) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> press(id)).bounds(x, y, width, 18).build());
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        int mode = menu.get(ControllerBlockEntity.DATA_MODE);
        boolean badPv = (menu.get(ControllerBlockEntity.DATA_ALARMS) & ControllerBlockEntity.ALARM_BAD_PV) != 0;
        for (Button b : opButtons) b.active = mode == PidController.Mode.MAN.ordinal();
        for (int i = 0; i < 3; i++) modeButtons[i].active = i != mode && (i == 0 || !badPv) && (i != 2 || !menu.casTag().isEmpty());
        tuningButton.setMessage(Component.literal("TUNE " + PidController.Tuning.values()[menu.get(ControllerBlockEntity.DATA_TUNING)]));
        actionButton.setMessage(Component.literal(PidController.Action.values()[menu.get(ControllerBlockEntity.DATA_ACTION)] + " ACTING"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, FRAME);
        g.fill(x + 2, y + 2, x + W - 2, y + H - 2, PANEL);

        int alarms = menu.get(ControllerBlockEntity.DATA_ALARMS);
        int pv = menu.get(ControllerBlockEntity.DATA_PV);
        // PV bar with SP marker and HI / LO limits
        int bx = x + BAR_X, by = y + BAR_Y;
        g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, FRAME);
        g.fill(bx, by, bx + BAR_W, by + BAR_H, WELL);
        if (pv >= 0) {
            int fill = scale(pv, BAR_H);
            g.fill(bx, by + BAR_H - fill, bx + BAR_W, by + BAR_H, (alarms & (ControllerBlockEntity.ALARM_HI | ControllerBlockEntity.ALARM_LO)) != 0 ? ALARM : BAR);
        }
        int spY = by + BAR_H - scale(menu.get(ControllerBlockEntity.DATA_SP), BAR_H);
        g.fill(bx - 4, spY - 1, bx + BAR_W + 4, spY + 1, SP);
        for (int limit : new int[]{menu.get(ControllerBlockEntity.DATA_HI), menu.get(ControllerBlockEntity.DATA_LO)}) {
            int ly = by + BAR_H - scale(limit, BAR_H);
            g.fill(bx + BAR_W + 2, ly, bx + BAR_W + 7, ly + 1, LIMIT);
        }

        // OP bar and valve position
        int ox = x + 52, oy = y + 120, ow = 84;
        g.fill(ox - 1, oy - 1, ox + ow + 1, oy + 7, FRAME);
        g.fill(ox, oy, ox + ow, oy + 6, WELL);
        g.fill(ox, oy, ox + scale(menu.get(ControllerBlockEntity.DATA_OP), ow), oy + 6, BAR);
        int valve = menu.get(ControllerBlockEntity.DATA_VALVE);
        g.fill(ox - 1, oy + 15, ox + ow + 1, oy + 20, FRAME);
        g.fill(ox, oy + 16, ox + ow, oy + 19, WELL);
        if (valve >= 0) g.fill(ox, oy + 16, ox + scale(valve, ow), oy + 19, VALVE);
    }

    private static int scale(int tenthsOfPercent, int pixels) {
        return Math.max(0, Math.min(pixels, Math.round(tenthsOfPercent / 1000f * pixels)));
    }

    private static String percent(int tenths) {
        return tenths < 0 ? "----" : String.format("%.1f", tenths / 10.0);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int alarms = menu.get(ControllerBlockEntity.DATA_ALARMS);
        int mode = menu.get(ControllerBlockEntity.DATA_MODE);
        g.text(font, menu.tag(), 8, 8, TEXT, false);
        g.text(font, PidController.Mode.values()[mode].name(), 100, 8, mode == PidController.Mode.MAN.ordinal() ? LIMIT : TEXT, false);
        String alarm = (alarms & ControllerBlockEntity.ALARM_BAD_PV) != 0 ? "BAD PV"
                : (alarms & ControllerBlockEntity.ALARM_HI) != 0 ? "PV HI"
                : (alarms & ControllerBlockEntity.ALARM_LO) != 0 ? "PV LO" : "";
        if (!alarm.isEmpty()) g.text(font, alarm, 8, 20, ALARM, false);

        g.text(font, "PV", 52, 36, DIM, false);
        g.text(font, percent(menu.get(ControllerBlockEntity.DATA_PV)) + " %", 72, 36, alarms != 0 ? ALARM : TEXT, false);
        g.text(font, "SP", 52, 54, DIM, false);
        g.text(font, percent(menu.get(ControllerBlockEntity.DATA_SP)) + " %", 72, 54, TEXT, false);
        g.text(font, "OP", 52, 72, DIM, false);
        g.text(font, percent(menu.get(ControllerBlockEntity.DATA_OP)) + " %", 72, 72, TEXT, false);
        g.text(font, "OUTPUT", 52, 110, DIM, false);
        // a pressure loop moves the boiler's burner, not a valve
        g.text(font, (menu.mvTag().startsWith("B-") ? "FIRING " : "VALVE ") + percent(menu.get(ControllerBlockEntity.DATA_VALVE)) + " %", 52, 140, DIM, false);

        // labels next to the button rows (rows start at y 26 and are 22 apart)
        g.text(font, "SP", 128, 55, DIM, false);
        g.text(font, "OP", 128, 77, DIM, false);
        g.text(font, "HI", 146, 143, LIMIT, false);
        g.text(font, "LO", 194, 143, LIMIT, false);
        g.text(font, "HI " + percent(menu.get(ControllerBlockEntity.DATA_HI)) + "  LO " + percent(menu.get(ControllerBlockEntity.DATA_LO)), 146, 160, DIM, false);

        String links = "PV " + dash(menu.pvTag()) + "   MV " + dash(menu.mvTag()) + "   CAS " + dash(menu.casTag());
        g.text(font, links, 8, H - 14, DIM, false);
    }

    private static String dash(String tag) {
        return tag.isEmpty() ? "-" : tag;
    }
}
