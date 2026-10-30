package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.AnimatePacket;
import java.util.ArrayList;
import java.util.List;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTotem;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.potion.Effect;
import java.lang.reflect.Field;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs the native Player -> HumanType -> Living -> Entity settlement, without a live server. */
class PvpDamageCooldownTest {
    private boolean previousArmorPolicy;
    @BeforeEach
    void initialize() {
        MockServer.init();
        Effect.init();
        previousArmorPolicy = MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction();
        reset(MockServer.get().getPluginManager());
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(false);
    }

    @AfterEach void restoreSharedTestState() {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(previousArmorPolicy);
        reset(MockServer.get().getPluginManager());
    }

    @Test
    void ordinaryAndEqualHitsKeepTheExistingBehavior() {
        Player victim = player(786);
        Player attacker = player(786);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        assertEquals(16f, victim.getHealth());
        assertEquals(10, ((EntityLiving) victim).attackTime);
        assertFalse(victim.attack(hit(attacker, victim, 4)));
        assertEquals(16f, victim.getHealth());
    }

    @ParameterizedTest
    @ValueSource(ints = {589, 649, 729, 766, 786, 2193})
    void strongerHitsApplyOnlySuccessiveDifferencesAndKeepTheRawBase(int protocol) {
        Player victim = player(protocol);
        Player attacker = player(protocol);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        EntityDamageEvent second = hit(attacker, victim, 6);
        assertTrue(victim.attack(second));
        assertEquals(14f, victim.getHealth(), 0.00001f);
        assertEquals(6f, second.getDamage(), "the raw base remains the immunity ceiling");
        assertFalse(second.isApplicable(EntityDamageEvent.DamageModifier.STRENGTH));
        assertFalse(second.isApplicable(EntityDamageEvent.DamageModifier.WEAKNESS));
        assertFalse(second.isApplicable(EntityDamageEvent.DamageModifier.RESISTANCE));
        assertTrue(victim.attack(hit(attacker, victim, 8)));
        assertEquals(12f, victim.getHealth(), 0.00001f);
        assertFalse(victim.attack(hit(attacker, victim, 8)));
        assertFalse(victim.attack(hit(attacker, victim, 7)));
        verify(victim, times(1)).knockBack(eq(attacker), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void supplementalDamageDoesNotRenewTheTimerOrEmitAnotherSelfHurtAnimation() throws Exception {
        Player victim = player(2193);
        Player attacker = player(2193);
        setField(victim, "spawned", true);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        ((EntityLiving) victim).attackTime = 3;
        EntityDamageEvent supplement = hit(attacker, victim, 6);
        supplement.setAttackCooldown(30);
        assertTrue(victim.attack(supplement));
        assertEquals(3, ((EntityLiving) victim).attackTime);
        verify(victim, times(1)).dataPacket(argThat(packet -> packet instanceof EntityEventPacket event
                && event.event == EntityEventPacket.HURT_ANIMATION));
        ((EntityLiving) victim).attackTime = 0;
        assertTrue(victim.attack(hit(attacker, victim, 6)));
        assertEquals(8f, victim.getHealth(), 0.00001f);
        assertEquals(10, ((EntityLiving) victim).attackTime);
        verify(victim, times(2)).knockBack(eq(attacker), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void differenceIsReducedByArmorBeforeHealthSettlement(boolean vanillaArmor) {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(vanillaArmor);
        Player victim = player(786);
        Player attacker = player(786);
        victim.getInventory().setArmorItem(0, Item.get(Item.DIAMOND_HELMET));
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        float before = victim.getHealth();
        EntityDamageEvent second = hit(attacker, victim, 6);
        assertTrue(victim.attack(second));
        // Legacy: 3 armor points * 4%; modern: 3 - 2 / (2 + 2 / 4) = 2.2, hence 8.8%.
        assertEquals(vanillaArmor ? 1.824f : 1.76f, before - victim.getHealth(), 0.00001f);
        assertEquals(6f, second.getDamage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resistanceIsAppliedOnceToTheDifference(boolean vanillaArmor) {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(vanillaArmor);
        Player victim = player(786);
        Player attacker = player(786);
        when(victim.hasEffect(Effect.DAMAGE_RESISTANCE)).thenReturn(true);
        when(victim.getEffect(Effect.DAMAGE_RESISTANCE))
                .thenReturn(Effect.getEffect(Effect.DAMAGE_RESISTANCE).setAmplifier(0));
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        assertTrue(victim.attack(hit(attacker, victim, 6)));
        assertEquals(15.2f, victim.getHealth(), 0.00001f);
    }

    @Test
    void absorptionConsumesTheDifferenceWithoutDoubleSpendingGoldenHearts() {
        Player victim = player(786);
        Player attacker = player(786);
        victim.setAbsorption(6);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        assertEquals(2f, victim.getAbsorption());
        assertTrue(victim.attack(hit(attacker, victim, 6)));
        assertEquals(0f, victim.getAbsorption());
        assertEquals(20f, victim.getHealth());
        assertTrue(victim.attack(hit(attacker, victim, 8)));
        assertEquals(18f, victim.getHealth());
    }

    @Test
    void cancelledSupplementDoesNotRaiseTheCeilingOrConsumeAbsorption() {
        Player victim = player(786);
        Player attacker = player(786);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        victim.setAbsorption(2);
        EntityDamageEvent rejected = hit(attacker, victim, 8);
        var plugins = MockServer.get().getPluginManager();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == rejected) rejected.setCancelled(true);
            return null;
        }).when(plugins).callEvent(any());
        assertFalse(victim.attack(rejected));
        assertEquals(16f, victim.getHealth());
        assertEquals(2f, victim.getAbsorption());
        assertEquals(4f, victim.getLastDamageCause().getDamage());
        assertTrue(victim.attack(hit(attacker, victim, 6)));
        assertEquals(16f, victim.getHealth());
        assertEquals(0f, victim.getAbsorption());
    }

    @Test
    void absoluteProtectionRefusesEvenStrongerHitsAndEventReplayCannotBypassTheWindow() {
        Player victim = player(786);
        Player attacker = player(786);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        victim.noDamageTicks = 2;
        assertFalse(victim.attack(hit(attacker, victim, 8)));
        assertEquals(16f, victim.getHealth());
        victim.noDamageTicks = 0;
        EntityDamageEvent second = hit(attacker, victim, 6);
        assertTrue(victim.attack(second));
        assertFalse(victim.attack(second));
        assertEquals(14f, victim.getHealth());
    }

    @Test
    void pluginPercentageShapingKeepsTheRawCeilingAndScalesTheDifference() {
        Player victim = player(786);
        Player attacker = player(786);
        var plugins = MockServer.get().getPluginManager();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityDamageEvent event) {
                event.setDamage(-event.getFinalDamage() * 0.5f,
                        EntityDamageEvent.DamageModifier.ARMOR_ENCHANTMENTS);
            }
            return null;
        }).when(plugins).callEvent(any());
        assertTrue(victim.attack(hit(attacker, victim, 8)));
        assertTrue(victim.attack(hit(attacker, victim, 10)));
        assertEquals(15f, victim.getHealth());
        assertEquals(10f, victim.getLastDamageCause().getDamage());
        assertFalse(victim.attack(hit(attacker, victim, 10)));
    }

