package io.hookdev.setpoint.block;

import io.hookdev.setpoint.block.entity.InstrumentBlockEntity;
import io.hookdev.setpoint.item.FieldCommunicatorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared behaviour of instruments: a renamed name tag sets the tag, the field communicator
 * gets first go, and an empty hand shows the live values.
 */
public abstract class InstrumentBlock extends BaseEntityBlock {
    protected InstrumentBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (itemStack.getItem() instanceof FieldCommunicatorItem) {
            return InteractionResult.PASS; // let the communicator handle it
        }
        Component name = itemStack.getCustomName();
        if (itemStack.is(Items.NAME_TAG) && name != null && level.getBlockEntity(pos) instanceof InstrumentBlockEntity instrument) {
            if (!level.isClientSide()) {
                instrument.setTag(name.getString());
                player.sendSystemMessage(Component.literal("Tag set to " + instrument.tag()).withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InstrumentBlockEntity instrument) {
            instrument.describe(player);
        }
        return InteractionResult.SUCCESS;
    }
}
