package cn.nukkit.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.item.EntityFishingHook;
import cn.nukkit.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemFishingRodTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    /**
     * Bedrock's fishing rod lasts 385 uses; the Java value of 64 is a different scale.
     * The server breaks the rod at {@code damage >= max}, so a Java value made the rod
     * vanish with ~83% of the durability bar still showing.
     */
    @Test
    void durabilityMatchesTheBedrockClientItem() {
        assertEquals(385, new ItemFishingRod().getMaxDurability());
    }

    /**
     * Regression: the reel path never broke the rod, so past max damage the rod lingered
     * in the hand and was destroyed by the generic tool-break check the next time the
     * player activated a block (chest, trapdoor, ...).
     */
    @Test
    void reelingPastDurabilityBreaksTheRod() {
        Player player = fishingPlayer(false);
        ItemFishingRod rod = new ItemFishingRod();
        rod.setDamage(rod.getMaxDurability() - 1);

        assertTrue(rod.onClickAir(player, null));

        assertEquals(0, rod.getCount());
    }

    @Test
    void reelingCostsOneDurabilityPerRetrieve() {
        Player player = fishingPlayer(false);
        ItemFishingRod rod = new ItemFishingRod();

        rod.onClickAir(player, null);

        assertEquals(1, rod.getDamage());
        assertEquals(1, rod.getCount());
    }

    @Test
    void creativeRodsDoNotWear() {
        Player player = fishingPlayer(true);
        ItemFishingRod rod = new ItemFishingRod();

        for (int i = 0; i < 500; i++) {
            rod.onClickAir(player, null);
        }

        assertEquals(0, rod.getDamage());
        assertEquals(1, rod.getCount());
    }

    private static Player fishingPlayer(boolean creative) {
        Player player = mock(Player.class);
        player.level = mock(Level.class);
        player.fishing = mock(EntityFishingHook.class);
        when(player.isCreative()).thenReturn(creative);
        when(player.fishing.getTarget()).thenReturn(0L);
        return player;
    }
}
