package io.hookdev.setpoint.physics;

/**
 * A small coal-fired boiler: a firebox under a water drum. The water heats up to boiling, then every
 * tick of firing turns water into steam. Without water the drum itself heats up. A low-low level
 * trip (on by default) shuts the burner off before that can happen, like the protection on a real boiler.
 */
public final class BoilerModel {
    /** Cold water and cold steel, in °C. */
    public static final double AMBIENT = 20.0;
    public static final double BOILING = 100.0;
    /** Heating the water from cold while firing, °C per tick (cold to boiling in 10 s). */
    public static final double HEAT_UP = 0.4;
    /** Cooling when the fire is out, °C per tick. */
    public static final double COOL_DOWN = 0.05;
    /** A dry drum heats much faster than one full of water, °C per tick. */
    public static final double DRY_HEAT = 1.0;
    /** Steam made per tick of firing at boiling point, in mB (and the same amount of water used). */
    public static final int STEAM_RATE = 20;

    /** The burner trips below this drum level and starts again above the reset level (fractions). */
    public static final double TRIP_LEVEL = 0.10;
    public static final double RESET_LEVEL = 0.25;
    /** A dry drum above this temperature is in danger: feeding water now flashes it to steam. */
    public static final double OVERHEAT = 250.0;
    /** The drum gives way at this temperature. */
    public static final double BURST = 450.0;

    private BoilerModel() {}

    /** Low-low level trip with a reset band, so the burner does not flicker around the trip point. */
    public static boolean tripped(boolean wasTripped, double levelFraction) {
        return wasTripped ? levelFraction < RESET_LEVEL : levelFraction < TRIP_LEVEL;
    }

    /** Drum temperature after one tick. */
    public static double nextTemperature(double temperature, boolean firing, int water) {
        if (firing) {
            if (water <= 0) return temperature + DRY_HEAT;
            if (temperature < BOILING) return Math.min(BOILING, temperature + HEAT_UP);
            return BOILING; // boiling water holds the drum at 100 °C
        }
        return Math.max(AMBIENT, temperature - COOL_DOWN);
    }

    /** Water boiled off into steam this tick, in mB. */
    public static int boil(double temperature, boolean firing, int water, int steamRoom) {
        if (!firing || temperature < BOILING) return 0;
        return Math.max(0, Math.min(STEAM_RATE, Math.min(water, steamRoom)));
    }

    /** Cold feed water hitting a dry, red-hot drum turns to steam all at once. */
    public static boolean flashes(double temperature, int water) {
        return temperature > OVERHEAT && water > 0;
    }

    public static boolean bursts(double temperature) {
        return temperature >= BURST;
    }
}
