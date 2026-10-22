package io.hookdev.setpoint.control;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.hookdev.setpoint.block.entity.AmmoniaReactorBlockEntity;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.CompressorBlockEntity;
import io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.DistillationColumnBlockEntity;
import io.hookdev.setpoint.block.entity.FertilizerMixerBlockEntity;
import io.hookdev.setpoint.block.entity.FuelCellBlockEntity;
import io.hookdev.setpoint.block.entity.GasMakerBlockEntity;
import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.PumpjackBlockEntity;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Emergency shutdown. Every ESD button that is pressed in is listed here, per world; a machine asks every tick whether
 * one is pressed within {@link #RANGE} blocks, and stands still while it is. Pull the button out to let the plant start
 * again. Only the server keeps the list.
 */
public final class Esd {
    /** How far an ESD button reaches, in blocks, in every direction: the same as a console sees. */
    public static final int RANGE = 32;
    private static final Map<Level, Set<BlockPos>> PRESSED = new WeakHashMap<>();

    private Esd() {}

    /** A button at this spot is pressed in (true) or pulled out or gone (false). */
    public static void set(Level level, BlockPos button, boolean pressed) {
        if (level.isClientSide()) return;
        Set<BlockPos> buttons = PRESSED.computeIfAbsent(level, l -> new HashSet<>());
        if (pressed) buttons.add(button.immutable());
        else buttons.remove(button);
    }

    /** Whether an ESD button within range of this spot is pressed in. */
    public static boolean tripped(Level level, BlockPos pos) {
        Set<BlockPos> buttons = PRESSED.get(level);
        if (buttons == null || buttons.isEmpty()) return false;
        for (BlockPos b : buttons) {
            if (Math.abs(b.getX() - pos.getX()) <= RANGE && Math.abs(b.getY() - pos.getY()) <= RANGE && Math.abs(b.getZ() - pos.getZ()) <= RANGE) return true;
        }
        return false;
    }

    /** How many machines an ESD button at this spot stops (loaded ones). */
    public static int reach(ServerLevel level, BlockPos button) {
        int count = 0;
        for (int cx = (button.getX() - RANGE) >> 4; cx <= (button.getX() + RANGE) >> 4; cx++) {
            for (int cz = (button.getZ() - RANGE) >> 4; cz <= (button.getZ() + RANGE) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (stops(be) && Math.abs(p.getX() - button.getX()) <= RANGE && Math.abs(p.getY() - button.getY()) <= RANGE
                            && Math.abs(p.getZ() - button.getZ()) <= RANGE) count++;
                }
            }
        }
        return count;
    }

    /** The equipment an ESD stops: everything that runs, makes, burns or moves something. Tanks and instruments stay. */
    public static boolean stops(BlockEntity be) {
        return be instanceof PumpBlockEntity || be instanceof CompressorBlockEntity || be instanceof GasMakerBlockEntity
                || be instanceof PumpjackBlockEntity || be instanceof DieselGeneratorBlockEntity || be instanceof FuelCellBlockEntity
                || be instanceof AmmoniaReactorBlockEntity || be instanceof FertilizerMixerBlockEntity || be instanceof DistillationColumnBlockEntity
                || be instanceof BoilerBlockEntity || be instanceof SteamTurbineBlockEntity || be instanceof GeneratorBlockEntity;
    }
}
