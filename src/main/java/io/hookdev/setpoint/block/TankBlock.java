package io.hookdev.setpoint.block;

import io.hookdev.setpoint.block.entity.TankBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/** Atmospheric storage tank. Fill and empty it with buckets, or let a pump do the work. */
public class TankBlock extends BaseEntityBlock {
    /** Sight glass level in eighths. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 8);

    public TankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 0));
    }

    public static int levelFor(int amount, int capacity) {
        if (amount <= 0) return 0;
        return Math.max(1, (int) Math.round(8.0 * amount / capacity));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection(), null)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
            int percent = (int) Math.round(tank.fillFraction() * 100);
            String contents = tank.amount() == 0 ? "empty" : tank.resource().getFluidType().getDescription().getString();
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal(EquipmentTag.of("T", pos) + "  ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("LEVEL " + percent + "%  ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format("%.1f / %.0f B %s", tank.amount() / 1000.0, TankBlockEntity.CAPACITY / 1000.0, contents))
                            .withStyle(ChatFormatting.GRAY)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }
}
