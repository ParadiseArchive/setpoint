package io.hookdev.setpoint.client;

import io.hookdev.setpoint.menu.MachineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Base for furnace-style machine screens, drawn without textures: a familiar light grey Minecraft
 * panel with slot wells, plus dark "display" panels in the Hook style for live information.
 */
public abstract class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {
    // vanilla-like panel
    protected static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADE = 0xFF555555, EDGE = 0xFF000000,
            SLOT = 0xFF8B8B8B, SLOT_DARK = 0xFF373737, LABEL = 0xFF404040;
    // Hook display
    protected static final int DISPLAY = 0xFF12151B, DISPLAY_EDGE = 0xFF2B2F38, TEXT = 0xFFD9DEEA, DIM = 0xFF7C869D,
            CYAN = 0xFF2DE2E6, AMBER = 0xFFFFB547, MAGENTA = 0xFFFF4F8B;

    protected MachineScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) slotWell(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        extractMachine(g, leftPos, topPos);
    }

    /** Draw the machine specific part; x and y are the top-left corner of the screen. */
    protected abstract void extractMachine(GuiGraphicsExtractor g, int x, int y);

    protected static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, SHADE);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, SHADE);
    }

    protected static void slotWell(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT);
        g.fill(x, y, x + 17, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + 17, SLOT_DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }

    protected static void display(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DISPLAY_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, DISPLAY);
    }

    /** A vertical gauge filling from the bottom. */
    protected static void verticalGauge(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, int colour) {
        display(g, x, y, w, h);
        int fill = (int) Math.round(Math.max(0, Math.min(1, fraction)) * (h - 2));
        if (fill > 0) g.fill(x + 1, y + h - 1 - fill, x + w - 1, y + h - 1, colour);
    }

    protected static String fe(int amount) {
        return String.format("%,d", amount);
    }
}
