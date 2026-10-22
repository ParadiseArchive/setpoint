package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Diesel generator: diesel in at the back, the sides or below; power out to every block it touches. */
public class DieselGeneratorBlock extends GasMakerBlock implements VesselBlock {
    public DieselGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** It burns a liquid: no gas lines. */
    @Override
    public boolean gasPort(BlockState state, Direction side) {
        return false;
    }

    @Override
    public boolean takesPipe(BlockState state, Direction side) {
        return side != state.getValue(FACING) && side != Direction.UP;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DieselGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.DIESEL_GENERATOR_ENTITY.get(), DieselGeneratorBlockEntity::serverTick);
    }
}
