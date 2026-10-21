package io.hookdev.setpoint.gas;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Something that holds gas under pressure: a gas cylinder, the outlet of an electrolyser, a boiler drum's steam.
 * Gas is counted in mB at 1 bar; the pressure is how much gas there is over how much room ({@link #volume()}).
 */
public interface GasVessel {
    /** Which gas is in it, or empty. */
    FluidResource gas();

    /** How much, in mB at 1 bar. */
    int amount();

    /** How much room: this much gas makes 1 bar. */
    int volume();

    /** The most pressure a pipe may push it up to, in bar. */
    double maxPressure();

    /** Pressure in bar (0 when empty). */
    default double pressure() {
        return amount() / (double) volume();
    }

    /** Does this gas go in? Only into an empty vessel or one with the same gas, and not into a source like a boiler. */
    boolean canTake(FluidResource gas);

    /** Puts up to {@code amount} in; returns how much went. */
    int insert(FluidResource gas, int amount);

    /** Takes up to {@code amount} out; returns how much came. */
    int extract(int amount);
}
