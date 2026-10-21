package io.hookdev.setpoint.item;

import io.hookdev.setpoint.client.ClientHooks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The Setpoint Handbook. Right-click to read it; new players get one when they first join. */
public class HandbookItem extends Item {
    public HandbookItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) ClientHooks.openHandbook();
        return InteractionResult.SUCCESS;
    }
}
