package io.hookdev.setpoint.hydraulics;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.PumpBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the control valve that manipulates a tank's level, so a controller can link itself.
 * Follows each pipe run from the tank to the pump at the other end: if that pump pushes towards
 * the tank, the valve sits in the feed (level control must be reverse acting); if the pump sucks
 * from the tank, the valve sits in the draw-off (direct acting).
 */
public final class LoopFinder {
    public static final int MAX_BLOCKS = 256;

    /**
     * @param valve     the control valve on the run
     * @param feedsTank true when the run fills the tank, false when it empties it
     */
    public record ValveLink(BlockPos valve, boolean feedsTank) {}

    private LoopFinder() {}

    public static @Nullable ValveLink findValve(Level level, BlockPos tankPos) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        parent.put(tankPos, tankPos);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(tankPos);
        while (!queue.isEmpty() && parent.size() <= MAX_BLOCKS) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            boolean atTank = current.equals(tankPos);
            for (Direction direction : Direction.values()) {
                if (!atTank && !Piping.leaves(currentState, direction)) continue;
                BlockPos next = current.relative(direction);
                if (parent.containsKey(next) || !level.isLoaded(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof PumpBlock && state.getValue(PumpBlock.FACING).getAxis() == direction.getAxis()) {
                    // The pump's discharge faces us: it pushes this run towards the tank.
                    boolean feeds = state.getValue(PumpBlock.FACING) == direction.getOpposite();
                    BlockPos valve = valveOnPath(level, current, parent);
                    if (valve != null) return new ValveLink(valve, feeds);
                    continue;
                }
                if (Piping.enters(state, direction)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static @Nullable BlockPos valveOnPath(Level level, BlockPos from, Map<BlockPos, BlockPos> parent) {
        for (BlockPos pos = from; !parent.get(pos).equals(pos); pos = parent.get(pos)) {
            if (level.getBlockState(pos).getBlock() instanceof ControlValveBlock) return pos;
        }
        return null;
    }
}
