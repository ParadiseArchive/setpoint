package io.hookdev.setpoint.menu;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.TankBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/** Level gauge and bucket slots of a tank. */
public class TankMenu extends MachineMenu {
    public static final int IN_X = 26, IN_Y = 20, OUT_X = 26, OUT_Y = 54;

    private final String tag;

    /** Client side. */
    public TankMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new ItemStacksResourceHandler(2), new SimpleContainerData(TankBlockEntity.DATA_COUNT), null, buf.readUtf());
    }

    /** Server side. */
    public TankMenu(int containerId, Inventory inventory, TankBlockEntity tank) {
        this(containerId, inventory, tank.buckets(), tank.data(), tank, "");
    }

    private TankMenu(int containerId, Inventory inventory, ItemStacksResourceHandler buckets, ContainerData data,
                     @Nullable TankBlockEntity tank, String tag) {
        super(SetpointContent.TANK_MENU.get(), containerId, 2, data,
                tank == null ? null : tank.getLevel(),
                tank == null ? BlockPos.ZERO : tank.getBlockPos(),
                tank == null ? null : tank.getBlockState().getBlock());
        this.tag = tag;
        addSlot(new ResourceHandlerSlot(buckets, buckets::set, TankBlockEntity.SLOT_IN, IN_X, IN_Y));
        addSlot(new ResourceHandlerSlot(buckets, buckets::set, TankBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false; // output only
            }
        });
        addPlayerInventory(inventory);
    }

    public String tag() {
        return tag;
    }
}
