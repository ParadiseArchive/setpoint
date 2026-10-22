package io.hookdev.setpoint.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompressorModelTest {
    @Test
    void denserGasMovesMore() {
        assertEquals(60, CompressorModel.flow(1.0));
        assertEquals(600, CompressorModel.flow(10.0));
        assertEquals(CompressorModel.MAX_FLOW, CompressorModel.flow(50.0), "the flow has a ceiling");
    }

    @Test
    void itCannotPullAVacuum() {
        assertEquals(0, CompressorModel.flow(CompressorModel.MIN_SUCTION));
        assertEquals(0, CompressorModel.flow(0));
    }

    @Test
    void squeezingHarderTakesMorePower() {
        int toSphere = CompressorModel.power(600, 10, 16);
        int toCylinder = CompressorModel.power(600, 10, 200);
        assertTrue(toCylinder > toSphere, "200 bar should cost more than 16 bar: " + toCylinder + " vs " + toSphere);
        assertEquals((int) Math.ceil(CompressorModel.IDLE_POWER), CompressorModel.power(600, 10, 5), "pushing to a lower pressure costs only the idle power");
    }

    @Test
    void doublingThePressureCostsTheSameEveryTime() {
        int once = CompressorModel.power(100, 1, 2) - (int) CompressorModel.IDLE_POWER;
        int again = CompressorModel.power(100, 50, 100) - (int) CompressorModel.IDLE_POWER;
        assertEquals(once, again);
    }
}
