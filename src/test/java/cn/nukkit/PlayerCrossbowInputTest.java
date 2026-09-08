package cn.nukkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerCrossbowInputTest {

    @Test
    void oneChargeConsumesOneArrowDespiteClientTailClicks() {
        boolean loaded = false;
        boolean usingItem = false;
        int tailUntilTick = Integer.MIN_VALUE;
        int arrows = 3;
        int completedCharges = 0;

        int[] clickTicks = {100, 125, 126, 136, 149};
        for (int currentTick : clickTicks) {
            Player.CrossbowClickDecision decision = Player.decideCrossbowClick(
                    loaded, usingItem, currentTick, tailUntilTick, true);
            switch (decision) {
                case START_LOADING:
                    usingItem = true;
                    break;
                case FINISH_LOADING:
                    usingItem = false;
                    loaded = true;
                    completedCharges++;
                    arrows--;
                    tailUntilTick = currentTick + 12;
                    break;
                case IGNORE_TAIL:
                    tailUntilTick = currentTick + 12;
                    break;
                case SHOOT:
                    loaded = false;
                    break;
            }
        }

        assertEquals(2, arrows);
        assertEquals(1, completedCharges);
        assertFalse(loaded);
        assertFalse(usingItem);
    }

    @Test
    void changingCrossbowSlotStartsAnIndependentCharge() {
        assertEquals(Player.CrossbowClickDecision.START_LOADING,
                Player.decideCrossbowClick(false, true, 105, 120, false));
        assertEquals(Player.CrossbowClickDecision.SHOOT,
                Player.decideCrossbowClick(true, true, 105, 120, false));
    }
}
