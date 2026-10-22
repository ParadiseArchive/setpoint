package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it is there so the clock's renderer can turn the hands. */
public class WallClockBlockEntity extends BlockEntity {
    public WallClockBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.WALL_CLOCK_ENTITY.get(), pos, state);
    }
}
