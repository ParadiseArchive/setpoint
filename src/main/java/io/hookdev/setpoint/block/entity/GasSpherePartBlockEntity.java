package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.hydraulics.PressureVessel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A part of a gas sphere stands in for it: a pressure transmitter or safety valve against it sees the whole sphere. */
public class GasSpherePartBlockEntity extends MultiblockPartBlockEntity implements PressureVessel {
    public GasSpherePartBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.GAS_SPHERE_PART_ENTITY.get(), pos, state);
    }

    public @Nullable GasSphereBlockEntity sphere() {
        return level != null && base() != null && level.getBlockEntity(base()) instanceof GasSphereBlockEntity sphere ? sphere : null;
    }

    @Override public double pressure() { GasSphereBlockEntity s = sphere(); return s == null ? 0 : s.pressure(); }
    @Override public double designPressure() { return GasSphereBlockEntity.MAX_PRESSURE; }
    @Override public int vent(int amount) { GasSphereBlockEntity s = sphere(); return s == null ? 0 : s.vent(amount); }
    @Override public String tag() { GasSphereBlockEntity s = sphere(); return s == null ? "" : s.tag(); }
}
