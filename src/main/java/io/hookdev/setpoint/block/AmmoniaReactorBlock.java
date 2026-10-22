package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.AmmoniaReactorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Ammonia reactor: nitrogen in on your left, hydrogen on your right, ammonia out on top. */
public class AmmoniaReactorBlock extends GasMakerBlock {
    public AmmoniaReactorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean gasPort(BlockState state, Direction side) {
        return side == left(state) || side == right(state) || side == Direction.UP;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoniaReactorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.AMMONIA_REACTOR_ENTITY.get(), AmmoniaReactorBlockEntity::serverTick);
    }
}
