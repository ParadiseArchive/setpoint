package io.hookdev.setpoint.control;

/**
 * PI(D) controller in velocity form, working in percent of range.
 * Velocity form gives bumpless MAN to AUTO transfers and needs no separate anti-windup:
 * the output simply stops at 0 or 100 %.
 */
public final class PidController {
    public enum Mode { MAN, AUTO, CAS }

    /** DIRECT: output rises when PV rises above SP. REVERSE: output falls. */
    public enum Action { DIRECT, REVERSE }

    /** Simple tuning presets instead of raw Kc / Ti / Td. Gain in %OP per %PV, integral time in seconds. */
    public enum Tuning {
        SLOW(1.0, 40.0), NORMAL(2.0, 20.0), FAST(4.0, 10.0);

        public final double gain;
        public final double integralSeconds;

        Tuning(double gain, double integralSeconds) {
            this.gain = gain;
            this.integralSeconds = integralSeconds;
        }

        public Tuning next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private double output;
    private double lastError;
    private boolean primed;

    public double output() {
        return output;
    }

    /** Forget the last error, so the next step takes no proportional kick: for when a measurement comes back. */
    public void reprime() {
        primed = false;
    }

    /** Operator sets the output (MAN mode). */
    public void setOutput(double percent) {
        output = clamp(percent);
    }

    /**
     * One control step. In MAN the output is left alone but the error is still tracked,
     * so switching to AUTO does not bump the output.
     */
    public double update(Mode mode, Action action, Tuning tuning, double pv, double sp, double dtSeconds) {
        double error = action == Action.DIRECT ? pv - sp : sp - pv;
        if (!primed) {
            lastError = error;
            primed = true;
        }
        if (mode != Mode.MAN) {
            double delta = tuning.gain * ((error - lastError) + dtSeconds / tuning.integralSeconds * error);
            output = clamp(output + delta);
        }
        lastError = error;
        return output;
    }

    public static double clamp(double percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
