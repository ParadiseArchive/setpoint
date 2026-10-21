package io.hookdev.setpoint.entity;

import net.minecraft.sounds.SoundEvent;

/** A vehicle with a horn that sounds for as long as its driver holds H (every player hears it, on a loop). */
public interface HornVehicle {
    boolean hornOn();

    void setHorn(boolean on);

    SoundEvent hornSound();
}
