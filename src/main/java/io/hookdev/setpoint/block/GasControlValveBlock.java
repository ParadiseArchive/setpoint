package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.GasControlValveBlockEntity;
import io.hookdev.setpoint.gas.GasLine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pressure control valve (PCV) for a gas line. A controller against a pressure transmitter on the line drives it,
 * like a control valve in a liquid line: the gas goes through as far as it is open.
 */
public class GasControlValveBlock extends ControlValveBlock implements GasLine {
    public GasControlValveBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasControlValveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.GAS_CONTROL_VALVE_ENTITY.get(), ControlValveBlockEntity::serverTick);
    }

    @Override
    public Direction.Axis gasAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public double opening(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof ControlValveBlockEntity valve ? valve.position() : 0;
    }
}
