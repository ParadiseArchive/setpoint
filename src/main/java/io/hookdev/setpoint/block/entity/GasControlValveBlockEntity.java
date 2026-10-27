package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A pressure control valve: a control valve in a gas line, tagged PV. */
public class GasControlValveBlockEntity extends ControlValveBlockEntity {
    public GasControlValveBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.GAS_CONTROL_VALVE_ENTITY.get(), pos, state, "PV");
    }
}