    @Test
    void supplementalTotemRescuePreservesTheRemainingWindowAndOtherHand() {
        Player victim = player(786);
        Player attacker = player(786);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        ((EntityLiving) victim).attackTime = 3;
        victim.setHealth(1);
        victim.getInventory().setItemInHand(new ItemTotem(0));
        victim.getOffhandInventory().setItem(0, new ItemTotem(0));
        assertFalse(victim.attack(hit(attacker, victim, 6)));
        assertEquals(1f, victim.getHealth());
        assertEquals(3, ((EntityLiving) victim).attackTime);
        assertTrue(victim.getInventory().getItemInHand().isNull());
        assertEquals(1, victim.getOffhandInventory().getItem(0).getCount());
        assertFalse(victim.attack(hit(attacker, victim, 6)));
        assertEquals(1, victim.getOffhandInventory().getItem(0).getCount());
    }

    @ParameterizedTest
    @CsvSource({
            "false, strength, 0, 7.8", "true, strength, 0, 7.8",
            "false, strength, 1, 9.6", "true, strength, 1, 9.6",
            "false, weakness, 0, 4.8", "true, weakness, 0, 4.8",
            "false, weakness, 1, 3.6", "true, weakness, 1, 3.6"
    })
    void attackerEffectsAreAppliedToTheDifferenceOnce(boolean vanillaArmor, String effectName,
                                                       int amplifier, float totalDamage) {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(vanillaArmor);
        Player victim = player(786);
        Player attacker = player(786);
        int effectId = effectName.equals("strength") ? Effect.STRENGTH : Effect.WEAKNESS;
        when(attacker.hasEffect(effectId)).thenReturn(true);
        when(attacker.getEffect(effectId)).thenReturn(Effect.getEffect(effectId).setAmplifier(amplifier));
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        assertTrue(victim.attack(hit(attacker, victim, 6)));
        assertEquals(20f - totalDamage, victim.getHealth(), 0.00001f);
    }

