package cn.nukkit;

import cn.nukkit.network.encryption.EncryptionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A name padded with whitespace is the same player as the trimmed name.
 * <p>
 * The padded form used to derive its own offline identity, so one account owned two data files
 * and relogging between the spellings loaded the inventory of the other file.
 */
class PlayerVerifiedLoginNameTest {

    @Test
    void paddedNameIsTrimmedButInnerSpacesStay() {
        assertEquals("Steve alex", Player.verifiedLoginName("Steve alex "));
        assertEquals("Steve alex", Player.verifiedLoginName(" Steve alex"));
        assertEquals("STEVE", Player.verifiedLoginName("  STEVE"));
        assertEquals("Steve  alex", Player.verifiedLoginName("Steve  alex "));
        assertEquals("Name", Player.verifiedLoginName("§aName §r"));
        assertNull(Player.verifiedLoginName(null));
    }

    @Test
    void paddedAndTrimmedNamesDeriveTheSameOfflineIdentity() {
        assertEquals(EncryptionUtils.deriveOfflineIdentity("Name"),
                EncryptionUtils.deriveOfflineIdentity(Player.verifiedLoginName("Name ")));
        assertNotEquals(EncryptionUtils.deriveOfflineIdentity("Steve alex"),
                EncryptionUtils.deriveOfflineIdentity(Player.verifiedLoginName("Steve  alex")),
                "a space inside the name is part of it");
    }

    @Test
    void paddingNoLongerHidesReservedNames() {
        assertEquals("rcon", Player.verifiedLoginName("rcon "));
        assertEquals("console", Player.verifiedLoginName(" console"));
    }
}
