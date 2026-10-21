package io.hookdev.setpoint.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A block whose thing is bigger than one block (an IBC, a drum, a pallet): its other blocks are {@link MultiblockPartBlock}s. */
public interface MultiblockBase {
    /** The other blocks it takes up right now, besides this base block. */
    List<BlockPos> parts(BlockPos base, BlockState state);
}
