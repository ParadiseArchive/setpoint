package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.gas.GasRun;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.hydraulics.PressureVessel;
import io.hookdev.setpoint.physics.BoilerModel;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Measures the pressure of the closed vessel it touches, like a boiler drum, or of a gas line it sits against: then
 * the highest pressure of what that line reaches without going through a pressure control valve. Range 0 to 15 bar.
 */
public class PressureTransmitterBlockEntity extends InstrumentBlockEntity {
    /** 100 % of the measuring range, in bar. */
    public static final double RANGE = BoilerModel.PRESSURE_RANGE;

    public PressureTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.PRESSURE_TRANSMITTER_ENTITY.get(), pos, state, "PT");
    }

    public @Nullable PressureVessel vessel() {
        if (level == null) return null;
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof PressureVessel vessel) return vessel;
        }
        return null;
    }

    /** The gas pipe (or gas valve) this transmitter sits against, when it is not on a closed vessel. */
    public @Nullable BlockPos gasLine() {
        if (level == null || vessel() != null) return null;
        for (Direction direction : Direction.values()) {
            BlockPos at = worldPosition.relative(direction);
            if (GasRun.isGasPiece(level.getBlockState(at))) return at;
        }
        return null;
    }

    /** Pressure in bar, or null when the transmitter is not on a closed vessel or a gas line (a bad PV). */
    public @Nullable Double bar() {
        PressureVessel vessel = vessel();
        if (vessel != null) return vessel.pressure();
        BlockPos line = gasLine();
        if (line == null) return null;
        double highest = 0;
        for (GasRun.End end : GasRun.lineAt(level, line)) {
            GasVessel gas = end.vessel(level);
            if (gas != null) highest = Math.max(highest, gas.pressure());
        }
        return highest;
    }

    /** Pressure in percent of the range, or null. */
    public @Nullable Double percent() {
        Double bar = bar();
        return bar == null ? null : Math.min(100, bar / RANGE * 100.0);
    }

    @Override
    public void describe(Player player) {
        Double bar = bar();
        player.sendSystemMessage(Component.empty().append(tagLabel()).append(bar == null
                ? Component.literal("BAD PV  (not on a boiler, vessel or gas line)").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal(String.format("PRESSURE %.1f bar", bar)).withStyle(ChatFormatting.WHITE)));
    }
}
