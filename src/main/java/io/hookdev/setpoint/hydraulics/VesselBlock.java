package io.hookdev.setpoint.hydraulics;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Marker for vessel blocks (tank, boiler), so pipes connect to them and pipe runs end at them. */
public interface VesselBlock {
    /** Does a liquid pipe join on this side? (Most vessels take pipes on every side.) */
    default boolean takesPipe(BlockState state, Direction side) {
        return true;
    }
}
