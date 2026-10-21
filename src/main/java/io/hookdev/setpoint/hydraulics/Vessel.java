package io.hookdev.setpoint.hydraulics;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Anything that holds a liquid with a level: a tank, a boiler drum. Pumps fill and empty vessels,
 * controllers and level transmitters measure them.
 */
public interface Vessel {
    FluidStacksResourceHandler fluid();

    FluidResource resource();

    int amount();

    /** Level as a fraction of the capacity, 0 to 1. */
    double fillFraction();

    /** Height of the liquid surface in the world, in blocks. */
    default double surfaceY() {
        return getBlockPos().getY() + fillFraction();
    }

    boolean canAccept(FluidResource incoming);

    BlockPos getBlockPos();

    /** Comparator signal for a level: 0 when empty, 1 to 15 otherwise, like a vanilla container. */
    static int comparatorSignal(double fillFraction) {
        return fillFraction <= 0 ? 0 : Math.max(1, (int) Math.round(fillFraction * 15));
    }
}
