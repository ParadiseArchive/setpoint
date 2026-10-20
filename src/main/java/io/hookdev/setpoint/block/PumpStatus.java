package io.hookdev.setpoint.block;

import net.minecraft.util.StringRepresentable;

/** What the status light on the pump shows. */
public enum PumpStatus implements StringRepresentable {
    /** Stopped: no run signal or no power. */
    OFF("off"),
    /** Running normally (cyan light). */
    RUNNING("running"),
    /** Running in trouble: cavitating, dry running or deadheaded (magenta light). */
    ALARM("alarm");

    private final String name;

    PumpStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
