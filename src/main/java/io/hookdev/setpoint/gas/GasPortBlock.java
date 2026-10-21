package io.hookdev.setpoint.gas;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A block with gas connections: gas pipes join it on these sides. */
public interface GasPortBlock {
    /** Does a gas pipe join on this side (the side of this block that faces the pipe)? */
    boolean gasPort(BlockState state, Direction side);

    /** The gas vessel behind a port of a block entity, if it has one there. */
    static @Nullable GasVessel vesselAt(@Nullable BlockEntity be, Direction side) {
        return be instanceof Ports ports ? ports.port(side) : null;
    }

    /** A block entity behind gas ports: which vessel each port leads to (null for a port that only takes, like a turbine's). */
    interface Ports {
        @Nullable GasVessel port(Direction side);
    }
}
