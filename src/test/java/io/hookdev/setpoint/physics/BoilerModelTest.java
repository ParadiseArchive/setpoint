package io.hookdev.setpoint.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoilerModelTest {
    @Test
    void coldBoilerReachesBoilingInTenSeconds() {
        double t = BoilerModel.AMBIENT;
        int ticks = 0;
        while (t < BoilerModel.BOILING) {
            t = BoilerModel.nextTemperature(t, true, 4_000);
            ticks++;
        }
        assertTrue(ticks >= 199 && ticks <= 201, "took " + ticks + " ticks");
    }

    @Test
    void noSteamBelowBoilingPoint() {
        assertEquals(0, BoilerModel.boil(99.9, true, 4_000, 4_000));
        assertEquals(BoilerModel.STEAM_RATE, BoilerModel.boil(100, true, 4_000, 4_000));
    }

    @Test
    void steamIsLimitedByWaterAndRoom() {
        assertEquals(5, BoilerModel.boil(100, true, 5, 4_000));
        assertEquals(3, BoilerModel.boil(100, true, 4_000, 3));
        assertEquals(0, BoilerModel.boil(100, false, 4_000, 4_000));
    }

    @Test
    void boilingWaterHoldsTheDrumAtOneHundred() {
        assertEquals(BoilerModel.BOILING, BoilerModel.nextTemperature(100, true, 1_000), 1e-9);
    }

    @Test
    void aDryDrumKeepsHeating() {
        assertTrue(BoilerModel.nextTemperature(100, true, 0) > 100);
    }

    @Test
    void fireOutCoolsSlowlyToAmbient() {
        assertEquals(99.95, BoilerModel.nextTemperature(100, false, 1_000), 1e-9);
        assertEquals(BoilerModel.AMBIENT, BoilerModel.nextTemperature(BoilerModel.AMBIENT, false, 1_000), 1e-9);
    }

    @Test
    void tripHasAResetBand() {
        assertFalse(BoilerModel.tripped(false, 0.2));
        assertTrue(BoilerModel.tripped(false, 0.05));
        assertTrue(BoilerModel.tripped(true, 0.2), "stays tripped until the level is back above the reset level");
        assertFalse(BoilerModel.tripped(true, 0.3));
    }

    @Test
    void waterOnAHotDryDrumFlashes() {
        assertTrue(BoilerModel.flashes(300, 100));
        assertFalse(BoilerModel.flashes(300, 0));
        assertFalse(BoilerModel.flashes(100, 100));
    }

    @Test
    void steamInTheDrumIsPressure() {
        assertEquals(0, BoilerModel.pressure(0), 1e-9);
        assertEquals(10, BoilerModel.pressure(4_000), 1e-9);
    }

    @Test
    void pressureTripHasAResetBand() {
        assertFalse(BoilerModel.pressureTripped(false, 8.9));
        assertTrue(BoilerModel.pressureTripped(false, 9.0));
        assertTrue(BoilerModel.pressureTripped(true, 7.5), "stays tripped until the pressure is below the reset pressure");
        assertFalse(BoilerModel.pressureTripped(true, 6.9));
    }

    @Test
    void theDrumBurstsWellAboveItsDesignPressure() {
        assertFalse(BoilerModel.burstsFromPressure(BoilerModel.DESIGN_PRESSURE));
        assertTrue(BoilerModel.burstsFromPressure(13.0));
    }

    @Test
    void partFireMakesLessSteam() {
        assertEquals(5, BoilerModel.boil(100, true, 1_000, 1_000, 5));
        assertEquals(BoilerModel.STEAM_RATE, BoilerModel.boil(100, true, 1_000, 1_000, 99), "never more than full fire");
    }
}
