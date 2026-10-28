package io.hookdev.setpoint.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A local control station: the start/stop push-button box in the field next to a machine. Press the green button (the
 * top half) to start, the red one (the bottom half) to stop. Stopped, it gives a redstone signal, which stops a pump
 * or any machine that stops on redstone; started, it gives none. A lamp on top shows which.
 */
public class LocalControlStationBlock extends FurnitureBlock {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public LocalControlStationBlock(BlockBehaviour.Properties properties) {
        super(properties, Block.box(4, 2, 12, 12, 14, 16));
        this.registerDefaultState(this.defaultBlockState().setValue(RUNNING, true));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            boolean start = hitResult.getLocation().y - pos.getY() >= 0.5;
            if (state.getValue(RUNNING) != start) {
                level.setBlock(pos, state.setValue(RUNNING, start), Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, this);
            }
            level.playSound(null, pos, start ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.6f, start ? 1.1f : 0.8f);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal(start ? "START" : "STOP").withStyle(start ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Stopped: a signal on every side, so the machine beside it stops. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(RUNNING) ? 0 : 15;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING);
    }
}
