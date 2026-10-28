package io.hookdev.setpoint.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;

/** A vehicle that other players see glide from one position to the next, like a boat, instead of jumping. */
public abstract class InterpolatedVehicle extends VehicleEntity {
    protected InterpolatedVehicle(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }
}
