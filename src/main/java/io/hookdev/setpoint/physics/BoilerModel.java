package io.hookdev.setpoint.physics;

/**
 * A small coal-fired boiler: a firebox under a water drum. The water heats up to boiling, then every
 * tick of firing turns water into steam. Without water the drum itself heats up. A low-low level
 * trip (on by default) shuts the burner off before that can happen, like the protection on a real boiler.
 * <p>
 * Steam in the closed drum is pressure: every {@link #STEAM_PER_BAR} mB is one bar. The drum is built for
 * {@link #DESIGN_PRESSURE}; a high-pressure trip (on by default) stops the burner below that, a safety valve
 * on the drum lifts at it, and with neither the drum bursts at {@link #BURST_PRESSURE}.
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

    /** Steam in the drum per bar of pressure, in mB. */
    public static final int STEAM_PER_BAR = 400;
    /** What the drum is built for, and where a safety valve is normally set, in bar. */
    public static final double DESIGN_PRESSURE = 10.0;
    /** The high-pressure trip stops the burner here and lets it start again below the reset pressure. */
    public static final double HP_TRIP = 9.0;
    public static final double HP_RESET = 7.0;
    /** The drum gives way at this pressure. */
    public static final double BURST_PRESSURE = 13.0;
    /** The most pressure a pressure transmitter reads (its range), in bar. */
    public static final double PRESSURE_RANGE = 15.0;

    private BoilerModel() {}

    /** Drum pressure from the steam in it, in bar. */
    public static double pressure(int steam) {
        return steam / (double) STEAM_PER_BAR;
    }

    /** High-pressure trip with a reset band. */
    public static boolean pressureTripped(boolean wasTripped, double pressure) {
        return wasTripped ? pressure > HP_RESET : pressure >= HP_TRIP;
    }

    public static boolean burstsFromPressure(double pressure) {
        return pressure >= BURST_PRESSURE;
    }

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

    /** Water boiled off into steam this tick, in mB, with the burner at {@code rate} mB/t (at most {@link #STEAM_RATE}). */
    public static int boil(double temperature, boolean firing, int water, int steamRoom, int rate) {
        if (!firing || temperature < BOILING) return 0;
        return Math.max(0, Math.min(Math.min(STEAM_RATE, rate), Math.min(water, steamRoom)));
    }

    /** Water boiled off into steam this tick at full fire, in mB. */
    public static int boil(double temperature, boolean firing, int water, int steamRoom) {
        return boil(temperature, firing, water, steamRoom, STEAM_RATE);
    }

    /** Cold feed water hitting a dry, red-hot drum turns to steam all at once. */
    public static boolean flashes(double temperature, int water) {
        return temperature > OVERHEAT && water > 0;
    }

    public static boolean bursts(double temperature) {
        return temperature >= BURST;
    }
}
