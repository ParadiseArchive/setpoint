package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.FuelCellBlockEntity;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Fuel cell: hydrogen on your left, oxygen on your right; power to every block it touches, water out at the back or below. */
public class FuelCellBlock extends GasMakerBlock implements VesselBlock {
    public FuelCellBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean gasPort(BlockState state, Direction side) {
        return side == left(state) || side == right(state);
    }

    @Override
    public boolean takesPipe(BlockState state, Direction side) {
        return side == state.getValue(FACING).getOpposite() || side == Direction.DOWN;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelCellBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.FUEL_CELL_ENTITY.get(), FuelCellBlockEntity::serverTick);
    }
}
