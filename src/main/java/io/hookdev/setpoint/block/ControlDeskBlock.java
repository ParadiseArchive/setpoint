package io.hookdev.setpoint.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A control-room desk: a top, a panel at the back, a keyboard and a mouse. Desks in a row join into
 * one long desk (the side panels go where they meet), and a corner desk turns the row. Its front faces you when you put
 * it down: that is where the operator sits.
 */
public class ControlDeskBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Another desk on the left or right, as the operator sits at it: no side panel there. */
    public static final BooleanProperty LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right");
    /** The top of a desk, in pixels: a full block, so whatever you put on it (screens, a radio, the ESD) stands on it. */
    public static final int TOP = 16;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(Block.box(0, TOP - 2, 0, 16, TOP, 16), Block.box(0, 0, 14, 16, TOP - 2, 16)));

    public ControlDeskBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false));
    }

    /** Whether a block counts as part of a desk row, for joining. */
    public static boolean isDesk(BlockState state) {
        return state.getBlock() instanceof ControlDeskBlock || state.getBlock() instanceof DeskCornerBlock;
    }

    /** The operator's left, sitting at a desk that faces this way (its front towards them, so they look the other way). */
    public static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    private BlockState joined(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, isDesk(level.getBlockState(pos.relative(left(facing)))))
                .setValue(RIGHT, isDesk(level.getBlockState(pos.relative(left(facing).getOpposite()))));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joined(this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return direction.getAxis().isHorizontal() ? joined(state, level, pos) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT);
    }
}
