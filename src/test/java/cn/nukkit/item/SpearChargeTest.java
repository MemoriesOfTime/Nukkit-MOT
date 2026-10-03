package cn.nukkit.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: the spear charge (hold and release, vanilla {@code minecraft:kinetic_weapon}) wore
 * the spear down on every release, a miss included, and struck at any hold length and any speed.
 * Vanilla strikes only after the tier's warm-up, inside its damage window, and only a target the
 * spear closes in on at 4.6 blocks per second; nothing hit costs nothing.
 */
public class SpearChargeTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    private static ItemSpear spear(boolean hits, AtomicInteger attempts) {
        return new ItemSpearIron() {
            @Override
            boolean chargeStab(Player player) {
                attempts.incrementAndGet();
                return hits;
            }
        };
    }

    private static Player player() {
        Player player = Mockito.mock(Player.class);
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getLevel()).thenReturn(Mockito.mock(Level.class));
        Mockito.when(inventory.getItemInHandFast()).thenReturn(Item.get(Item.AIR));
        return player;
    }

    @Test
    void chargeThatMissesCostsNothing() {
        AtomicInteger attempts = new AtomicInteger();
        ItemSpear spear = spear(false, attempts);
        assertTrue(spear.onRelease(player(), 40));
        assertEquals(1, attempts.get());
        assertEquals(0, spear.getDamage());
    }

    @Test
    void chargeThatHitsWearsTheSpearDown() {
        AtomicInteger attempts = new AtomicInteger();
        ItemSpear spear = spear(true, attempts);
        spear.onRelease(player(), 40);
        assertEquals(1, spear.getDamage());
    }

    @Test
    void releaseBeforeTheWarmUpDoesNotStrike() {
        AtomicInteger attempts = new AtomicInteger();
        ItemSpear spear = spear(true, attempts);
        spear.onRelease(player(), 2);
        assertEquals(0, attempts.get());
        assertEquals(0, spear.getDamage());
    }

    @Test
    void releaseAfterTheDamageWindowDoesNotStrike() {
        AtomicInteger attempts = new AtomicInteger();
        ItemSpear spear = spear(true, attempts);
        spear.onRelease(player(), 12 + 225 + 10);
        assertEquals(0, attempts.get());
        assertEquals(0, spear.getDamage());
    }

    @Test
    void chargeWindowFollowsVanillaKineticWeapon() {
        assertEquals(15, new ItemSpearWood().getChargeDelayTicks());
        assertEquals(300, new ItemSpearWood().getChargeDamageTicks());
        assertEquals(14, new ItemSpearGold().getChargeDelayTicks());
        assertEquals(12, new ItemSpearIron().getChargeDelayTicks());
        assertEquals(225, new ItemSpearIron().getChargeDamageTicks());
        assertEquals(8, new ItemSpearNetherite().getChargeDelayTicks());
        assertEquals(175, new ItemSpearNetherite().getChargeDamageTicks());

        assertFalse(ItemSpear.isChargeInWindow(8, 12, 225));
        assertTrue(ItemSpear.isChargeInWindow(9, 12, 225));
        assertTrue(ItemSpear.isChargeInWindow(240, 12, 225));
        assertFalse(ItemSpear.isChargeInWindow(241, 12, 225));
    }

    @Test
    void chargeNeedsVanillaClosingSpeed() {
        Vector3 look = new Vector3(0, 0, 1);
        Vector3 still = new Vector3(0, 0, 0);
        // Sprinting on the ground, 5.6 blocks per second, at a standing target.
        assertTrue(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.28), still, look)));
        // Walking, 4.3 blocks per second, is too slow.
        assertFalse(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.215), still, look)));
        // Standing still.
        assertFalse(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(still, still, look)));
        // Chasing a target that runs away just as fast closes nothing.
        assertFalse(ItemSpear.isChargeFastEnough(
                ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.28), new Vector3(0, 0, 0.28), look)));
        // Walking into a target that walks towards the spear closes fast enough.
        assertTrue(ItemSpear.isChargeFastEnough(
                ItemSpear.relativeChargeSpeed(new Vector3(0, 0, 0.215), new Vector3(0, 0, -0.1), look)));
        // Running sideways to the look does not count.
        assertFalse(ItemSpear.isChargeFastEnough(ItemSpear.relativeChargeSpeed(new Vector3(0.28, 0, 0), still, look)));
    }
}
