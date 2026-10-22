package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasSphereBlock;
import io.hookdev.setpoint.gas.GasFlow;
import io.hookdev.setpoint.gas.GasPortBlock;
import io.hookdev.setpoint.gas.GasTank;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.hydraulics.PressureVessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * What is in a gas sphere: one gas, in a room of {@link #VOLUME} mB, up to {@link #MAX_PRESSURE} bar. Gas comes in and
 * goes out through the valve station; a pressure transmitter reads it and a safety valve protects it. Break it and the
 * gas goes along in the item.
 */
public class GasSphereBlockEntity extends BlockEntity implements GasPortBlock.Ports, PressureVessel, NamedEquipment {
    /** Its room: this much gas makes 1 bar (the sphere is about 220 blocks inside). */
    public static final int VOLUME = 220_000;
    /** What it is built for, in bar, like a real propane sphere. */
    public static final double MAX_PRESSURE = 16.0;

    private final GasTank gas = new GasTank("gas", VOLUME, MAX_PRESSURE, this::onGasChanged);
    private final GasFlow flow = new GasFlow();
    private final EquipmentName name = new EquipmentName("TK");

    public GasSphereBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.GAS_SPHERE_ENTITY.get(), pos, state);
    }

    public GasTank tank() { return gas; }

    public Vec3 centre() {
        return GasSphereBlock.centre(worldPosition, getBlockState());
    }

    @Override
    public @Nullable GasVessel port(Direction side) {
        return side == Direction.DOWN ? null : gas;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GasSphereBlockEntity sphere) {
        sphere.flow.tick((ServerLevel) level, pos, sphere.gas, Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    }

    private void onGasChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

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

    /** Right-click the valve station with an empty hand: what is in it. */
    public void describe(Player player) {
        Component what = gas.amount() == 0 ? Component.literal("EMPTY").withStyle(ChatFormatting.GRAY)
                : Component.literal(String.format("%s  %.2f bar  (%.0f B at 1 bar)", gas.gas().getFluidType().getDescription().getString(), gas.pressure(), gas.amount() / 1000.0))
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
    }
}
