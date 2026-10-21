package io.hookdev.setpoint.gas;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Gas goes from high pressure to low by itself. Kept by every gas vessel with ports: every second it looks where its
 * pipes lead, and every tick it lets gas out to every vessel there at a lower pressure, faster the bigger the
 * difference and the shorter the pipe, but never so much that the other ends up higher (they meet in the middle).
 */
public final class GasFlow {
    /** mB per tick for each bar of difference, through a pipe of no length. */
    public static final double CONDUCTANCE = 400;
    /** Every this many blocks of pipe halves the flow again. */
    public static final double HALF_LENGTH = 32;

    private final List<GasRun.End> ends = new ArrayList<>();
    private long nextScan;
    /** Gas let out in the last tick, mB. */
    private int lastFlow;

    public int lastFlow() { return lastFlow; }

    /** Pushes gas from {@code from}, out of these sides of the block at pos. */
    public void tick(ServerLevel level, BlockPos pos, GasVessel from, Direction... sides) {
        if (level.getGameTime() >= nextScan) {
            nextScan = level.getGameTime() + 20;
            ends.clear();
            for (Direction side : sides) ends.addAll(GasRun.traceAll(level, pos, side));
        }
        lastFlow = 0;
        for (GasRun.End end : ends) {
            GasVessel to = end.vessel(level);
            if (to == null || to == from) continue;
            lastFlow += push(from, to, end.length());
        }
    }

    /** Lets gas go from one vessel to the other through this much pipe; returns how much went. */
    public static int push(GasVessel from, @Nullable GasVessel to, int length) {
        if (to == null || from.amount() == 0) return 0;
        double drop = from.pressure() - to.pressure();
        if (drop <= 0 || !to.canTake(from.gas())) return 0;
        // where both stand at the same pressure
        long even = ((long) from.amount() * to.volume() - (long) to.amount() * from.volume()) / (from.volume() + to.volume());
        double rate = CONDUCTANCE * drop / (1 + length / HALF_LENGTH);
        int wanted = (int) Math.min(even, Math.ceil(rate));
        if (wanted <= 0) return 0;
        var gas = from.gas();
        int taken = from.extract(wanted);
        int put = to.insert(gas, taken);
        if (put < taken) from.insert(gas, taken - put); // what did not fit goes back
        return put;
    }
}
