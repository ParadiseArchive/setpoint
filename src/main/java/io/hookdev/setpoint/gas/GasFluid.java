package io.hookdev.setpoint.gas;

import java.util.function.Supplier;

import net.neoforged.neoforge.fluids.FluidType;

/** A gas: it lives in gas vessels and moves through gas pipes. */
public class GasFluid extends VirtualFluid {
    public GasFluid(Supplier<FluidType> type) {
        super(type);
    }
}
