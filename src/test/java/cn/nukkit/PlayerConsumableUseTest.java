package cn.nukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A consumable is finished by the server timer, so a use transaction that repeats while the item is
 * still being eaten must be ignored instead of cancelling the use.
 */
class PlayerConsumableUseTest {

    @Test
    @DisplayName("a repeat before the food is ready keeps the item in use")
    void repeatBeforeReadyKeepsUsing() {
        assertTrue(Player.isRepeatedConsumableUse(32, 0));
        assertTrue(Player.isRepeatedConsumableUse(32, 31));
    }

    @Test
    @DisplayName("the transaction that completes the food is handled as before")
    void completedUseIsNotTreatedAsRepeat() {
        assertFalse(Player.isRepeatedConsumableUse(32, 32));
        assertFalse(Player.isRepeatedConsumableUse(32, 100));
    }

    @Test
    @DisplayName("items released by the client keep the old behaviour")
    void itemsWithoutUseDurationAreUntouched() {
        assertFalse(Player.isRepeatedConsumableUse(0, 0));
        assertFalse(Player.isRepeatedConsumableUse(0, 5));
    }
}
