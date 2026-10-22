package io.hookdev.setpoint.console;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.entity.ConsoleBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Finds the DCS consoles around a spot: for the alarm beacons and the overview screens of a control room. */
public final class Consoles {
    private Consoles() {}

    /** Every console within this many blocks (in every direction), in loaded chunks. */
    public static List<ConsoleBlockEntity> near(ServerLevel level, BlockPos centre, int range) {
        List<ConsoleBlockEntity> consoles = new ArrayList<>();
        for (int cx = (centre.getX() - range) >> 4; cx <= (centre.getX() + range) >> 4; cx++) {
            for (int cz = (centre.getZ() - range) >> 4; cz <= (centre.getZ() + range) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (be instanceof ConsoleBlockEntity console && Math.abs(p.getX() - centre.getX()) <= range
                            && Math.abs(p.getY() - centre.getY()) <= range && Math.abs(p.getZ() - centre.getZ()) <= range) consoles.add(console);
                }
            }
        }
        return consoles;
    }

    /** The console closest to a spot, within this many blocks, or null. */
    public static @Nullable ConsoleBlockEntity nearest(ServerLevel level, BlockPos centre, int range) {
        ConsoleBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ConsoleBlockEntity console : near(level, centre, range)) {
            double d = console.getBlockPos().distSqr(centre);
            if (d < bestDistance) { best = console; bestDistance = d; }
        }
        return best;
    }
}