    @Test
    void weaknessCannotCreateAbsorptionFromANegativeSupplementBudget() {
        Player victim = player(786);
        Player attacker = player(786);
        when(attacker.hasEffect(Effect.WEAKNESS)).thenReturn(true);
        when(attacker.getEffect(Effect.WEAKNESS)).thenReturn(Effect.getEffect(Effect.WEAKNESS).setAmplifier(1));
        victim.setAbsorption(4);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        assertTrue(victim.attack(hit(attacker, victim, 6)));
        assertEquals(0.4f, victim.getAbsorption(), 0.00001f);
        assertEquals(20f, victim.getHealth());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void preCancelledBlockedProbeStillCalculatesArmor(boolean absoluteProtection) {
        Player victim = player(786); Player attacker = player(786);
        assertTrue(victim.attack(hit(attacker, victim, 4)));
        victim.getInventory().setArmorItem(0, Item.get(Item.DIAMOND_HELMET));
        if (absoluteProtection) victim.noDamageTicks = 2;
        EntityDamageEvent probe = hit(attacker, victim, 4); probe.setCancelled(true);
        assertFalse(victim.attack(probe));
        assertEquals(3.52f, probe.getFinalDamage(), 0.00001f);
        assertEquals(16, victim.getHealth());
        assertEquals(4, victim.getLastDamageCause().getDamage());
    }

    @Test
    void preCancelledProbeCalculatesArmorWithoutSettlingDamage() {
        Player victim = player(786);
        Player attacker = player(786);
        victim.getInventory().setArmorItem(0, Item.get(Item.DIAMOND_HELMET));
        EntityDamageEvent probe = hit(attacker, victim, 4);
        probe.setCancelled(true);
        assertFalse(victim.attack(probe));
        assertEquals(3.52f, probe.getFinalDamage(), 0.00001f);
        assertEquals(20f, victim.getHealth());
        assertEquals(0, ((EntityLiving) victim).attackTime);
        assertNull(victim.getLastDamageCause());
        verify(victim, never()).knockBack(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test void nonhumanLivingAlsoSettlesOnlyTheStrongerDifference() {
        EntityLiving victim = living(); Player attacker = player(786);
        assertTrue(victim.attack(new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4)));
        victim.attackTime = 3;
        EntityDamageEvent next = new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6);
        assertTrue(victim.attack(next));
        assertEquals(14, victim.getHealth()); assertEquals(6, next.getDamage());
        assertEquals(3, victim.attackTime);
        verify(victim, times(1)).knockBack(eq(attacker), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test void supplementalViewerHurtIsSuppressedButLethalDeathStillBroadcasts() {
        EntityLiving victim = living(); Player attacker = player(786);
        List<Integer> events = new ArrayList<>();
        try (var server = mockStatic(Server.class, CALLS_REAL_METHODS)) {
            server.when(() -> Server.broadcastPacket(anyCollection(), any(DataPacket.class)))
                    .thenAnswer(call -> {
                        if (call.getArgument(1) instanceof EntityEventPacket packet) events.add(packet.event);
                        return null;
                    });
            for (int base : new int[]{4, 6, 22}) assertTrue(victim.attack(new EntityDamageByEntityEvent(
                    attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, base)));
        }
        assertEquals(List.of((int) EntityEventPacket.HURT_ANIMATION,
                (int) EntityEventPacket.DEATH_ANIMATION), events);
    }

    @Test void supplementalCriticalDoesNotReplayTheCriticalAnimationOrSound() {
        Player victim = player(786); Player attacker = player(786);
        for (int base : new int[]{4, 6}) {
            EntityDamageEvent event = hit(attacker, victim, base);
            // The old public modifier API permits zero-valued critical markers.
            event.setDamage(0, EntityDamageEvent.DamageModifier.CRITICAL);
            assertTrue(victim.attack(event));
        }
        verify(victim.getLevel(), times(1)).addChunkPacket(anyInt(), anyInt(), any(AnimatePacket.class));
    }

    private static EntityLiving living() {
        EntityLiving victim = mock(EntityLiving.class); victim.server = MockServer.get();
        victim.health = 20; victim.level = mock(Level.class);
        setField(victim, "hasSpawned", new HashMap<>()); setField(victim, "effects", new HashMap<>());
        when(victim.getHealth()).thenAnswer(call -> victim.health);
        doAnswer(call -> { victim.health = call.getArgument(0); return null; }).when(victim).setHealth(anyFloat());
        when(victim.getLevel()).thenReturn(victim.level);
        when(victim.level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        doCallRealMethod().when(victim).attack(any(EntityDamageEvent.class));
        doCallRealMethod().when(victim).setLastDamageCause(any());
        when(victim.getLastDamageCause()).thenCallRealMethod();
        return victim;
    }

    private static EntityDamageEvent hit(Player attacker, Player target, float damage) {
        return new EntityDamageByEntityEvent(attacker, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage);
    }

    private static Player player(int protocol) {
        Player player = mock(Player.class);
        Entity entity = player;
        entity.server = MockServer.get();
        entity.health = 20;
        setField(player, "hasSpawned", new HashMap<>());
        setField(player, "effects", new HashMap<>());
        setField(player, "foodData", mock(cn.nukkit.PlayerFood.class));
        player.protocol = protocol;
        Level level = mock(Level.class);
        entity.level = level;
        when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(player.getLevel()).thenReturn(level);
        entity.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0.6, 1.8, 0.6);
        when(player.getBoundingBox()).thenReturn(entity.boundingBox);
        when(player.isAlive()).thenAnswer(invocation -> entity.health > 0);
        when(player.getHealth()).thenAnswer(invocation -> entity.health);
        doAnswer(invocation -> { entity.health = invocation.getArgument(0); return null; })
                .when(player).setHealth(anyFloat());
        when(player.getAbsorption()).thenAnswer(invocation -> entity.absorption);
        doAnswer(invocation -> { entity.absorption = invocation.getArgument(0); return null; })
                .when(player).setAbsorption(anyFloat());
        PlayerInventory inventory = new PlayerInventory(player);
        ((EntityHumanType) player).inventory = inventory;
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(player));
        doCallRealMethod().when(player).damageArmor(any(Item.class), nullable(Entity.class),
                anyFloat(), anyBoolean(), any(EntityDamageEvent.DamageCause.class));
        doCallRealMethod().when(player).recalculateResistanceDamage(any());
        doCallRealMethod().when(player).attack(any(EntityDamageEvent.class));
        doCallRealMethod().when(player).setLastDamageCause(any());
        when(player.getLastDamageCause()).thenCallRealMethod();
        return player;
    }

    private static void setField(Object target, String name, Object value) {
        for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // The owning native class may sit above Mockito's generated class.
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
        }
        throw new AssertionError(name);
    }
}
