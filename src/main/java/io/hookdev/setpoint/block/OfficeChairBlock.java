package io.hookdev.setpoint.block;

import io.hookdev.setpoint.entity.SeatEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A 24-hour control-room chair: high back with a headrest, armrests, five castors. Right-click to sit down, facing the
 * way the chair faces (its back away from the desk); sneak to get up.
 */
public class OfficeChairBlock extends FurnitureBlock {
    /** Where the cushion is, in blocks above the floor. */
    public static final double SEAT_HEIGHT = 0.7;

    public OfficeChairBlock(BlockBehaviour.Properties properties, VoxelShape north) {
        super(properties, north);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isPassenger() || player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            // the chair's front faces you when you put it down; sitting, you look the other way: at the desk
            float yaw = state.getValue(FACING).getOpposite().toYRot();
            if (!SeatEntity.sit(level, pos, player, yaw) && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Someone is sitting there").withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
