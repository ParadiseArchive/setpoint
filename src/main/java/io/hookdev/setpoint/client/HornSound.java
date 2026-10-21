package io.hookdev.setpoint.client;

import io.hookdev.setpoint.entity.HornVehicle;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** A vehicle's horn: one steady note on a loop, going along with the vehicle, until the driver lets go of H. */
public class HornSound extends AbstractTickableSoundInstance {
    private final Entity vehicle;

    public <T extends Entity & HornVehicle> HornSound(T vehicle) {
        super(vehicle.hornSound(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        follow();
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved() || !((HornVehicle) vehicle).hornOn()) {
            stop();
            return;
        }
        follow();
    }

    private void follow() {
        x = vehicle.getX();
        y = vehicle.getY() + 1;
        z = vehicle.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
