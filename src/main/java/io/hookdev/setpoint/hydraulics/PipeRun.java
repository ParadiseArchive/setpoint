package io.hookdev.setpoint.hydraulics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.TankBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Traces a pipe run from one side of a pump to the nearest tank. */
public final class PipeRun {
    /** Longer runs than this are not followed (keeps big networks cheap). */
    public static final int MAX_PIPES = 512;

    /**
     * @param tankPos where the run ends
     * @param length  number of pipe blocks between the pump and the tank (1 block = 1 m)
     */
    public record End(BlockPos tankPos, int length) {}

    private PipeRun() {}

    /** Nearest tank reached from {@code pumpPos} going out through {@code side}, or null if there is none. */
    public static @Nullable End trace(Level level, BlockPos pumpPos, Direction side) {
        BlockPos first = pumpPos.relative(side);
        if (!level.isLoaded(first)) return null;
        BlockState firstState = level.getBlockState(first);
        if (firstState.getBlock() instanceof TankBlock) return new End(first, 0);
        if (!(firstState.getBlock() instanceof FluidPipeBlock)) return null;

        // Breadth first, so the first tank found is the closest one.
        Set<BlockPos> seen = new HashSet<>();
        seen.add(pumpPos);
        seen.add(first);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ArrayDeque<Integer> lengths = new ArrayDeque<>();
        queue.add(first);
        lengths.add(1);
        while (!queue.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos pipe = queue.poll();
            int length = lengths.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pipe.relative(direction);
                if (!seen.add(next) || !level.isLoaded(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof TankBlock) return new End(next, length);
                if (state.getBlock() instanceof FluidPipeBlock) {
                    queue.add(next);
                    lengths.add(length + 1);
                }
            }
        }
        return null;
    }
}
