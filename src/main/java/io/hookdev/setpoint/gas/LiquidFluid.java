package io.hookdev.setpoint.gas;

import java.util.function.Supplier;

import net.neoforged.neoforge.fluids.FluidType;

/** A process liquid (crude oil, naphtha, diesel...): it lives in tanks, drums and liquid pipes like water. */
public class LiquidFluid extends VirtualFluid {
    public LiquidFluid(Supplier<FluidType> type) {
        super(type);
    }
}
