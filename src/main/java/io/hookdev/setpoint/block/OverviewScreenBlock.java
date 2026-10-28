package io.hookdev.setpoint.block;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.OverviewScreenBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Overview screen for the wall of a control room. Screens side by side and below each other make one video wall; it
 * shows what the nearest DCS console (within 32 blocks) sees: every tag at a glance, the alarm list, or the trends.
 * Right-click to switch page.
 */
public class OverviewScreenBlock extends SetpointEntityBlock {
    /** The way the screen faces: away from the wall. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int LIGHT = 7;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(0, 0, 14, 16, 16, 16), Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 2),
            Direction.EAST, Block.box(0, 0, 0, 2, 16, 16), Direction.WEST, Block.box(14, 0, 0, 16, 16, 16));

    public OverviewScreenBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return this.defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    /** The top-left screen of the wall this screen belongs to: it holds the page and the data. */
    public static BlockPos topLeft(BlockGetter level, BlockPos pos, Direction facing) {
        return WallPanel.topLeft(level, pos, SetpointContent.OVERVIEW_SCREEN.get(), facing);
    }

    public static int[] size(BlockGetter level, BlockPos topLeft, Direction facing) {
        return WallPanel.size(level, topLeft, SetpointContent.OVERVIEW_SCREEN.get(), facing);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos origin = topLeft(level, pos, state.getValue(FACING));
        if (!level.isClientSide() && level.getBlockEntity(origin) instanceof OverviewScreenBlockEntity screen) screen.nextPage(player, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OverviewScreenBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.OVERVIEW_SCREEN_ENTITY.get(), OverviewScreenBlockEntity::serverTick);
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
        builder.add(FACING);
    }
}
