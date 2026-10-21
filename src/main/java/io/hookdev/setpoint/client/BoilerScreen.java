package io.hookdev.setpoint.client;

import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.menu.BoilerMenu;
import io.hookdev.setpoint.physics.BoilerModel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Boiler screen. Left: fuel slot with the flame above it. Then the drum level gauge with its trip mark,
 * the status display, and the steam gauge on the right.
 */
public class BoilerScreen extends MachineScreen<BoilerMenu> {
    private static final int FLAME_X = 29, FLAME_Y = 33, FLAME = 12;
    private static final int DRUM_X = 50, DRUM_Y = 16, DRUM_W = 18, DRUM_H = 58;
    private static final int INFO_X = 74, INFO_Y = 16, INFO_W = 72, INFO_H = 58;
    private static final int STEAM_X = 152, STEAM_Y = 16, STEAM_W = 16, STEAM_H = 58;
    private static final int WATER = 0xFF3A6FD8;

    public BoilerScreen(BoilerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private boolean flag(int flag) {
        return (menu.get(BoilerBlockEntity.DATA_FLAGS) & flag) != 0;
    }

    @Override
    protected void extractMachine(GuiGraphicsExtractor g, int x, int y) {
        int left = menu.get(BoilerBlockEntity.DATA_BURN_LEFT), total = menu.get(BoilerBlockEntity.DATA_BURN_TOTAL);
        int flame = total > 0 && flag(BoilerBlockEntity.FLAG_FIRING) ? (int) Math.ceil(FLAME * (double) left / total) : 0;
        g.fill(x + FLAME_X, y + FLAME_Y, x + FLAME_X + FLAME, y + FLAME_Y + FLAME, 0xFF9A9A9A);
        if (flame > 0) {
            int top = y + FLAME_Y + FLAME - flame;
            g.fill(x + FLAME_X, top, x + FLAME_X + FLAME, y + FLAME_Y + FLAME, 0xFFE0661F);
            g.fill(x + FLAME_X + 3, Math.max(top, y + FLAME_Y + 4), x + FLAME_X + FLAME - 3, y + FLAME_Y + FLAME, 0xFFFFC04A);
        }

        int water = menu.get(BoilerBlockEntity.DATA_WATER);
        verticalGauge(g, x + DRUM_X, y + DRUM_Y, DRUM_W, DRUM_H, water / (double) BoilerBlockEntity.WATER_CAPACITY,
                flag(BoilerBlockEntity.FLAG_OVERHEAT) ? MAGENTA : WATER);
        if (flag(BoilerBlockEntity.FLAG_PROTECTED)) { // the low-low trip point
            int ty = y + DRUM_Y + DRUM_H - 1 - (int) Math.round(BoilerModel.TRIP_LEVEL * (DRUM_H - 2));
            g.fill(x + DRUM_X + 1, ty, x + DRUM_X + DRUM_W - 1, ty + 1, AMBER);
        }

        display(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H);
        verticalGauge(g, x + STEAM_X, y + STEAM_Y, STEAM_W, STEAM_H,
                menu.get(BoilerBlockEntity.DATA_STEAM) / (double) BoilerBlockEntity.STEAM_CAPACITY, TEXT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title.getString() + "  " + menu.tag(), titleLabelX, titleLabelY, LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        boolean firing = flag(BoilerBlockEntity.FLAG_FIRING);
        int boiled = menu.get(BoilerBlockEntity.DATA_BOILED);
        double temperature = menu.get(BoilerBlockEntity.DATA_TEMPERATURE) / 10.0;
        boolean hasFuel = menu.slots.get(0).hasItem() || menu.get(BoilerBlockEntity.DATA_BURN_LEFT) > 0;
        int water = menu.get(BoilerBlockEntity.DATA_WATER);

        String status, detail;
        int colour;
        if (flag(BoilerBlockEntity.FLAG_OVERHEAT)) {
            status = "OVERHEAT"; detail = "drum is dry!"; colour = MAGENTA;
        } else if (flag(BoilerBlockEntity.FLAG_TRIPPED)) {
            status = "LOW WATER"; detail = "burner off"; colour = MAGENTA;
        } else if (firing && boiled > 0) {
            status = "STEAMING"; detail = boiled + " mB/t"; colour = CYAN;
        } else if (firing) {
            status = "HEATING"; detail = "warming up"; colour = TEXT;
        } else if (!hasFuel) {
            status = "ADD FUEL"; detail = "put coal in"; colour = AMBER;
        } else {
            status = "IDLE"; detail = "steam full"; colour = DIM;
        }
        int tx = INFO_X + 5, ty = INFO_Y + 5;
        g.text(font, status, tx, ty, colour, false);
        g.text(font, detail, tx, ty + 11, DIM, false);
        g.text(font, Math.round(temperature) + " °C", tx, ty + 26, temperature > BoilerModel.OVERHEAT ? MAGENTA : TEXT, false);
        g.text(font, "drum " + Math.round(water * 100.0 / BoilerBlockEntity.WATER_CAPACITY) + " %", tx, ty + 38, TEXT, false);
    }
}
