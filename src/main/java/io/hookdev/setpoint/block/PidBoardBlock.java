package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.entity.PidBoardBlockEntity;
import io.hookdev.setpoint.pid.BoardLayout;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * P&ID board: a thin panel for the wall of a control room. Boards side by side and below each other make one big
 * panel; right-click any of them with a P&ID and the drawing fills the whole panel, live, like a mimic board.
 * Right-click a symbol to work it: a controller opens its faceplate, a pump starts or stops, anything else reports.
 * Sneak-right-click with an empty hand to take the P&ID back. A comparator counts the alarms on the drawing.
 */
public class PidBoardBlock extends BaseEntityBlock {
    /** The way the drawing faces: away from the wall. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The largest panel one drawing covers, in blocks. */
    public static final int MAX_WIDTH = 8, MAX_HEIGHT = 6;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(0, 0, 15, 16, 16, 16), Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 1),
            Direction.EAST, Block.box(0, 0, 0, 1, 16, 16), Direction.WEST, Block.box(15, 0, 0, 16, 16, 16));

    public PidBoardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return this.defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    /** To the right of a board, as you stand in front of it. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static boolean isBoard(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PidBoardBlock && state.getValue(FACING) == facing;
    }

    /** The top-left board of the panel this board belongs to. */
    public static BlockPos topLeft(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos at = pos;
        for (int i = 0; i < MAX_HEIGHT && isBoard(level, at.above(), facing); i++) at = at.above();
        Direction left = right(facing).getOpposite();
        for (int i = 0; i < MAX_WIDTH && isBoard(level, at.relative(left), facing); i++) at = at.relative(left);
        return at;
    }

    /** Width and height of the panel that starts at this top-left board, in blocks. */
    public static int[] size(BlockGetter level, BlockPos topLeft, Direction facing) {
        Direction right = right(facing);
        int width = 1;
        while (width < MAX_WIDTH && isBoard(level, topLeft.relative(right, width), facing)) width++;
        int height = 1;
        rows:
        while (height < MAX_HEIGHT) {
            for (int x = 0; x < width; x++) if (!isBoard(level, topLeft.below(height).relative(right, x), facing)) break rows;
            height++;
        }
        return new int[]{width, height};
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PidBoardBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.PID_BOARD_ENTITY.get(), PidBoardBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.has(SetpointContent.PID_DRAWING.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        BlockPos origin = topLeft(level, pos, state.getValue(FACING));
        if (!level.isClientSide() && level.getBlockEntity(origin) instanceof PidBoardBlockEntity board) {
            ItemStack previous = board.insertPid(stack);
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false, Prediction.SERVER_ONLY);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6f, 1.0f);
            if (player instanceof ServerPlayer serverPlayer) {
                int[] size = size(level, origin, state.getValue(FACING));
                serverPlayer.sendSystemMessage(Component.literal("P&ID on the board: " + size[0] + " x " + size[1] + " blocks, live."), true);
                SetpointContent.PLANT_EVENT.get().trigger(serverPlayer, PlantEventTrigger.PID_ON_CONSOLE);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Right-click a symbol to work it; sneak-right-click with an empty hand to take the P&ID back. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos origin = topLeft(level, pos, state.getValue(FACING));
        if (!player.isSecondaryUseActive()) {
            if (!(level.getBlockEntity(origin) instanceof PidBoardBlockEntity board) || board.drawing() == null) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                int[] size = size(level, origin, state.getValue(FACING));
                double[] uv = BoardLayout.panelPoint(origin, state.getValue(FACING), size[0], size[1], pos, hitResult.getLocation());
                PidDrawing.Cell cell = BoardLayout.of(board.drawing(), size[0], size[1]).cellAt(board.drawing(), uv[0], uv[1]);
                if (cell != null) board.operate(cell, player, pos);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(origin) instanceof PidBoardBlockEntity board && !board.pid().isEmpty()) {
            ItemStack pid = board.ejectPid();
            if (!player.getInventory().add(pid)) player.drop(pid, false, Prediction.SERVER_ONLY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator next to any board of the panel gives the number of alarms on the drawing (at most 15). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockPos origin = topLeft(level, pos, state.getValue(FACING));
        return level.getBlockEntity(origin) instanceof PidBoardBlockEntity board ? Math.min(15, board.alarms()) : 0;
    }

    /** Every board of the panel, for the comparators next to them. */
    public static List<BlockPos> panel(BlockGetter level, BlockPos topLeft, Direction facing) {
        int[] size = size(level, topLeft, facing);
        List<BlockPos> boards = new ArrayList<>();
        for (int x = 0; x < size[0]; x++) for (int y = 0; y < size[1]; y++) boards.add(topLeft.relative(right(facing), x).below(y));
        return boards;
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
