package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTotem;
import cn.nukkit.level.Level;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.potion.Effect;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real Player -> EntityHumanType -> EntityLiving -> Entity attack chain. */
class PlayerTotemHandPriorityTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
        Effect.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {589, 786, 2169})
    void mainHandWinsOverOffhandTaggedTotemOnLethalMelee(int protocol) {
        Player player = player(protocol);
        Item taggedTotem = new ItemTotem(0);
        taggedTotem.setNamedTag(new CompoundTag().putString("testMarker", "offhand"));
        player.getInventory().setItemInHand(new ItemTotem(0));
        player.getOffhandInventory().setItem(0, taggedTotem);
        EntityDamageEvent hit = melee(player, 30);

        assertFalse(player.attack(hit));

        assertTrue(hit.isCancelled());
        assertEquals(1, player.getHealth());
        assertTrue(player.getInventory().getItemInHand().isNull());
        assertTrue(player.getOffhandInventory().getItem(0).equalsExact(taggedTotem));
        verify(player, times(1)).dataPacket(argThat(packet -> packet instanceof EntityEventPacket event
                && event.event == EntityEventPacket.CONSUME_TOTEM));
        verify(player).extinguish();
        verify(player).removeAllEffects(EntityPotionEffectEvent.Cause.TOTEM);
        verify(player).addEffect(argThat(effect -> effect.getId() == Effect.REGENERATION
                && effect.getDuration() == 800 && effect.getAmplifier() == 1), eq(EntityPotionEffectEvent.Cause.TOTEM));
        verify(player).addEffect(argThat(effect -> effect.getId() == Effect.FIRE_RESISTANCE
                && effect.getDuration() == 800), eq(EntityPotionEffectEvent.Cause.TOTEM));
        verify(player).addEffect(argThat(effect -> effect.getId() == Effect.ABSORPTION
                && effect.getDuration() == 100 && effect.getAmplifier() == 1), eq(EntityPotionEffectEvent.Cause.TOTEM));

        assertFalse(player.attack(hit));
        assertEquals(1, player.getOffhandInventory().getItem(0).getCount(), "replayed cancelled hit cannot consume twice");
    }

    @Test
    void mainHandWinsOverPlainOffhandTotem() {
        Player player = player(786);
        player.getInventory().setItemInHand(new ItemTotem(0));
        player.getOffhandInventory().setItem(0, new ItemTotem(0));
        assertFalse(player.attack(melee(player, 30)));
        assertTrue(player.getInventory().getItemInHand().isNull());
        assertEquals(1, player.getOffhandInventory().getItem(0).getCount());
    }

    @Test
    void genericMainHandTotemAlsoRescues() {
        Player player = player(2169);
        player.getInventory().setItemInHand(new Item(Item.TOTEM, 0, 1, "Totem of Undying"));
        assertFalse(player.attack(melee(player, 30)));
        assertEquals(1, player.getHealth());
        assertTrue(player.getInventory().getItemInHand().isNull());
    }

    @Test
    void offhandTaggedTotemRescuesWhenMainHandIsNotATotem() {
        Player player = player(786);
        Item taggedTotem = new ItemTotem(0);
        taggedTotem.setNamedTag(new CompoundTag().putString("testMarker", "offhand"));
        player.getInventory().setItemInHand(Item.get(Item.DIAMOND_SWORD));
        player.getOffhandInventory().setItem(0, taggedTotem);
        assertFalse(player.attack(melee(player, 30)));
        assertEquals(1, player.getHealth());
        assertEquals(Item.DIAMOND_SWORD, player.getInventory().getItemInHand().getId());
        assertTrue(player.getOffhandInventory().getItem(0).isNull());
    }

    @Test
    void ordinaryDamageDoesNotConsumeATotem() {
        Player player = player(786);
        player.getInventory().setItemInHand(new ItemTotem(0));
        player.getOffhandInventory().setItem(0, new ItemTotem(0));
        assertTrue(player.attack(melee(player, 2)));
        assertEquals(18, player.getHealth());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
        assertEquals(1, player.getOffhandInventory().getItem(0).getCount());
    }

    @Test
    void cancelledDamageDoesNotConsumeATotem() {
        Player player = player(786);
        player.getInventory().setItemInHand(new ItemTotem(0));
        EntityDamageEvent hit = melee(player, 30);
        hit.setCancelled(true);
        assertFalse(player.attack(hit));
        assertEquals(20, player.getHealth());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
    }

    @Test
    void absorptionPreventsUnnecessaryTotemConsumption() {
        Player player = player(786);
        when(player.getAbsorption()).thenReturn(15f);
        player.getInventory().setItemInHand(new ItemTotem(0));
        assertTrue(player.attack(melee(player, 30)));
        assertEquals(5, player.getHealth());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
    }

    @Test
    void emptyHandsDoNotPreventDeath() {
        Player player = player(786);
        assertTrue(player.attack(melee(player, 30)));
        assertTrue(player.getHealth() <= 0);
    }

    @Test
    void voidAndSuicideStillBypassBothHands() {
        for (EntityDamageEvent.DamageCause cause : new EntityDamageEvent.DamageCause[]{
                EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.SUICIDE}) {
            Player player = player(2169);
            player.getInventory().setItemInHand(new ItemTotem(0));
            player.getOffhandInventory().setItem(0, new ItemTotem(0));
            assertTrue(player.attack(new EntityDamageEvent(player, cause, 30)));
            assertTrue(player.getHealth() <= 0);
            assertEquals(1, player.getInventory().getItemInHand().getCount());
            assertEquals(1, player.getOffhandInventory().getItem(0).getCount());
        }
    }

    private static EntityDamageEvent melee(Player target, float amount) {
        return new EntityDamageByEntityEvent(mock(Player.class), target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, amount);
    }

    private static Player player(int protocol) {
        Player player = mock(Player.class);
        Entity entity = player;
        entity.server = MockServer.get();
        entity.health = 20;
        try {
            var viewers = Entity.class.getDeclaredField("hasSpawned");
            viewers.setAccessible(true);
            viewers.set(player, new HashMap<>());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        player.protocol = protocol;
        Level level = mock(Level.class);
        entity.level = level;
        when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(player.getLevel()).thenReturn(level);
        when(player.getBoundingBox()).thenReturn(new SimpleAxisAlignedBB(0, 0, 0, 0.6, 1.8, 0.6));
        when(player.getServer()).thenReturn(MockServer.get());
        when(player.isAlive()).thenAnswer(invocation -> entity.health > 0);
        when(player.getHealth()).thenAnswer(invocation -> entity.health);
        doAnswer(invocation -> { entity.health = invocation.getArgument(0); return null; })
                .when(player).setHealth(anyFloat());
        PlayerInventory inventory = new PlayerInventory(player);
        ((EntityHumanType) player).inventory = inventory;
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(player));
        doCallRealMethod().when((EntityHumanType) player).damageArmor(any(Item.class), nullable(Entity.class),
                anyFloat(), anyBoolean(), any(EntityDamageEvent.DamageCause.class));
        doCallRealMethod().when(player).attack(any(EntityDamageEvent.class));
        return player;
    }
}
