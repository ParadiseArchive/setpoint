package io.hookdev.setpoint.item;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.client.ClientHooks;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A drawn P&ID. Right-click to open it: give it a name and read the drawing. Its name shows as the item name. */
public class PidItem extends Item {
    public PidItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PidDrawing drawing = stack.get(SetpointContent.PID_DRAWING.get());
        if (drawing == null) return InteractionResult.PASS;
        if (level.isClientSide()) ClientHooks.openPid(drawing, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        PidDrawing drawing = stack.get(SetpointContent.PID_DRAWING.get());
        return drawing == null || drawing.name().equals(PidDrawing.UNTITLED) ? super.getName(stack) : Component.literal(drawing.name());
    }
}
