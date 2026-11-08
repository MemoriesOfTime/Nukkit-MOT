package cn.nukkit;

import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.ItemElytra;
import cn.nukkit.item.enchantment.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PlayerElytraDurabilityTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void unenchantedElytraLosesOneDurabilityPerSecond() {
        Fixture fixture = new Fixture(20);

        fixture.player.updateElytraDurability();

        assertEquals(1, fixture.elytra.getDamage());
        verify(fixture.inventory).setChestplate(fixture.elytra);
    }

    @Test
    void durabilityIsOnlyCheckedEveryTwentyTicks() {
        Fixture fixture = new Fixture(19);

        fixture.player.updateElytraDurability();

        assertEquals(0, fixture.elytra.getDamage());
        verify(fixture.inventory, never()).setChestplate(fixture.elytra);
    }

    @Test
    void unbreakingUsesIndependentDurabilityChance() {
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextInt(4)).thenReturn(0, 3);
        Fixture damaged = new Fixture(20);
        Fixture protectedByUnbreaking = new Fixture(40);
        damaged.elytra.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_DURABILITY).setLevel(3));
        protectedByUnbreaking.elytra.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_DURABILITY).setLevel(3));

        try (MockedStatic<ThreadLocalRandom> current = org.mockito.Mockito.mockStatic(ThreadLocalRandom.class)) {
            current.when(ThreadLocalRandom::current).thenReturn(random);
            damaged.player.updateElytraDurability();
            protectedByUnbreaking.player.updateElytraDurability();
        }

        assertEquals(1, damaged.elytra.getDamage());
        assertEquals(0, protectedByUnbreaking.elytra.getDamage());
        verify(random, org.mockito.Mockito.times(2)).nextInt(4);
        verify(protectedByUnbreaking.inventory, never()).setChestplate(protectedByUnbreaking.elytra);
    }

    @Test
    void elytraStopsImmediatelyWhenDurabilityReachesOne() {
        Fixture fixture = new Fixture(20);
        fixture.elytra.setDamage(fixture.elytra.getMaxDurability() - 2);

        fixture.player.updateElytraDurability();

        assertEquals(432, fixture.elytra.getDamage());
        verify(fixture.inventory).setChestplate(fixture.elytra);
        verify(fixture.player).setGliding(false);
    }

    @Test
    void unbreakingCannotKeepBrokenElytraGliding() {
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        Fixture fixture = new Fixture(20);
        fixture.elytra.setDamage(fixture.elytra.getMaxDurability() - 1);
        fixture.elytra.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_DURABILITY).setLevel(3));

        try (MockedStatic<ThreadLocalRandom> current = org.mockito.Mockito.mockStatic(ThreadLocalRandom.class)) {
            current.when(ThreadLocalRandom::current).thenReturn(random);
            fixture.player.updateElytraDurability();
        }

        verify(fixture.player).setGliding(false);
        verify(random, never()).nextInt(4);
        verify(fixture.inventory, never()).setChestplate(fixture.elytra);
    }

    @Test
    void elytraAtOneRemainingDurabilityCannotStartGliding() {
        ItemElytra elytra = new ItemElytra();
        elytra.setDamage(elytra.getMaxDurability() - 1);
        org.junit.jupiter.api.Assertions.assertFalse(Player.isUsableElytra(elytra));

        elytra.setDamage(elytra.getMaxDurability() - 2);
        org.junit.jupiter.api.Assertions.assertTrue(Player.isUsableElytra(elytra));
    }

    @Test
    void elytraStopsWorkingAtOneRemainingDurabilityWithoutBreaking() {
        Fixture fixture = new Fixture(20);
        fixture.elytra.setDamage(fixture.elytra.getMaxDurability() - 1);

        fixture.player.updateElytraDurability();

        assertEquals(432, fixture.elytra.getDamage());
        verify(fixture.player).setGliding(false);
        verify(fixture.inventory, never()).setChestplate(fixture.elytra);
    }

    @Test
    void creativeAndUnbreakableElytraDoNotLoseDurability() {
        Fixture creative = new Fixture(20);
        creative.player.gamemode = Player.CREATIVE;
        Fixture unbreakable = new Fixture(20);
        unbreakable.elytra.setUnbreakable(true);

        creative.player.updateElytraDurability();
        unbreakable.player.updateElytraDurability();

        assertEquals(0, creative.elytra.getDamage());
        assertEquals(0, unbreakable.elytra.getDamage());
    }

    @Test
    void bedrockDurabilityAllowsFourHundredThirtyTwoDamagePoints() {
        assertEquals(433, new ItemElytra().getMaxDurability());
    }

    private static final class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final ItemElytra elytra = new ItemElytra();

        Fixture(int age) {
            player.age = age;
            player.gamemode = Player.SURVIVAL;
            when(player.getInventory()).thenReturn(inventory);
            when(player.isGliding()).thenReturn(true);
            when(inventory.getChestplateFast()).thenReturn(elytra);
        }
    }
}
