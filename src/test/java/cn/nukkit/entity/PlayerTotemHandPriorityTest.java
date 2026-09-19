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
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.potion.Effect;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
        assertEquals(0, player.noDamageTicks, "cancelled damage must not grant rescue immunity");
        verify(player, never()).removeAllEffects(EntityPotionEffectEvent.Cause.TOTEM);
    }

    @Test
    void absorptionPreventsUnnecessaryTotemConsumption() {
        Player player = player(786);
        when(player.getAbsorption()).thenReturn(15f);
        player.getInventory().setItemInHand(new ItemTotem(0));
        assertTrue(player.attack(melee(player, 30)));
        assertEquals(5, player.getHealth());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
        assertEquals(0, player.noDamageTicks, "absorbed nonlethal damage is not a rescue");
    }

    @Test
    void emptyHandsDoNotPreventDeath() {
        Player player = player(786);
        assertTrue(player.attack(melee(player, 30)));
        assertTrue(player.getHealth() <= 0);
        assertEquals(0, player.noDamageTicks, "death without a totem must not gain immunity");
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

    @ParameterizedTest
    @ValueSource(ints = {589, 786, 2169})
    void distinctLethalHitsCannotConsumeBothHandsBeforeNormalHurtWindowExpires(int protocol) throws Throwable {
        Player player = player(protocol);
        player.getInventory().setItemInHand(new ItemTotem(0));
        player.getOffhandInventory().setItem(0, new ItemTotem(0));
        EntityDamageEvent first = melee(player, 30);

        assertFalse(player.attack(first), "rescue must retain its false result for death consumers");
        assertTrue(first.isCancelled());
        assertTrue(player.getInventory().getItemInHand().isNull());
        assertEquals(10, ((EntityLiving) player).attackTime);
        assertEquals(0, player.noDamageTicks);
        assertFalse(player.attack(melee(player, 30)), "a distinct same-tick hit must be blocked");
        assertEquals(1, player.getOffhandInventory().getItem(0).getCount());

        tickEntityBase(player, 9);
        assertEquals(1, ((EntityLiving) player).attackTime);
        assertFalse(player.attack(melee(player, 20)), "a weaker hit also waits for the ordinary hurt window");
        assertEquals(1, player.getOffhandInventory().getItem(0).getCount());

        tickEntityBase(player, 1);
        assertEquals(0, ((EntityLiving) player).attackTime);
        EntityDamageEvent next = melee(player, 30);
        assertFalse(player.attack(next), "the other hand rescues after the normal window expires");
        assertTrue(next.isCancelled());
        assertTrue(player.getOffhandInventory().getItem(0).isNull());
        assertEquals(10, ((EntityLiving) player).attackTime);
        assertEquals(0, player.noDamageTicks);
    }

    @Test
    void offhandRescueProtectsAReplacementTotemAndDoesNotProtectOtherPlayers() {
        Player player = player(786);
        Player attacker = player(786);
        Player otherVictim = player(786);
        player.getInventory().setItemInHand(Item.get(Item.DIAMOND_SWORD));
        player.getOffhandInventory().setItem(0, new ItemTotem(0));
        EntityDamageEvent hit = new EntityDamageByEntityEvent(attacker, player,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 30);
        assertFalse(player.attack(hit));
        assertTrue(player.getOffhandInventory().getItem(0).isNull());
        assertEquals(Item.DIAMOND_SWORD, player.getInventory().getItemInHand().getId());
        player.getOffhandInventory().setItem(0, new ItemTotem(0));
        assertFalse(player.attack(melee(player, 30)));
        assertEquals(1, player.getOffhandInventory().getItem(0).getCount());
        assertEquals(0, attacker.noDamageTicks);
        assertEquals(0, otherVictim.noDamageTicks);
        assertTrue(otherVictim.attack(melee(otherVictim, 30)));
        assertTrue(otherVictim.getHealth() <= 0);
    }

    @Test
    void rescueUsesTheDamageEventsCooldownAndExpiresWithTheRealEntityTick() throws Throwable {
        Player player = player(786);
        player.getInventory().setItemInHand(new ItemTotem(0));
        EntityDamageEvent hit = melee(player, 30);
        hit.setAttackCooldown(17);
        assertFalse(player.attack(hit));
        assertEquals(17, ((EntityLiving) player).attackTime);
        tickEntityBase(player, 16);
        assertEquals(1, ((EntityLiving) player).attackTime);
        assertFalse(player.attack(melee(player, 30)));
        assertEquals(1, player.getHealth());
        tickEntityBase(player, 1);
        assertTrue(player.attack(melee(player, 30)), "rescue must not make empty hands immortal");
        assertTrue(player.getHealth() <= 0);
    }

    @Test
    void rescueNeverShortensProtectionGrantedWhileItsEffectsAreApplied() {
        Player player = player(786);
        player.getInventory().setItemInHand(new ItemTotem(0));
        doAnswer(invocation -> {
            ((EntityLiving) player).attackTime = 40;
            player.noDamageTicks = 7;
            return null;
        }).when(player).removeAllEffects(EntityPotionEffectEvent.Cause.TOTEM);
        assertFalse(player.attack(melee(player, 30)));
        assertEquals(40, ((EntityLiving) player).attackTime);
        assertEquals(7, player.noDamageTicks, "rescue must not change independent absolute protection");
    }

    @Test
    void strongerDamageRetainsTheSameBehaviorAsAnOrdinaryHurtWindow() {
        Player ordinary = player(786);
        ordinary.setHealth(40);
        assertTrue(ordinary.attack(melee(ordinary, 30)));
        assertEquals(10, ((EntityLiving) ordinary).attackTime);

        Player rescued = player(786);
        rescued.getInventory().setItemInHand(new ItemTotem(0));
        assertFalse(rescued.attack(melee(rescued, 30)));
        assertEquals(10, ((EntityLiving) rescued).attackTime);
        assertEquals(0, rescued.noDamageTicks);

        assertTrue(ordinary.attack(melee(ordinary, 31)), "ordinary windows allow stronger base damage");
        assertTrue(rescued.attack(melee(rescued, 31)), "rescue must retain the same stronger-hit rule");
        assertTrue(ordinary.getHealth() <= 0);
        assertTrue(rescued.getHealth() <= 0);
    }

    @Test
    void suicideAndStrongerVoidDamageStillBypassTheTotemAfterRescue() {
        for (EntityDamageEvent.DamageCause cause : new EntityDamageEvent.DamageCause[]{
                EntityDamageEvent.DamageCause.SUICIDE, EntityDamageEvent.DamageCause.VOID}) {
            Player player = player(2169);
            player.getInventory().setItemInHand(new ItemTotem(0));
            player.getOffhandInventory().setItem(0, new ItemTotem(0));
            assertFalse(player.attack(melee(player, 30)));
            assertEquals(10, ((EntityLiving) player).attackTime);
            EntityDamageEvent forcedDeath = new EntityDamageEvent(player, cause, 1_000_000);
            assertTrue(player.attack(forcedDeath));
            assertFalse(forcedDeath.isCancelled());
            assertTrue(player.getHealth() <= 0);
            assertEquals(1, player.getOffhandInventory().getItem(0).getCount());
        }
    }

    @Test
    void preexistingAbsoluteProtectionStillBlocksDamageWithoutConsumingATotem() {
        Player player = player(786);
        player.getInventory().setItemInHand(new ItemTotem(0));
        player.noDamageTicks = 12;
        assertFalse(player.attack(melee(player, 30)));
        assertEquals(20, player.getHealth());
        assertEquals(1, player.getInventory().getItemInHand().getCount());
        assertEquals(12, player.noDamageTicks);
        assertEquals(0, ((EntityLiving) player).attackTime);
    }

    /** Invoke the real living-entity timers without unrelated Player networking and hunger ticks. */
    private static void tickEntityBase(Player player, int ticks) throws Throwable {
        MethodHandles.privateLookupIn(EntityCreature.class, MethodHandles.lookup())
                .findSpecial(EntityLiving.class, "entityBaseTick", MethodType.methodType(boolean.class, int.class),
                        EntityCreature.class)
                .invoke((EntityCreature) player, ticks);
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
            var effects = Entity.class.getDeclaredField("effects");
            effects.setAccessible(true);
            effects.set(player, new HashMap<>());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        player.protocol = protocol;
        Level level = mock(Level.class);
        entity.level = level;
        when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
        when(level.getNearbyEntities(any(), any(), anyBoolean(), anyBoolean())).thenReturn(new Entity[0]);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(player.getLevel()).thenReturn(level);
        entity.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0.6, 1.8, 0.6);
        when(player.getBoundingBox()).thenReturn(entity.boundingBox);
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
        doCallRealMethod().when(player).setLastDamageCause(any(EntityDamageEvent.class));
        when(player.getLastDamageCause()).thenCallRealMethod();
        return player;
    }
}
