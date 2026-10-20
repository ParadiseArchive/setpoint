package io.hookdev.setpoint.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.hookdev.setpoint.control.PidController.Action;
import io.hookdev.setpoint.control.PidController.Mode;
import io.hookdev.setpoint.control.PidController.Tuning;

class PidControllerTest {
    private static final double DT = 0.05; // one tick

    /**
     * Level in a tank (% of range) with a fixed outflow and an inflow set by the controller output,
     * roughly the numbers of a pump filling a 16 bucket tank. Returns the level after {@code seconds}.
     */
    private static double simulateLevelLoop(Tuning tuning, double startLevel, double sp, double seconds, double[] maxOvershoot) {
        PidController pid = new PidController();
        pid.setOutput(50);
        double level = startLevel;
        double outflowPerTick = 75.0 / 16_000 * 100;   // % per tick
        double maxInflowPerTick = 94.0 / 16_000 * 100;  // % per tick at 100 % output
        for (int tick = 0; tick < seconds / DT; tick++) {
            double op = pid.update(Mode.AUTO, Action.REVERSE, tuning, level, sp, DT);
            level = Math.max(0, Math.min(100, level + maxInflowPerTick * op / 100 - outflowPerTick));
            maxOvershoot[0] = Math.max(maxOvershoot[0], level - sp);
        }
        return level;
    }

    @ParameterizedTest
    @EnumSource(Tuning.class)
    void levelLoopSettlesAtSetpoint(Tuning tuning) {
        double[] overshoot = {0};
        double seconds = tuning == Tuning.SLOW ? 240 : 120;
        double level = simulateLevelLoop(tuning, 20, 50, seconds, overshoot);
        assertEquals(50, level, 1.0, tuning + " should settle at the setpoint");
        assertTrue(overshoot[0] < 15, tuning + " overshoots too much: " + overshoot[0]);
    }

    @Test
    void manualModeHoldsTheOutput() {
        PidController pid = new PidController();
        pid.setOutput(37);
        for (int i = 0; i < 100; i++) pid.update(Mode.MAN, Action.DIRECT, Tuning.FAST, 80, 20, DT);
        assertEquals(37, pid.output(), 1e-9);
    }

    @Test
    void switchingToAutoDoesNotBump() {
        PidController pid = new PidController();
        pid.setOutput(40);
        for (int i = 0; i < 20; i++) pid.update(Mode.MAN, Action.DIRECT, Tuning.NORMAL, 70, 50, DT);
        double before = pid.output();
        double after = pid.update(Mode.AUTO, Action.DIRECT, Tuning.NORMAL, 70, 50, DT);
        assertTrue(Math.abs(after - before) < 1, "output jumped from " + before + " to " + after);
    }

    @Test
    void actionSetsTheDirection() {
        PidController direct = new PidController();
        direct.setOutput(50);
        PidController reverse = new PidController();
        reverse.setOutput(50);
        for (int i = 0; i < 40; i++) {
            direct.update(Mode.AUTO, Action.DIRECT, Tuning.NORMAL, 70, 50, DT);
            reverse.update(Mode.AUTO, Action.REVERSE, Tuning.NORMAL, 70, 50, DT);
        }
        assertTrue(direct.output() > 50, "direct acting should open on high PV");
        assertTrue(reverse.output() < 50, "reverse acting should close on high PV");
    }

    @Test
    void outputStaysWithinRange() {
        PidController pid = new PidController();
        for (int i = 0; i < 2000; i++) pid.update(Mode.AUTO, Action.DIRECT, Tuning.FAST, 100, 0, DT);
        assertEquals(100, pid.output(), 1e-9);
    }
}
