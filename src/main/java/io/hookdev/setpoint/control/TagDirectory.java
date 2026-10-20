package io.hookdev.setpoint.control;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.entity.InstrumentBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Finds instruments by tag, like a DCS finds field devices by their tag name.
 * Server side only; instruments register themselves while their chunk is loaded.
 */
public final class TagDirectory {
    private static final Map<Level, Map<String, BlockPos>> TAGS = new WeakHashMap<>();

    private TagDirectory() {}

    public static synchronized void put(Level level, String tag, BlockPos pos) {
        TAGS.computeIfAbsent(level, l -> new HashMap<>()).put(tag, pos.immutable());
    }

    public static synchronized void remove(Level level, String tag, BlockPos pos) {
        Map<String, BlockPos> tags = TAGS.get(level);
        if (tags != null && pos.equals(tags.get(tag))) tags.remove(tag);
    }

    /** The closest loaded instrument of this type within {@code radius} blocks, or null. */
    public static synchronized <T extends InstrumentBlockEntity> @Nullable T nearest(Level level, BlockPos from, int radius, Class<T> type) {
        Map<String, BlockPos> tags = TAGS.get(level);
        if (tags == null) return null;
        T best = null;
        double bestDistance = (double) radius * radius;
        for (BlockPos pos : tags.values()) {
            double distance = pos.distSqr(from);
            if (distance > bestDistance || !level.isLoaded(pos)) continue;
            BlockEntity entity = level.getBlockEntity(pos);
            if (type.isInstance(entity)) {
                best = type.cast(entity);
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The loaded instrument with this tag and type, or null if there is none. */
    public static synchronized <T extends InstrumentBlockEntity> @Nullable T find(Level level, String tag, Class<T> type) {
        if (tag.isEmpty()) return null;
        Map<String, BlockPos> tags = TAGS.get(level);
        BlockPos pos = tags == null ? null : tags.get(tag);
        if (pos == null || !level.isLoaded(pos)) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (type.isInstance(entity) && type.cast(entity).tag().equals(tag)) return type.cast(entity);
        return null;
    }
}
