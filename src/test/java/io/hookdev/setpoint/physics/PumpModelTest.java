package io.hookdev.setpoint.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.hookdev.setpoint.physics.PumpModel.Inputs;
import io.hookdev.setpoint.physics.PumpModel.Result;
import io.hookdev.setpoint.physics.PumpModel.State;

class PumpModelTest {
    private static Inputs flooded(double suction, double discharge, double pipes) {
        return new Inputs(suction, discharge, pipes / 2, pipes / 2, true, true, 0);
    }

    @Test
    void curveHasShutoffHeadAtZeroFlowAndNoHeadAtRunOut() {
        assertEquals(PumpModel.SHUTOFF_HEAD, PumpModel.curveHead(0, 0), 1e-9);
        assertEquals(0, PumpModel.curveHead(PumpModel.MAX_FLOW, 0), 1e-9);
    }

    @Test
    void operatingPointLiesOnBothCurves() {
        Inputs in = flooded(0.5, 4.5, 10);
        Result r = PumpModel.solve(in);
        assertEquals(State.OK, r.state());
        double staticHead = in.dischargeSurface() - in.suctionSurface();
        double systemHead = staticHead + PumpModel.FRICTION * 10 * r.flow() * r.flow();
        assertEquals(PumpModel.curveHead(r.flow(), 0), systemHead, 1e-6);
    }

    @Test
    void moreLiftMeansLessFlow() {
        double low = PumpModel.solve(flooded(0.5, 2, 6)).flow();
        double high = PumpModel.solve(flooded(0.5, 8, 6)).flow();
        assertTrue(high < low, "flow should drop when pumping higher");
    }

    @Test
    void longerPipesMeanLessFlow() {
        double shortRun = PumpModel.solve(flooded(0.5, 2, 4)).flow();
        double longRun = PumpModel.solve(flooded(0.5, 2, 60)).flow();
        assertTrue(longRun < shortRun, "flow should drop with more friction");
    }

    @Test
    void cannotBeatStaticHeadAboveShutoff() {
        Result r = PumpModel.solve(flooded(0, PumpModel.SHUTOFF_HEAD + 1, 4));
        assertEquals(State.DEADHEAD, r.state());
        assertEquals(0, r.flow(), 1e-9);
    }

    @Test
    void floodedSuctionDoesNotCavitate() {
        assertEquals(State.OK, PumpModel.solve(flooded(1, 3, 6)).state());
    }

    @Test
    void bigSuctionLiftCavitatesAndLosesFlow() {
        Result r = PumpModel.solve(new Inputs(-7, -2, 8, 4, true, true, 0));
        assertEquals(State.CAVITATING, r.state());
        assertTrue(r.npshA() < r.npshR());
        assertTrue(r.cavitation() > 0);
    }

    @Test
    void emptySourceIsDryRun() {
        Result r = PumpModel.solve(new Inputs(0, 2, 2, 2, false, true, 0));
        assertEquals(State.DRY_RUN, r.state());
        assertEquals(1.0, r.cavitation(), 1e-9);
    }

    @Test
    void closedDischargeIsDeadhead() {
        Result r = PumpModel.solve(new Inputs(1, 2, 2, 2, true, false, 0));
        assertEquals(State.DEADHEAD, r.state());
        assertEquals(PumpModel.SHUTOFF_HEAD, r.head(), 1e-9);
    }

    @Test
    void wornImpellerPumpsLess() {
        double fresh = PumpModel.solve(new Inputs(0.5, 4, 3, 3, true, true, 0)).flow();
        double worn = PumpModel.solve(new Inputs(0.5, 4, 3, 3, true, true, 1)).flow();
        assertTrue(worn < fresh);
    }
}
