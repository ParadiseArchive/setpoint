package io.hookdev.setpoint.block;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;
import io.hookdev.setpoint.block.entity.TankPartBlockEntity;
import io.hookdev.setpoint.hydraulics.Vessel;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * A container you can carry: a drum or an IBC. Holds any one liquid, works with pipes, pumps, controllers and
 * buckets like a tank, and keeps what is in it when you break it. It is bigger than one block: this block is its
 * base, and {@link TankPartBlock}s fill the rest of its space and pass everything on to it.
 */
public abstract class PortableTankBlock extends BaseEntityBlock implements VesselBlock {
    private final int capacity;
    private final String prefix;

    protected PortableTankBlock(BlockBehaviour.Properties properties, int capacity, String prefix) {
        super(properties);
        this.capacity = capacity;
        this.prefix = prefix;
    }

    /** How much it holds, in mB. */
    public int capacity() { return capacity; }

    /** The letters of its tag, like D for D-417. */
    public String prefix() { return prefix; }

    /** How high the liquid can stand, in blocks: the surface is this far up when it is full. */
    public abstract double liquidHeight();

    /** The other blocks it takes up, besides this base block. */
    public abstract List<BlockPos> parts(BlockPos base, BlockState state);

    /** Which part shape a part block of this container gets. */
    protected abstract int partKind();

    /** Room for all of it? If not, it cannot be placed here. */
    protected @Nullable BlockState fitting(BlockPlaceContext context, BlockState state) {
        for (BlockPos part : parts(context.getClickedPos(), state)) {
            if (!context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide()) return;
        for (BlockPos part : parts(pos, state)) {
            level.setBlock(part, SetpointContent.TANK_PART.get().defaultBlockState().setValue(TankPartBlock.KIND, partKind()), Block.UPDATE_ALL);
            if (level.getBlockEntity(part) instanceof TankPartBlockEntity be) be.setBase(pos);
        }
    }

    /** Gone: its parts go too. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (BlockPos part : parts(pos, state)) {
            if (level.getBlockEntity(part) instanceof TankPartBlockEntity be && pos.equals(be.base())) level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortableTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.PORTABLE_TANK_ENTITY.get(), PortableTankBlockEntity::serverTick);
    }

    /** A dye paints it: the drum any colour, the IBC white or black. */
    public static InteractionResult dye(ItemStack itemStack, Level level, BlockPos base, Player player) {
        DyeColor dye = itemStack.get(DataComponents.DYE);
        if (dye == null || !(level.getBlockEntity(base) instanceof PortableTankBlockEntity tank)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!tank.dye(dye)) {
            if (tank.isIbc() && dye != DyeColor.WHITE && dye != DyeColor.BLACK && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("IBCs come in white or black.").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, base, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        itemStack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (itemStack.has(DataComponents.DYE)) return dye(itemStack, level, pos, player);
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection(), null)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** An empty hand reads what is in it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PortableTankBlockEntity tank) tank.describe(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads the level: 0 when empty, 15 when full. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof PortableTankBlockEntity tank ? Vessel.comparatorSignal(tank.fillFraction()) : 0;
    }
}
