package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.EsdButtonBlock;
import io.hookdev.setpoint.control.Esd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the ESD list right: a pressed button counts again when its chunk loads, and stops counting when it goes. */
public class EsdButtonBlockEntity extends BlockEntity {
    public EsdButtonBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.ESD_BUTTON_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) Esd.set(level, worldPosition, getBlockState().getValue(EsdButtonBlock.PRESSED));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) Esd.set(level, worldPosition, false);
    }
}
