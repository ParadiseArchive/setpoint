package io.hookdev.setpoint.block.entity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.CompressorBlock;
import io.hookdev.setpoint.gas.GasRun;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.physics.CompressorModel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A gas compressor: it takes gas from the line on its suction side (the side that faced you as you placed it) and
 * pushes it into the line on its discharge side, up to {@link CompressorModel#MAX_DISCHARGE} bar. It draws from the
 * fullest vessel on the suction line and fills the emptiest one on the discharge line that takes that gas.
 */
public class CompressorBlockEntity extends BlockEntity implements NamedEquipment {
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CompressorModel.ENERGY_CAPACITY, CompressorModel.ENERGY_MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final EquipmentName name = new EquipmentName("K");
    private final List<GasRun.End> suctionLine = new ArrayList<>(), dischargeLine = new ArrayList<>();
    private String status = "IDLE";
    private int flow;
    private double suction, discharge;

    public CompressorBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.COMPRESSOR_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    /** RUNNING, or why not: NO POWER, NO GAS, FULL, NO LINE. */
    public String status() { return status; }
    /** Gas moved in the last tick, mB at 1 bar. */
    public int flow() { return flow; }
    public double suctionPressure() { return suction; }
    public double dischargePressure() { return discharge; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CompressorBlockEntity compressor) {
        compressor.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction out = state.getValue(CompressorBlock.FACING);
        if (level.getGameTime() % 20 == 0) {
            suctionLine.clear();
            suctionLine.addAll(GasRun.traceAll(level, pos, out.getOpposite()));
            dischargeLine.clear();
            dischargeLine.addAll(GasRun.traceAll(level, pos, out));
        }
        flow = 0;
        GasVessel from = fullest(level);
        GasVessel to = from == null ? null : emptiest(level, from.gas());
        suction = from == null ? 0 : from.pressure();
        discharge = to == null ? 0 : to.pressure();
        if (suctionLine.isEmpty() || dischargeLine.isEmpty()) status = "NO LINE";
        else if (from == null || CompressorModel.flow(from.pressure()) == 0) status = "NO GAS";
        else if (to == null) status = "FULL";
        else {
            int wanted = CompressorModel.flow(from.pressure());
            int power = CompressorModel.power(wanted, from.pressure(), Math.max(to.pressure(), 1));
            if (energy.getAmountAsInt() < power) status = "NO POWER";
            else {
                FluidResource gas = from.gas();
                int room = (int) Math.max(0, Math.min(to.maxPressure(), CompressorModel.MAX_DISCHARGE) * to.volume() - to.amount());
                int taken = from.extract(Math.min(wanted, room));
                int put = to.insert(gas, taken);
                if (put < taken) from.insert(gas, taken - put);
                flow = put;
                energy.set(energy.getAmountAsInt() - power);
                status = "RUNNING";
            }
        }
        boolean running = flow > 0;
        if (state.getValue(CompressorBlock.RUNNING) != running) level.setBlock(pos, state.setValue(CompressorBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        if (running && (level.getGameTime() + pos.hashCode()) % 40 == 0) {
            level.playSound(null, pos, SetpointContent.PUMP_RUN.get(), SoundSource.BLOCKS, 0.25f, 0.55f); // a deep thud of the pistons
        }
    }

    /** The vessel on the suction line with the most pressure, if any has gas. */
    private @Nullable GasVessel fullest(Level level) {
        GasVessel best = null;
        for (GasRun.End end : suctionLine) {
            GasVessel vessel = end.vessel(level);
            if (vessel != null && vessel.amount() > 0 && (best == null || vessel.pressure() > best.pressure())) best = vessel;
        }
        return best;
    }

    /** The vessel on the discharge line with the least pressure that takes this gas and has room. */
    private @Nullable GasVessel emptiest(Level level, FluidResource gas) {
        GasVessel best = null;
        for (GasRun.End end : dischargeLine) {
            GasVessel vessel = end.vessel(level);
            if (vessel == null || !vessel.canTake(gas)) continue;
            if (vessel.pressure() >= Math.min(vessel.maxPressure(), CompressorModel.MAX_DISCHARGE)) continue;
            if (best == null || vessel.pressure() < best.pressure()) best = vessel;
        }
        return best;
    }

    /** Right-click with an empty hand: what it is doing. */
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("in %.1f bar, out %.1f bar, %d mB/t", suction, discharge, flow)).withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        name.load(input);
    }
}
