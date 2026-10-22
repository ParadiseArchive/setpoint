package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.gas.GasFlow;
import io.hookdev.setpoint.gas.GasPortBlock;
import io.hookdev.setpoint.gas.GasTank;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.hydraulics.PressureVessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A gas cylinder: a steel bottle for any one gas, its shoulder painted in the colour of what is in it. Gas pipes join it
 * on top and on its sides. It takes gas up to {@link #MAX_PRESSURE} bar and lets it out again to anything lower on the
 * line. Break it and the gas goes along in the item.
 */
public class GasCylinderBlockEntity extends BlockEntity implements GasPortBlock.Ports, PressureVessel, NamedEquipment, ShowsFluid {
    /** Its room: this much gas makes 1 bar (a real 50-litre cylinder, scaled up like everything here). */
    public static final int VOLUME = 1_000;
    /** What it is built for, in bar, like a real cylinder. */
    public static final double MAX_PRESSURE = 200.0;

    private final GasTank gas = new GasTank("gas", VOLUME, MAX_PRESSURE, this::onGasChanged);
    private final GasFlow flow = new GasFlow();
    private final EquipmentName name = new EquipmentName("GC");
    /** Client side: which gas is in it, for the colour of its shoulder. */
    private Fluid shownGas = Fluids.EMPTY;

    public GasCylinderBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.GAS_CYLINDER_ENTITY.get(), pos, state);
    }

    public GasTank tank() { return gas; }

    @Override
    public @Nullable GasVessel port(Direction side) {
        return side == Direction.DOWN ? null : gas;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GasCylinderBlockEntity cylinder) {
        cylinder.flow.tick((ServerLevel) level, pos, cylinder.gas, Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    }

    private Fluid lastGas = Fluids.EMPTY;

    private void onGasChanged() {
        setChanged();
        Fluid now = gas.gas().getFluid();
        if (level != null && !level.isClientSide() && !now.isSame(lastGas)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // a new colour
            lastGas = now;
        }
        if (level != null && !level.isClientSide()) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    // ---------- pressure: transmitters read it, safety valves let it off ----------
    @Override public double pressure() { return gas.pressure(); }
    @Override public double designPressure() { return MAX_PRESSURE; }
    @Override public int vent(int amount) { return gas.extract(amount); }
    @Override public net.neoforged.neoforge.transfer.fluid.FluidResource ventedGas() { return gas.gas(); }
    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    @Override public Fluid shownFluid() { return shownGas; }

    /** Right-click with an empty hand: what is in it. */
    public void describe(Player player) {
        Component what = gas.amount() == 0 ? Component.literal("EMPTY").withStyle(ChatFormatting.GRAY)
                : Component.literal(String.format("%s  %.1f bar  (%.1f B at 1 bar)", gas.gas().getFluidType().getDescription().getString(), gas.pressure(), gas.amount() / 1000.0))
                        .withStyle(ChatFormatting.WHITE);
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA).append(what));
    }

    // ---------- the gas goes along in the item ----------
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (gas.amount() > 0) components.set(SetpointContent.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(gas.gas().toStack(gas.amount())));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent contents = components.get(SetpointContent.FLUID_CONTENTS.get());
        if (contents != null && !contents.isEmpty() && Gases.isGas(FluidResource.of(contents.copy()))) {
            gas.set(FluidResource.of(contents.copy()), Math.min((int) (MAX_PRESSURE * VOLUME), contents.copy().getAmount()));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        gas.save(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        gas.load(input);
        name.load(input);
        lastGas = gas.gas().getFluid();
    }

    // ---------- players see which gas ----------
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("shown_gas", BuiltInRegistries.FLUID.getKey(gas.gas().getFluid()).toString());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        readShownGas(input);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readShownGas(input);
    }

    private void readShownGas(ValueInput input) {
        Identifier id = Identifier.tryParse(input.getStringOr("shown_gas", "minecraft:empty"));
        shownGas = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(id);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // redraw
    }
}
