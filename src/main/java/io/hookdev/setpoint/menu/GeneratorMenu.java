package io.hookdev.setpoint.menu;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/** Fuel slot, burn time and FE buffer of a Stirling engine or biogas generator. */
public class GeneratorMenu extends MachineMenu {
    public static final int FUEL_SLOT_X = 26, FUEL_SLOT_Y = 46;

    private final String fuelHint;
    private final int output;

    /** Client side: the server sends what to burn and how much it makes. */
    public GeneratorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new ItemStacksResourceHandler(1), new SimpleContainerData(GeneratorBlockEntity.DATA_COUNT),
                null, buf.readUtf(), buf.readVarInt());
    }

    /** Server side. */
    public GeneratorMenu(int containerId, Inventory inventory, GeneratorBlockEntity generator) {
        this(containerId, inventory, generator.input(), generator.data(), generator, generator.fuelName(), generator.output());
    }

    private GeneratorMenu(int containerId, Inventory inventory, ItemStacksResourceHandler input, ContainerData data,
                          @Nullable GeneratorBlockEntity generator, String fuelHint, int output) {
        super(SetpointContent.GENERATOR_MENU.get(), containerId, 1, data,
                generator == null ? null : generator.getLevel(),
                generator == null ? BlockPos.ZERO : generator.getBlockPos(),
                generator == null ? null : generator.getBlockState().getBlock());
        this.fuelHint = fuelHint;
        this.output = output;
        addSlot(new ResourceHandlerSlot(input, input::set, 0, FUEL_SLOT_X, FUEL_SLOT_Y));
        addPlayerInventory(inventory);
    }

    public String fuelHint() { return fuelHint; }
    public int output() { return output; }
}
