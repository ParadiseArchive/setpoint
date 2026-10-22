package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.FertilizerMixerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fertilizer mixer: ammonia in on your left. Right-click it with bone meal to fill it; right-click with an empty hand to
 * take the fertilizer out (or read it while there is none). Hoppers work too.
 */
public class FertilizerMixerBlock extends GasMakerBlock {
    public FertilizerMixerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean gasPort(BlockState state, Direction side) {
        return side == left(state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!itemStack.is(Items.BONE_MEAL)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FertilizerMixerBlockEntity mixer) itemStack.consume(mixer.insert(itemStack), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FertilizerMixerBlockEntity mixer) {
            ItemStack made = mixer.takeOutput();
            if (made.isEmpty()) mixer.describe(player);
            else if (!player.getInventory().add(made)) player.drop(made, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FertilizerMixerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.FERTILIZER_MIXER_ENTITY.get(), FertilizerMixerBlockEntity::serverTick);
    }
}
