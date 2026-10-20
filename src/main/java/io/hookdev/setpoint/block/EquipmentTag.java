package io.hookdev.setpoint.block;

import net.minecraft.core.BlockPos;

/** Equipment tags like on a P&amp;ID: a letter code plus a number that stays the same for a position. */
public final class EquipmentTag {
    private EquipmentTag() {}

    public static String of(String prefix, BlockPos pos) {
        int number = 100 + Math.floorMod(pos.hashCode(), 900);
        return prefix + "-" + number;
    }
}
