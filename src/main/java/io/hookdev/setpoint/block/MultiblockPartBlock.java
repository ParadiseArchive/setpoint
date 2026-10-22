package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The rest of something bigger than one block. It draws nothing itself (the base block's model reaches over it),
 * holds you up, and passes clicks, comparators and breaking on to its base: to a player it is the same thing.
 */
public abstract class MultiblockPartBlock extends BaseEntityBlock {
    protected MultiblockPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** It draws nothing, so it casts no shade on the blocks around it (a full collision box would darken them in square patches)... */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    /** ...and lets daylight through, so whatever its base draws over it is lit like the open air. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    protected static @Nullable BlockPos base(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) || part.base() == null) return null;
        return level.getBlockState(part.base()).getBlock() instanceof MultiblockBase ? part.base() : null;
    }

    /** The same click, on the base block. */
    private static BlockHitResult atBase(BlockHitResult hit, BlockPos base) {
        return new BlockHitResult(hit.getLocation(), hit.getDirection(), base, hit.isInside());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        BlockPos base = base(level, pos);
        return base == null ? InteractionResult.PASS : level.getBlockState(base).useItemOn(itemStack, level, player, hand, atBase(hitResult, base));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos base = base(level, pos);
        return base == null ? InteractionResult.PASS : level.getBlockState(base).useWithoutItem(level, player, atBase(hitResult, base));
    }

    /** Middle-click (and Jade) see the thing it belongs to, not this part. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        BlockPos base = base(level, pos);
        return base == null ? ItemStack.EMPTY : level.getBlockState(base).getCloneItemStack(level, base, includeData);
    }

    /** Broken in creative: the whole thing goes, without a drop. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos base = base(level, pos);
        if (!level.isClientSide() && player.isCreative() && base != null) level.removeBlock(base, false);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Any part gone (broken, blown up): the whole thing breaks, and drops with all it holds. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos base = MultiblockPartBlockEntity.lastBase(pos);
        if (base != null && level.getBlockState(base).getBlock() instanceof MultiblockBase) level.destroyBlock(base, true);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockPos base = base(level, pos);
        if (base == null) return 0;
        BlockState baseState = level.getBlockState(base);
        return baseState.hasAnalogOutputSignal() ? baseState.getAnalogOutputSignal(level, base, direction) : 0;
    }
}
