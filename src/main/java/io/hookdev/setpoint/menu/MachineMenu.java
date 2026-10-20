package io.hookdev.setpoint.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Furnace-style machine screen: a few machine slots first, then the player's inventory at the usual place.
 * Shift-click moves items between the two, and only into slots that accept them.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    public static final int INVENTORY_X = 8, INVENTORY_Y = 84;

    private final int machineSlots;
    private final ContainerData data;
    private final @Nullable Level level;
    private final BlockPos pos;
    private final @Nullable Block block;

    protected MachineMenu(MenuType<?> type, int containerId, int machineSlots, ContainerData data,
                          @Nullable Level level, BlockPos pos, @Nullable Block block) {
        super(type, containerId);
        this.machineSlots = machineSlots;
        this.data = data;
        this.level = level;
        this.pos = pos;
        this.block = block;
        addDataSlots(data);
    }

    /** Call after adding the machine slots. */
    protected void addPlayerInventory(Inventory inventory) {
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < machineSlots
                ? moveItemStackTo(stack, machineSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, machineSlots, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || block == null) return true; // client side
        return stillValid(ContainerLevelAccess.create(level, pos), player, block);
    }
}
