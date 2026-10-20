package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Control valve in a pipe run. Flow passes along its AXIS. Sneak-click with an empty hand
 * to switch between fail-close (FC) and fail-open (FO).
 */
public class ControlValveBlock extends InstrumentBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** Valve position shown on the actuator, in quarters. */
    public static final IntegerProperty OPEN = IntegerProperty.create("open", 0, 4);

    private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(5, 5, 0, 11, 11, 16), Block.box(4, 4, 4, 12, 12, 12), Block.box(3, 13, 3, 13, 16, 13));
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(0, 5, 5, 16, 11, 11), Block.box(4, 4, 4, 12, 12, 12), Block.box(3, 13, 3, 13, 16, 13));
    private static final VoxelShape SHAPE_Y = Shapes.or(Block.box(5, 0, 5, 11, 16, 11), Block.box(4, 4, 4, 12, 12, 12), Block.box(3, 3, 0, 13, 13, 3)); // actuator turned to the north side

    public ControlValveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(OPEN, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Line up with the pipe you click on, otherwise with the way you look.
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection().getAxis()
                : context.getClickedFace().getAxis());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControlValveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.CONTROL_VALVE_ENTITY.get(), ControlValveBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof ControlValveBlockEntity valve) {
            if (!level.isClientSide()) {
                valve.setFailOpen(!valve.failOpen());
                player.sendSystemMessage(Component.literal(valve.tag() + "  fail action set to " + (valve.failOpen() ? "FAIL OPEN (FO)" : "FAIL CLOSE (FC)"))
                        .withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
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
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            Direction.Axis axis = state.getValue(AXIS);
            if (axis == Direction.Axis.X) return state.setValue(AXIS, Direction.Axis.Z);
            if (axis == Direction.Axis.Z) return state.setValue(AXIS, Direction.Axis.X);
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN);
    }
}
