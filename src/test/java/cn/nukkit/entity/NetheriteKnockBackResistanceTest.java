package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.*;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NetheriteKnockBackResistanceTest {
    @Test
    void everyWornPieceContributesOneTenth() {
        Item[] pieces = {new ItemHelmetNetherite(), new ItemChestplateNetherite(),
                new ItemLeggingsNetherite(), new ItemBootsNetherite()};
        for (int count = 0; count <= 4; count++) {
            Player player = wearer(java.util.Arrays.copyOf(pieces, count));
            assertEquals(count * 0.1, player.getKnockBackResistance(), 1e-9);
            Vector3 motion = push(player, 3, 4);
            assertEquals(0.18 * (1 - count * 0.1), motion.x, 1e-9);
            assertEquals(0.3 * (1 - count * 0.1), motion.y, 1e-9);
            assertEquals(0.24 * (1 - count * 0.1), motion.z, 1e-9);
        }
    }

    @Test
    void diamondArmourDoesNotAddResistance() {
        Player player = wearer(new ItemHelmetDiamond(), new ItemChestplateDiamond(),
                new ItemLeggingsDiamond(), new ItemBootsDiamond());
        assertEquals(0, player.getKnockBackResistance());
        assertEquals(0.3, push(player, 1, 0).x, 1e-9);
    }

    @Test
    void mixedArmourOnlyCountsNetherite() {
        Player player = wearer(new ItemHelmetNetherite(), new ItemChestplateDiamond(),
                new ItemLeggingsDiamond(), new ItemBootsNetherite());
        assertEquals(0.2, player.getKnockBackResistance(), 1e-9);
    }

    @Test
    void currentMotionAndVerticalCeilingKeepTheirExistingMeaning() {
        Player player = wearer(new ItemHelmetNetherite());
        player.motionX = 0.2;
        player.motionY = 2;
        player.motionZ = -0.2;
        Vector3 motion = push(player, 1, 0);
        assertEquals(0.37, motion.x, 1e-9);
        assertEquals(0.3, motion.y, 1e-9);
        assertEquals(-0.1, motion.z, 1e-9);
        verify(player).resetFallDistance();
        assertEquals(10, player.knockBackTime);
    }

    @Test
    void fullOrGreaterResistanceSkipsTheImpulse() {
        for (double resistance : new double[]{1, 2}) {
            Player player = wearer();
            when(player.getKnockBackResistance()).thenReturn(resistance);
            player.knockBack(null, 1, 1, 0, 0.3);
            verify(player, never()).setMotion(any(Vector3.class));
            verify(player, never()).resetFallDistance();
        }
    }

    @Test
    void negativeResistanceDoesNotAmplifyKnockback() {
        Player player = wearer();
        when(player.getKnockBackResistance()).thenReturn(-1d);
        assertEquals(0.3, push(player, 1, 0).x, 1e-9);
    }

    @Test
    void zeroDirectionStillDoesNothing() {
        Player player = wearer(new ItemHelmetNetherite());
        player.knockBack(null, 1, 0, 0, 0.3);
        verify(player, never()).setMotion(any(Vector3.class));
    }

    @Test
    void attributePacketReflectsWornNetherite() {
        Player player = wearer(new ItemHelmetNetherite(), new ItemBootsNetherite());
        doCallRealMethod().when(player).sendKnockBackResistanceAttribute();
        player.sendKnockBackResistanceAttribute();
        ArgumentCaptor<Attribute> attribute = ArgumentCaptor.forClass(Attribute.class);
        verify(player).setAttribute(attribute.capture());
        assertEquals(Attribute.KNOCKBACK_RESISTANCE, attribute.getValue().getId());
        assertEquals("minecraft:knockback_resistance", attribute.getValue().getName());
        assertEquals(0.2f, attribute.getValue().getValue(), 1e-9f);
    }

    @Test
    void attributeValueIsClampedToAttributeRange() {
        Player player = wearer();
        when(player.getKnockBackResistance()).thenReturn(2d);
        doCallRealMethod().when(player).sendKnockBackResistanceAttribute();
        player.sendKnockBackResistanceAttribute();
        ArgumentCaptor<Attribute> attribute = ArgumentCaptor.forClass(Attribute.class);
        verify(player).setAttribute(attribute.capture());
        assertEquals(1f, attribute.getValue().getValue());
    }

    @Test
    void unchangedResistanceSkipsThePacket() {
        Player player = wearer(new ItemHelmetNetherite());
        doCallRealMethod().when(player).sendKnockBackResistanceAttribute();
        player.sendKnockBackResistanceAttribute();
        player.sendKnockBackResistanceAttribute();
        verify(player, times(1)).setAttribute(any(Attribute.class));
    }

    @Test
    void armourSlotChangeSyncsTheAttribute() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        player.inventory = inventory;
        player.spawned = true;
        when(inventory.getHolder()).thenReturn(player);
        when(inventory.getSize()).thenReturn(36);
        when(inventory.getViewers()).thenReturn(java.util.Collections.emptySet());
        when(player.getViewers()).thenReturn(new java.util.HashMap<>());
        doCallRealMethod().when(inventory).onSlotChange(anyInt(), any(), anyBoolean());

        inventory.onSlotChange(36, Item.get(Item.AIR), true);

        verify(player).sendKnockBackResistanceAttribute();
    }

    private static Player wearer(Item... armour) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        player.inventory = inventory;
        when(inventory.getArmorContents()).thenReturn(armour);
        doCallRealMethod().when(player).getKnockBackResistance();
        doCallRealMethod().when(player).knockBack(nullable(Entity.class), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        return player;
    }

    private static Vector3 push(Player player, double x, double z) {
        player.knockBack(null, 1, x, z, 0.3);
        ArgumentCaptor<Vector3> motion = ArgumentCaptor.forClass(Vector3.class);
        verify(player).setMotion(motion.capture());
        return motion.getValue();
    }
}
