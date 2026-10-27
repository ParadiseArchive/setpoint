package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.BurnerBlockEntity;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An oil and gas burner, placed under a boiler: it fires the boiler on liquid fuel from a pipe or on gas from a gas
 * line, when the boiler has no coal burning. Pipes and gas pipes join it at the back and on both sides.
 */
public class BurnerBlock extends GasMakerBlock implements VesselBlock {
    public BurnerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean gasPort(BlockState state, Direction side) {
        return side.getAxis().isHorizontal() && side != state.getValue(FACING);
    }

    @Override
    public boolean takesPipe(BlockState state, Direction side) {
        return side != state.getValue(FACING) && side != Direction.UP;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.BURNER_ENTITY.get(), BurnerBlockEntity::serverTick);
    }
}
