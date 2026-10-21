package io.hookdev.setpoint.hydraulics;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.FluidPipeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Another mod's tank (or anything with fluid storage, even a cauldron) seen as a vessel, so pumps can draw
 * from it and fill it, and controllers can measure it.
 */
public record ForeignVessel(BlockPos pos, ResourceHandler<FluidResource> handler) implements Vessel {
    /** A Setpoint vessel at {@code pos}, or another mod's fluid storage reached from {@code side}; null if neither. */
    public static @Nullable Vessel at(Level level, BlockPos pos, @Nullable Direction side) {
        if (level.getBlockEntity(pos) instanceof Vessel vessel) return vessel;
        if (isPiping(level, pos)) return null;
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        return handler == null ? null : new ForeignVessel(pos, handler);
    }

    /** Whether there is another mod's fluid storage here that a pipe can connect to. */
    public static boolean isStorage(Level level, BlockPos pos, @Nullable Direction side) {
        return !isPiping(level, pos) && !(level.getBlockEntity(pos) instanceof Vessel)
                && level.getCapability(Capabilities.Fluid.BLOCK, pos, side) != null;
    }

    private static boolean isPiping(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FluidPipeBlock;
    }

    @Override
    public ResourceHandler<FluidResource> fluid() {
        return handler;
    }

    @Override
    public FluidResource resource() {
        for (int i = 0; i < handler.size(); i++) {
            if (!handler.getResource(i).isEmpty() && handler.getAmountAsLong(i) > 0) return handler.getResource(i);
        }
        return FluidResource.EMPTY;
    }

    @Override
    public int amount() {
        long total = 0;
        for (int i = 0; i < handler.size(); i++) total += handler.getAmountAsLong(i);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    @Override
    public double fillFraction() {
        long capacity = 0;
        for (int i = 0; i < handler.size(); i++) {
            FluidResource resource = handler.getResource(i);
            capacity += handler.getCapacityAsLong(i, resource.isEmpty() ? FluidResource.of(Fluids.WATER) : resource);
        }
        return capacity <= 0 ? 0 : Math.min(1, amount() / (double) capacity);
    }

    @Override
    public boolean canAccept(FluidResource incoming) {
        try (Transaction probe = Transaction.openRoot()) {
            // never committed: only asking. Some storages only take whole buckets.
            return handler.insert(incoming, 1, probe) > 0 || handler.insert(incoming, 1_000, probe) > 0;
        }
    }

    @Override
    public BlockPos getBlockPos() {
        return pos;
    }
}
