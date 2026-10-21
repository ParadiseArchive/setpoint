package io.hookdev.setpoint.menu;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Faceplate of a controller. No item slots: the live values travel as container data and the
 * buttons use the vanilla container button packet, so no custom networking is needed.
 */
public class FaceplateMenu extends AbstractContainerMenu {
    private final @Nullable ControllerBlockEntity controller; // server side only
    private final BlockPos pos;
    private final BlockPos station; // where the player must stay: the controller, or the console it was opened from
    private final ContainerData data;
    private final String tag, pvTag, mvTag, casTag;

    /** Client side: built from the data the server sent when the menu opened. */
    public FaceplateMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(SetpointContent.FACEPLATE.get(), containerId);
        this.controller = null;
        this.pos = buf.readBlockPos();
        this.station = pos;
        this.tag = buf.readUtf();
        this.pvTag = buf.readUtf();
        this.mvTag = buf.readUtf();
        this.casTag = buf.readUtf();
        this.data = new SimpleContainerData(ControllerBlockEntity.DATA_COUNT);
        addDataSlots(data);
    }

    /** Server side, opened at the controller. */
    public FaceplateMenu(int containerId, Inventory inventory, ControllerBlockEntity controller) {
        this(containerId, inventory, controller, controller.getBlockPos());
    }

    /** Server side, opened from a DCS console: the player stays at the console. */
    public FaceplateMenu(int containerId, Inventory inventory, ControllerBlockEntity controller, BlockPos station) {
        super(SetpointContent.FACEPLATE.get(), containerId);
        this.controller = controller;
        this.pos = controller.getBlockPos();
        this.station = station;
        this.tag = controller.tag();
        this.pvTag = controller.pvTag();
        this.mvTag = controller.mvTag();
        this.casTag = controller.casTag();
        this.data = controller.data();
        addDataSlots(data);
    }

    public int get(int index) { return data.get(index); }
    public String tag() { return tag; }
    public String pvTag() { return pvTag; }
    public String mvTag() { return mvTag; }
    public String casTag() { return casTag; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (controller != null) controller.handleButton(buttonId);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (controller == null) return true;
        return !controller.isRemoved() && player.distanceToSqr(station.getX() + 0.5, station.getY() + 0.5, station.getZ() + 0.5) <= 64;
    }
}
