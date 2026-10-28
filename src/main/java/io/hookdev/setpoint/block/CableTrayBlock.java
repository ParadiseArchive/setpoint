package io.hookdev.setpoint.block;

import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cable tray: a galvanised steel tray with power cables in it. It is a power cable itself, only wider and tidier:
 * it joins cables, trays and every block with FE the same way.
 */
public class CableTrayBlock extends PowerCableBlock {
    private static final VoxelShape CORE = Block.box(2, 4, 2, 14, 8, 14);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(2, 4, 0, 14, 8, 2),
            Direction.SOUTH, Block.box(2, 4, 14, 14, 8, 16),
            Direction.WEST, Block.box(0, 4, 2, 2, 8, 14),
            Direction.EAST, Block.box(14, 4, 2, 16, 8, 14),
            Direction.DOWN, Block.box(4, 0, 4, 12, 4, 12),
            Direction.UP, Block.box(4, 8, 4, 12, 16, 12));

    private final Function<BlockState, VoxelShape> trayShapes;

    public CableTrayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.trayShapes = this.getShapeForEachState(CableTrayBlock::makeShape);
    }

    private static VoxelShape makeShape(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(direction))) shape = Shapes.or(shape, ARMS.get(direction));
        }
        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return trayShapes.apply(state);
    }
}
