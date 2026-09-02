package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockShulkerBoxBreakTimeTest {

    @Test
    void hasteUsesBedrockBreakSpeed() {
        Player player = mockPlayerWithEmptyHelmet();
        Effect haste = Mockito.mock(Effect.class);
        Mockito.when(player.getEffect(Effect.HASTE)).thenReturn(haste);
        Mockito.when(haste.getAmplifier()).thenReturn(3);

        Block shulkerBox = new BlockUndyedShulkerBox();
        int hasteLevel = 4;
        double expectedSeconds = 3d
                / ((1d + 0.2d * hasteLevel) * Math.pow(1.2d, hasteLevel));

        assertEquals(3d,
                shulkerBox.calculateBreakTimeNotInAir(Item.AIR_ITEM, null),
                1e-12,
                "base hand break time");
        assertEquals(expectedSeconds,
                shulkerBox.calculateBreakTimeNotInAir(Item.AIR_ITEM, player),
                1e-12,
                "haste amplifier 3 must match Bedrock client progress");
    }

    @Test
    void conduitPowerKeepsExistingBreakSpeed() {
        Player player = mockPlayerWithEmptyHelmet();
        Mockito.when(player.hasEffect(Effect.CONDUIT_POWER)).thenReturn(true);
        Block shulkerBox = new BlockUndyedShulkerBox();

        assertEquals(3d / 1.4d,
                shulkerBox.calculateBreakTimeNotInAir(Item.AIR_ITEM, player),
                1e-12,
                "conduit power must keep its existing linear multiplier");
    }

    private static Player mockPlayerWithEmptyHelmet() {
        Player player = Mockito.mock(Player.class);
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(inventory.getHelmet()).thenReturn(Item.AIR_ITEM);
        return player;
    }
}
