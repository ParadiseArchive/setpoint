package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.advancement.PlantEventTrigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hand-operated gate valve to isolate a line: right-click to open or close it. A redstone signal closes it
 * no matter how the handwheel stands. The rising stem shows the position: sticking out means open.
 */
public class HandValveBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** The handwheel: open or shut by hand. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(5, 5, 0, 11, 11, 16), Block.box(4, 4, 5, 12, 11, 11), Block.box(3, 13, 3, 13, 15, 13));
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(0, 5, 5, 16, 11, 11), Block.box(5, 4, 4, 11, 11, 12), Block.box(3, 13, 3, 13, 15, 13));
    private static final VoxelShape SHAPE_Y = Shapes.or(Block.box(5, 0, 5, 11, 16, 11), Block.box(4, 5, 4, 12, 11, 11), Block.box(3, 3, 13, 13, 13, 15));

    public HandValveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(OPEN, true).setValue(POWERED, false));
    }

    /** Liquid passes only when the wheel is open and no redstone signal holds it shut. */
    public static boolean isOpen(BlockState state) {
        return state.getValue(OPEN) && !state.getValue(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Line up with the pipe you click on, otherwise with the way you look.
        Direction.Axis axis = context.getClickedFace().getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection().getAxis()
                : context.getClickedFace().getAxis();
        return this.defaultBlockState().setValue(AXIS, axis).setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockState next = state.cycle(OPEN);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, next.getValue(OPEN) ? 0.8f : 0.6f);
            String text = next.getValue(POWERED) ? "held SHUT by redstone" : next.getValue(OPEN) ? "OPEN" : "SHUT";
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Hand valve " + text)
                        .withStyle(isOpen(next) ? ChatFormatting.AQUA : ChatFormatting.GOLD), true);
                if (!next.getValue(OPEN)) io.hookdev.setpoint.SetpointContent.PLANT_EVENT.get().trigger(serverPlayer, PlantEventTrigger.VALVE_SHUT);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180) return state;
        return switch (state.getValue(AXIS)) {
            case X -> state.setValue(AXIS, Direction.Axis.Z);
            case Z -> state.setValue(AXIS, Direction.Axis.X);
            case Y -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN, POWERED);
    }
}
