package io.hookdev.setpoint.item;

import io.hookdev.setpoint.McVersion;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.pid.PidCapture;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A blank P&ID sheet. Right-click a block for the first corner, then the opposite corner: the plant in between,
 * from the lowest corner up {@link PidCapture#HEIGHT} blocks, is drawn onto the sheet. One sheet is used up per drawing
 * (not in creative). While the first corner is set, the selection shows in the world. Sneak-right-click
 * the air to start over.
 */
public class BlankPidItem extends Item {
    public BlankPidItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack sheet = context.getItemInHand();
        BlockPos clicked = context.getClickedPos().immutable();
        BlockPos corner = sheet.get(SetpointContent.PID_CORNER.get());

        if (corner == null) {
            sheet.set(SetpointContent.PID_CORNER.get(), clicked);
            say(player, "First corner set. Now right-click the opposite corner (the drawing goes " + PidCapture.HEIGHT + " blocks up).", ChatFormatting.AQUA);
            level.playSound(null, clicked, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6f, 1.3f);
            return InteractionResult.SUCCESS;
        }

        PidCapture.Result result = PidCapture.capture(level, corner, clicked);
        if (result.drawing() == null) {
            say(player, result.error(), ChatFormatting.GOLD);
            return InteractionResult.SUCCESS;
        }
        if (result.drawing().cells().isEmpty()) {
            sheet.remove(SetpointContent.PID_CORNER.get());
            say(player, "Nothing to draw there: no Setpoint equipment in that box. Start again.", ChatFormatting.GOLD);
            return InteractionResult.SUCCESS;
        }
        sheet.remove(SetpointContent.PID_CORNER.get());
        ItemStack drawing = new ItemStack(SetpointContent.PID.get());
        drawing.set(SetpointContent.PID_DRAWING.get(), result.drawing());
        sheet.consume(1, player);
        if (!player.getInventory().add(drawing)) McVersion.drop(player, drawing);
        level.playSound(null, clicked, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8f, 1.0f);
        say(player, "P&ID drawn: " + result.drawing().cells().size() + " symbols. Right-click it to name and read it.", ChatFormatting.AQUA);
        if (player instanceof ServerPlayer serverPlayer) SetpointContent.PLANT_EVENT.get().trigger(serverPlayer, PlantEventTrigger.PID_DRAWN);
        return InteractionResult.SUCCESS;
    }

    /** Sneak-right-click the air to forget the first corner. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack sheet = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && sheet.has(SetpointContent.PID_CORNER.get())) {
            if (!level.isClientSide()) {
                sheet.remove(SetpointContent.PID_CORNER.get());
                say(player, "Selection cleared.", ChatFormatting.GRAY);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static void say(Player player, String text, ChatFormatting colour) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text).withStyle(colour), true);
    }
}
