package io.hookdev.setpoint.hydraulics;

import io.hookdev.setpoint.gas.Gases;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A closed vessel with pressure in it, like a boiler drum. Pressure transmitters read it and safety valves
 * vent it when it gets too high.
 */
public interface PressureVessel {
    /** Pressure in bar (gauge). */
    double pressure();

    /** The pressure it is built for, in bar: where its safety valve is normally set. */
    double designPressure();

    /** Lets up to {@code amount} mB out through a safety valve. Returns how much went. */
    int vent(int amount);

    /** What a safety valve on it lets out: steam from a boiler, the gas in a gas vessel. */
    default FluidResource ventedGas() {
        return Gases.STEAM.resource();
    }

    String tag();

    BlockPos getBlockPos();
}
