package cn.nukkit.command.defaults;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectCommandSafetyTest {

    @Test
    void acceptsOnlyBoundedDurations() {
        assertTrue(EffectCommand.isSafeDurationSeconds(0));
        assertTrue(EffectCommand.isSafeDurationSeconds(EffectCommand.MAX_DURATION_SECONDS));

        assertFalse(EffectCommand.isSafeDurationSeconds(-1));
        assertFalse(EffectCommand.isSafeDurationSeconds(EffectCommand.MAX_DURATION_SECONDS + 1));
        assertFalse(EffectCommand.isSafeDurationSeconds(Integer.MAX_VALUE));
    }

    @Test
    void acceptsOnlyBoundedAmplifiers() {
        assertTrue(EffectCommand.isSafeAmplifier(0));
        assertTrue(EffectCommand.isSafeAmplifier(EffectCommand.MAX_AMPLIFIER));

        assertFalse(EffectCommand.isSafeAmplifier(-1));
        assertFalse(EffectCommand.isSafeAmplifier(EffectCommand.MAX_AMPLIFIER + 1));
        assertFalse(EffectCommand.isSafeAmplifier(Integer.MAX_VALUE));
    }

    @Test
    void incidentValuesAreRejected() {
        assertFalse(EffectCommand.isSafeDurationSeconds(999999));
        assertFalse(EffectCommand.isSafeAmplifier(99999));
    }
}
