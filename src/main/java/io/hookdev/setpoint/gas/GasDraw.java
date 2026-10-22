package io.hookdev.setpoint.gas;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A machine that uses gas from a gas line (a fuel cell, a reactor, a boiler burning with oxygen): every second it looks
 * where the line leads, and it takes its gas from the fullest vessel on it that holds the right gas.
 */
public final class GasDraw {
    private final List<GasRun.End> ends = new ArrayList<>();
    private long nextScan;

    /** The vessel with the most pressure on the line out of this side that holds this gas, if any. */
    public @Nullable GasVessel fullest(Level level, BlockPos pos, Direction side, FluidResource gas) {
        if (level.getGameTime() >= nextScan) {
            nextScan = level.getGameTime() + 20;
            ends.clear();
            ends.addAll(GasRun.traceAll(level, pos, side));
        }
        GasVessel best = null;
        for (GasRun.End end : ends) {
            GasVessel vessel = end.vessel(level);
            if (vessel != null && vessel.amount() > 0 && gas.equals(vessel.gas()) && (best == null || vessel.pressure() > best.pressure())) best = vessel;
        }
        return best;
    }

    /** Takes up to this much of the gas from the line; returns how much it got. */
    public int take(Level level, BlockPos pos, Direction side, FluidResource gas, int amount) {
        GasVessel from = fullest(level, pos, side, gas);
        return from == null ? 0 : from.extract(amount);
    }
}
