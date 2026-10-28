package io.hookdev.setpoint.block;

import java.util.Map;

import io.hookdev.setpoint.block.entity.EsdButtonBlockEntity;
import io.hookdev.setpoint.control.Esd;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Emergency stop: a red mushroom button in a yellow box, for a wall or a desk. Push it in and every pump, compressor,
 * generator, boiler and machine within 32 blocks stops at once and stays stopped; pull it out (right-click again) to
 * let the plant start. While it is pressed in it gives a full redstone signal.
 */
public class EsdButtonBlock extends SetpointEntityBlock {
    /** The way the button points: out of the wall (or up from the desk) it sits on. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Shapes.or(Block.box(3, 3, 11, 13, 13, 16), Block.box(5, 5, 7, 11, 11, 11)));

    public EsdButtonBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PRESSED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server) {
            boolean pressed = !state.getValue(PRESSED);
            server.setBlock(pos, state.setValue(PRESSED, pressed), Block.UPDATE_ALL);
            Esd.set(server, pos, pressed);
            server.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
            if (pressed) {
                server.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.5f);
                server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.8f, 0.6f);
            } else {
                server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.8f, 0.8f);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(pressed
                        ? Component.literal("ESD TRIPPED: " + Esd.reach(server, pos) + " machines within " + Esd.RANGE + " blocks stopped").withStyle(ChatFormatting.RED)
                        : Component.literal("ESD reset: the plant may start again").withStyle(ChatFormatting.GREEN), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(PRESSED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(PRESSED) && direction == state.getValue(FACING) ? 15 : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EsdButtonBlockEntity(pos, state);
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
        builder.add(FACING, PRESSED);
    }
}
