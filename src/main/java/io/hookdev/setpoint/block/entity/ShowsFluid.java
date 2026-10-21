package io.hookdev.setpoint.block.entity;

import net.minecraft.world.level.material.Fluid;

/** A block whose liquid you can see (a tank's sight glass, an IBC's tote): the client knows which liquid, for its colour. */
public interface ShowsFluid {
    Fluid shownFluid();
}
