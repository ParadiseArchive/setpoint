package io.hookdev.setpoint.client;

import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.menu.TankMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Tank screen. Left: bucket in and bucket out. Middle: a big level gauge with eighth marks.
 * Right: contents in plain words.
 */
public class TankScreen extends MachineScreen<TankMenu> {
    private static final int GAUGE_X = 56, GAUGE_Y = 16, GAUGE_W = 30, GAUGE_H = 58;
    private static final int INFO_X = 94, INFO_Y = 16, INFO_W = 74, INFO_H = 58;

    public TankScreen(TankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** The whole column, in mB. */
    private int amount() {
        return menu.get(TankBlockEntity.DATA_AMOUNT) * 10;
    }

    private int capacity() {
        return Math.max(1, menu.get(TankBlockEntity.DATA_TANKS)) * TankBlockEntity.CAPACITY;
    }

    private Fluid fluid() {
        int id = menu.get(TankBlockEntity.DATA_FLUID);
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    private static int colour(Fluid fluid) {
        if (fluid.isSame(Fluids.LAVA)) return 0xFFE0661F;
        if (fluid.isSame(Fluids.WATER)) return 0xFF3A6FD8;
        return 0xFF8D95A5;
    }

    @Override
    protected void extractMachine(GuiGraphicsExtractor g, int x, int y) {
        verticalGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, amount() / (double) capacity(), colour(fluid()));
        for (int i = 1; i < 8; i++) { // eighth marks, like the sight glass
            int my = y + GAUGE_Y + GAUGE_H - 1 - Math.round(i * (GAUGE_H - 2) / 8f);
            g.fill(x + GAUGE_X + 1, my, x + GAUGE_X + (i % 4 == 0 ? 9 : 5), my + 1, 0x80FFFFFF);
        }
        // arrow from the bucket in to the bucket out
        g.fill(x + 33, y + 40, x + 37, y + 50, SHADE);
        g.fill(x + 31, y + 47, x + 39, y + 49, SHADE);

        display(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title.getString() + (menu.tag().isEmpty() ? "" : "  " + menu.tag()), titleLabelX, titleLabelY, LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        int amount = amount(), capacity = capacity();
        Fluid fluid = fluid();
        int tx = INFO_X + 5, ty = INFO_Y + 5;
        String name = amount == 0 ? "Empty" : fluid.getFluidType().getDescription().getString();
        g.text(font, name, tx, ty, amount == 0 ? DIM : TEXT, false);
        g.text(font, Math.round(amount * 100.0 / capacity) + " %", tx, ty + 14, CYAN, false);
        g.text(font, String.format("%.1f / %d B", amount / 1000.0, capacity / 1000), tx, ty + 28, TEXT, false);
        int tanks = menu.get(TankBlockEntity.DATA_TANKS);
        g.text(font, tanks > 1 ? tanks + " tanks high" : "bucket: top", tx, ty + 40, DIM, false);
    }
}
