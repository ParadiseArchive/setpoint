package io.hookdev.setpoint.physics;

/**
 * A small steam turbine with a generator on its shaft. Steam flow sets the speed the rotor wants to
 * run at; the rotor's inertia makes it spin up and run down smoothly. Power follows steam flow and speed,
 * so a turbine that is still spinning up makes less than its rating.
 */
public final class TurbineModel {
    /** Steam the turbine can swallow, in mB per tick. Matches one boiler. */
    public static final int MAX_STEAM = BoilerModel.STEAM_RATE;
    public static final double RATED_SPEED = 3000.0;
    /** Fraction of the speed difference closed each tick (time constant 50 ticks, 2.5 s). */
    public static final double INERTIA = 0.02;
    /** FE made per mB of steam at rated speed: one boiler gives 60 FE/t. */
    public static final double FE_PER_MB = 3.0;

    private TurbineModel() {}

    /** Rotor speed after one tick with this much steam, in rpm. */
    public static double nextSpeed(double speed, int steam) {
        double target = RATED_SPEED * Math.min(steam, MAX_STEAM) / MAX_STEAM;
        double next = speed + (target - speed) * INERTIA;
        return next < 1.0 && target == 0 ? 0.0 : next;
    }

    /** FE made this tick. */
    public static int power(double speed, int steam) {
        return (int) Math.round(Math.min(steam, MAX_STEAM) * FE_PER_MB * Math.min(1.0, speed / RATED_SPEED));
    }
}
