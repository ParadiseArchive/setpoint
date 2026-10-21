package io.hookdev.setpoint.menu;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/** Fuel slot, drum level, temperature and steam of a boiler. */
public class BoilerMenu extends MachineMenu {
    public static final int FUEL_SLOT_X = 26, FUEL_SLOT_Y = 52;

    private final String tag;

    /** Client side: the server sends the boiler's tag. */
    public BoilerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new ItemStacksResourceHandler(1), new SimpleContainerData(BoilerBlockEntity.DATA_COUNT), null, buf.readUtf());
    }

    /** Server side. */
    public BoilerMenu(int containerId, Inventory inventory, BoilerBlockEntity boiler) {
        this(containerId, inventory, boiler.fuel(), boiler.data(), boiler, boiler.tag());
    }

    private BoilerMenu(int containerId, Inventory inventory, ItemStacksResourceHandler fuel, ContainerData data,
                       @Nullable BoilerBlockEntity boiler, String tag) {
        super(SetpointContent.BOILER_MENU.get(), containerId, 1, data,
                boiler == null ? null : boiler.getLevel(),
                boiler == null ? BlockPos.ZERO : boiler.getBlockPos(),
                boiler == null ? null : boiler.getBlockState().getBlock());
        this.tag = tag;
        addSlot(new ResourceHandlerSlot(fuel, fuel::set, 0, FUEL_SLOT_X, FUEL_SLOT_Y));
        addPlayerInventory(inventory);
    }

    public String tag() { return tag; }
}
