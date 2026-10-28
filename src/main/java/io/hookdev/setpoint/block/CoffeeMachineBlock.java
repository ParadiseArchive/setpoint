package io.hookdev.setpoint.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The coffee machine of the control room. Right-click it for a mug of coffee; drinking it keeps you going, a minute of
 * speed and haste. One coffee at a time: with a mug in your hand the machine pours you no other.
 */
public class CoffeeMachineBlock extends FurnitureBlock {
    public static final int DURATION = 60 * 20;

    public CoffeeMachineBlock(BlockBehaviour.Properties properties, VoxelShape north) {
        super(properties, north);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server) {
            if (holdsCoffee(player)) {
                say(player, "You already have a coffee in your hand.", ChatFormatting.GRAY);
                return InteractionResult.SUCCESS;
            }
            ItemStack mug = new ItemStack(io.hookdev.setpoint.SetpointContent.MUG_OF_COFFEE.get());
            if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, mug);
            else if (!player.getInventory().add(mug)) Block.popResource(level, pos.above(), mug);
            server.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 1.4f);
            server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 3, 0.05, 0.05, 0.05, 0.01);
            say(player, "Fresh coffee. Right-click and hold to drink it.", ChatFormatting.GOLD);
        }
        return InteractionResult.SUCCESS;
    }

    /** A mug of coffee in either hand. */
    public static boolean holdsCoffee(Player player) {
        for (InteractionHand hand : InteractionHand.values()) if (player.getItemInHand(hand).is(io.hookdev.setpoint.SetpointContent.MUG_OF_COFFEE.get())) return true;
        return false;
    }

    private static void say(Player player, String text, ChatFormatting colour) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text).withStyle(colour), true);
    }
}
