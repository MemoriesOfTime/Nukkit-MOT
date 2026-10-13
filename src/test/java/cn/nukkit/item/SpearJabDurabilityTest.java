package cn.nukkit.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: 1.21.110+ clients jab with a spear through the attack button
 * ({@code USE_ITEM_ACTION_USE_AS_ATTACK}). The core ignored that action, so a jab neither hit nor
 * wore the spear down. A jab that hits must cost one durability point like any melee hit.
 */
public class SpearJabDurabilityTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    private static ItemSpear spear(boolean hits) {
        return new ItemSpearIron() {
            @Override
            boolean stab(Player player) {
                return hits;
            }
        };
    }

    private static Player player(boolean creative) {
        Player player = Mockito.mock(Player.class);
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(inventory.getItemInHandFast()).thenReturn(Item.get(Item.AIR));
        Mockito.when(player.isCreative()).thenReturn(creative);
        return player;
    }

    @Test
    void jabThatHitsWearsTheSpearDown() {
        ItemSpear spear = spear(true);
        assertTrue(spear.onJab(player(false)));
        assertEquals(1, spear.getDamage());
        assertTrue(spear.onJab(player(false)));
        assertEquals(2, spear.getDamage());
    }

    @Test
    void missedJabCostsNothing() {
        ItemSpear spear = spear(false);
        assertFalse(spear.onJab(player(false)));
        assertEquals(0, spear.getDamage());
    }

    @Test
    void creativeJabKeepsTheSpear() {
        ItemSpear spear = spear(true);
        assertTrue(spear.onJab(player(true)));
        assertEquals(0, spear.getDamage());
    }

    @Test
    void brokenSpearDoesNotJab() {
        ItemSpear spear = spear(true);
        spear.setDamage(spear.getMaxDurability());
        assertFalse(spear.onJab(player(false)));
        assertEquals(spear.getMaxDurability(), spear.getDamage());
    }

    @Test
    void jabCoolDownFollowsVanillaSwingDuration() {
        assertEquals(13, new ItemSpearWood().getJabCooldownTicks());
        assertEquals(19, new ItemSpearIron().getJabCooldownTicks());
        assertEquals(23, new ItemSpearNetherite().getJabCooldownTicks());
    }
}
