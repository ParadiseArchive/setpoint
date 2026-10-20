package io.hookdev.setpoint.client;

import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;
import io.hookdev.setpoint.menu.GeneratorMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Stirling engine / biogas generator screen. Left: fuel slot with a flame showing the burn left.
 * Middle: status display. Right: FE buffer gauge.
 */
public class GeneratorScreen extends MachineScreen<GeneratorMenu> {
    private static final int FLAME_X = 29, FLAME_Y = 26, FLAME = 12;
    private static final int INFO_X = 50, INFO_Y = 16, INFO_W = 96, INFO_H = 58;
    private static final int GAUGE_X = 152, GAUGE_Y = 16, GAUGE_W = 16, GAUGE_H = 58;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractMachine(GuiGraphicsExtractor g, int x, int y) {
        // Flame: how much of the current fuel item is left
        int left = menu.get(GeneratorBlockEntity.DATA_RUN_LEFT), total = menu.get(GeneratorBlockEntity.DATA_RUN_TOTAL);
        int flame = total > 0 ? (int) Math.ceil(FLAME * (double) left / total) : 0;
        g.fill(x + FLAME_X, y + FLAME_Y, x + FLAME_X + FLAME, y + FLAME_Y + FLAME, 0xFF9A9A9A);
        if (flame > 0) {
            int top = y + FLAME_Y + FLAME - flame;
            g.fill(x + FLAME_X, top, x + FLAME_X + FLAME, y + FLAME_Y + FLAME, 0xFFE0661F);
            g.fill(x + FLAME_X + 3, Math.max(top, y + FLAME_Y + 4), x + FLAME_X + FLAME - 3, y + FLAME_Y + FLAME, 0xFFFFC04A);
        }

        display(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H);
        int energy = menu.get(GeneratorBlockEntity.DATA_ENERGY);
        verticalGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, energy / (double) GeneratorBlockEntity.ENERGY_CAPACITY, CYAN);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        boolean running = menu.get(GeneratorBlockEntity.DATA_RUNNING) == 1;
        boolean hasFuel = menu.slots.get(0).hasItem() || menu.get(GeneratorBlockEntity.DATA_RUN_LEFT) > 0;
        int energy = menu.get(GeneratorBlockEntity.DATA_ENERGY);
        int tx = INFO_X + 5, ty = INFO_Y + 5;
        if (running) {
            g.text(font, "RUNNING", tx, ty, CYAN, false);
        } else if (!hasFuel) {
            g.text(font, "ADD FUEL", tx, ty, AMBER, false);
        } else {
            g.text(font, "PAUSED", tx, ty, DIM, false);
        }
        String detail = running ? "making power" : !hasFuel ? "put " + menu.fuelHint() + " in" : "buffer is full";
        g.text(font, detail, tx, ty + 11, DIM, false);
        g.text(font, (running ? menu.output() : 0) + " FE/t", tx, ty + 26, TEXT, false);
        g.text(font, fe(energy) + " FE", tx, ty + 38, TEXT, false);
    }
}
