package io.hookdev.setpoint.block.entity;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A part of something bigger than one block remembers its base block. Players get it too, so their game (and Jade)
 * knows what the part belongs to.
 */
public class MultiblockPartBlockEntity extends BlockEntity {
    /** Parts that were just removed, and their base: the block finishes the job after the entity is gone. */
    private static final Map<BlockPos, BlockPos> REMOVED = new HashMap<>();

    private @Nullable BlockPos base;

    public MultiblockPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public @Nullable BlockPos base() { return base; }

    public void setBase(BlockPos base) {
        this.base = base.immutable();
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Let go of the base before the part is taken away on purpose, so taking it away does not break the base. */
    public void release() {
        base = null;
    }

    /** The base of a part that was just removed, once. */
    public static @Nullable BlockPos lastBase(BlockPos pos) {
        return REMOVED.remove(pos);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (base != null && level != null && !level.isClientSide()) REMOVED.put(pos.immutable(), base);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (base != null) output.putLong("base", base.asLong());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long at = input.getLongOr("base", Long.MIN_VALUE);
        base = at == Long.MIN_VALUE ? null : BlockPos.of(at);
    }

    // players get the base too
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
