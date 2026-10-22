package io.hookdev.setpoint.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

/**
 * The colour of a liquid in a sight glass or on a gauge: water blue, lava orange, and for other mods' fluids the map
 * colour of their fluid block, so every fluid gets its own colour without a list. A gas has the colour of its cylinder.
 */
public final class FluidColours {
    public static final int WATER = 0xFF3A6FD8, LAVA = 0xFFE0661F, UNKNOWN = 0xFF8D95A5;

    private FluidColours() {}

    public static int of(Fluid fluid) {
        if (fluid.isSame(Fluids.EMPTY)) return UNKNOWN;
        if (fluid instanceof io.hookdev.setpoint.gas.GasFluid) return io.hookdev.setpoint.gas.Gases.colour(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid));
        var liquid = io.hookdev.setpoint.gas.Liquids.of(fluid);
        if (liquid != null) return liquid.colour();
        if (fluid.isSame(Fluids.WATER)) return WATER;
        if (fluid.isSame(Fluids.LAVA)) return LAVA;
        MapColor map = fluid.defaultFluidState().createLegacyBlock().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return map == MapColor.NONE || map.col == 0 ? UNKNOWN : 0xFF000000 | map.col;
    }
}
