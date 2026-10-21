package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * A container you can carry: a drum or an IBC. Holds any one liquid, works with pipes, pumps, controllers and
 * buckets like a tank, and keeps what is in it when you break it.
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortableTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.PORTABLE_TANK_ENTITY.get(), PortableTankBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
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
        return level.getBlockEntity(pos) instanceof PortableTankBlockEntity tank ? io.hookdev.setpoint.hydraulics.Vessel.comparatorSignal(tank.fillFraction()) : 0;
    }
}
