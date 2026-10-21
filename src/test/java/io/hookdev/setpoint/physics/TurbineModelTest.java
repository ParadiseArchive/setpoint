package io.hookdev.setpoint.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurbineModelTest {
    @Test
    void fullSteamSpinsUpToRatedSpeed() {
        double speed = 0;
        for (int i = 0; i < 400; i++) speed = TurbineModel.nextSpeed(speed, TurbineModel.MAX_STEAM);
        assertEquals(TurbineModel.RATED_SPEED, speed, 5.0);
        assertEquals(60, TurbineModel.power(speed, TurbineModel.MAX_STEAM));
    }

    @Test
    void spinningUpMakesLessThanRated() {
        double speed = TurbineModel.nextSpeed(0, TurbineModel.MAX_STEAM);
        assertTrue(TurbineModel.power(speed, TurbineModel.MAX_STEAM) < 60);
    }

    @Test
    void halfSteamHalfSpeed() {
        double speed = 0;
        for (int i = 0; i < 400; i++) speed = TurbineModel.nextSpeed(speed, TurbineModel.MAX_STEAM / 2);
        assertEquals(TurbineModel.RATED_SPEED / 2, speed, 5.0);
    }

    @Test
    void noSteamRunsDownToAStop() {
        double speed = TurbineModel.RATED_SPEED;
        for (int i = 0; i < 1_000; i++) speed = TurbineModel.nextSpeed(speed, 0);
        assertEquals(0, speed, 1e-9);
        assertEquals(0, TurbineModel.power(speed, 0));
    }

    @Test
    void steamAboveTheRatingIsNotUsed() {
        assertEquals(TurbineModel.power(TurbineModel.RATED_SPEED, TurbineModel.MAX_STEAM),
                TurbineModel.power(TurbineModel.RATED_SPEED, TurbineModel.MAX_STEAM * 3));
    }
}
