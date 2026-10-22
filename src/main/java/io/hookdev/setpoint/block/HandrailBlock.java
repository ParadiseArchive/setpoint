package io.hookdev.setpoint.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A yellow handrail along the edge of a platform, the kind round the top of the gas sphere. It stands on the side of
 * the block you look towards as you place it; place another in the same block to rail a second side, for a corner.
 * It is a block and a half high to walk into, so nobody jumps over it. It shares its block with a grating in the bottom
 * half ({@link #FLOOR}): so a walkway on beams gets its floor and its handrails in the same block.
 */
public class HandrailBlock extends Block {
    public static final Map<Direction, BooleanProperty> EDGES = Map.of(Direction.NORTH, PipeBlock.NORTH, Direction.EAST, PipeBlock.EAST,
            Direction.SOUTH, PipeBlock.SOUTH, Direction.WEST, PipeBlock.WEST);
    /** A steel grating in the bottom of this block, under the handrail. */
    public static final BooleanProperty FLOOR = BooleanProperty.create("floor");
    private static final VoxelShape FLOOR_SHAPE = Block.box(0, 0, 0, 16, 2, 16);
    private static final Map<Direction, VoxelShape> OUTLINE = Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, 2), Direction.SOUTH, Block.box(0, 0, 14, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, 2, 16, 16), Direction.EAST, Block.box(14, 0, 0, 16, 16, 16));
    private static final Map<Direction, VoxelShape> WALL = Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 24, 2), Direction.SOUTH, Block.box(0, 0, 14, 16, 24, 16),
            Direction.WEST, Block.box(0, 0, 0, 2, 24, 16), Direction.EAST, Block.box(14, 0, 0, 16, 24, 16));

    public HandrailBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any();
        for (BooleanProperty edge : EDGES.values()) state = state.setValue(edge, false);
        this.registerDefaultState(state.setValue(FLOOR, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        BlockState state = here.is(this) ? here : this.defaultBlockState()
                .setValue(FLOOR, here.getBlock() instanceof GratingBlock && here.getValue(GratingBlock.HALF) == Half.BOTTOM);
        return state.setValue(EDGES.get(context.getHorizontalDirection()), true);
    }

    /** Another handrail fits into this block, on a side that has none yet; so does a grating under it, if there is none. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (context.getItemInHand().getItem() instanceof net.minecraft.world.item.BlockItem item && item.getBlock() instanceof GratingBlock) return !state.getValue(FLOOR);
        return context.getItemInHand().is(this.asItem()) && !state.getValue(EDGES.get(context.getHorizontalDirection()));
    }

    private static VoxelShape shape(BlockState state, Map<Direction, VoxelShape> parts) {
        VoxelShape shape = state.getValue(FLOOR) ? FLOOR_SHAPE : Shapes.empty();
        for (var edge : EDGES.entrySet()) if (state.getValue(edge.getValue())) shape = Shapes.or(shape, parts.get(edge.getKey()));
        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state, OUTLINE);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state, WALL);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (var edge : EDGES.entrySet()) turned = turned.setValue(EDGES.get(rotation.rotate(edge.getKey())), state.getValue(edge.getValue()));
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (var edge : EDGES.entrySet()) mirrored = mirrored.setValue(EDGES.get(mirror.mirror(edge.getKey())), state.getValue(edge.getValue()));
        return mirrored;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        EDGES.values().forEach(builder::add);
        builder.add(FLOOR);
    }
}
