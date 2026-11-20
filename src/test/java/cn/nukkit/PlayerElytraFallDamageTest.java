package cn.nukkit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerElytraFallDamageTest {

    @ParameterizedTest
    @CsvSource({
            "0.49, true",
            "0.0, true",
            "-0.49, true",
            "-0.5, true",
            "-10.0, true",
            "0.5, false",
            "0.6, false"
    })
    void safeLandingAllowsUpwardMovementAndLimitsDownwardSpeed(double verticalSpeed, boolean safe) {
        assertEquals(safe, Player.isSafeElytraLanding(verticalSpeed));
    }
}
