package io.hookdev.setpoint.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The coffee machine of the control room. A cup keeps you going: a minute of speed and haste. */
public class CoffeeMachineBlock extends FurnitureBlock {
    public static final int DURATION = 60 * 20;

    public CoffeeMachineBlock(BlockBehaviour.Properties properties, VoxelShape north) {
        super(properties, north);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, DURATION, 0));
            server.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 1.4f);
            server.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.6f, 1.0f);
            server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 3, 0.05, 0.05, 0.05, 0.01);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Fresh coffee. The shift goes on.").withStyle(ChatFormatting.GOLD), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
