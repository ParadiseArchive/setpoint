package io.hookdev.setpoint.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A safety sign on a wall or post: right-click to turn to the next sign, the way a plant marks its hazards and rules. */
public class SafetySignBlock extends FurnitureBlock {
    public static final String[] SIGNS = {"FLAMMABLE", "TOXIC", "HIGH VOLTAGE", "HARD HAT AREA", "EAR PROTECTION", "ASSEMBLY POINT"};
    public static final IntegerProperty SIGN = IntegerProperty.create("sign", 0, SIGNS.length - 1);

    public SafetySignBlock(BlockBehaviour.Properties properties) {
        super(properties, Block.box(1, 2, 15, 15, 14, 16));
        this.registerDefaultState(this.defaultBlockState().setValue(SIGN, 0));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            int next = (state.getValue(SIGN) + 1) % SIGNS.length;
            level.setBlock(pos, state.setValue(SIGN, next), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal("Sign: " + SIGNS[next]).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SIGN);
    }
}
