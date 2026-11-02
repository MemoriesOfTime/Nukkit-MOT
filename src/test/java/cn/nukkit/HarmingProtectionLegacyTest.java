package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityAreaEffectCloud;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static cn.nukkit.event.entity.EntityDamageEvent.DamageCause.*;
import static cn.nukkit.event.entity.EntityDamageEvent.DamageModifier.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HarmingProtectionLegacyTest {
    private boolean previousVanilla;
    @BeforeAll static void bootstrap() { MockServer.init(); Effect.init(); Potion.init(); }
    @BeforeEach void legacy() {
        MockServer.init();
        previousVanilla = MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction();
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(false);
    }
    @AfterEach void restore() {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(previousVanilla);
    }

    @ParameterizedTest @CsvSource({"0,6,0", "1,12,0", "0,6,-1", "1,12,-1"})
    void arrowInstantDamageKeepsItsExistingBase(int amplifier, float raw, int protection) throws Exception {
        Fixture f = new Fixture(protection);
        arrow(amplifier, 1).onCollideWithEntity(f.player);
        EntityDamageEvent magic = onlyMagic(f.player);
        assertEquals(raw, magic.getDamage(), .0001f);
        assertEquals(raw, magic.getFinalDamage(), .0001f);
        assertEquals(0f, magic.getDamage(ARMOR), .0001f);
    }

    @ParameterizedTest @CsvSource({"4,false", "10,false", "4,true", "10,true"})
    void arrowProtectionUsesExistingLegacyRollAndVanillaFormula(int protection, boolean vanilla) throws Exception {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(vanilla);
        Fixture f = new Fixture(protection);
        arrow(1, 1).onCollideWithEntity(f.player);
        assertProtected(onlyMagic(f.player), 12f, protection, vanilla);
    }

    @ParameterizedTest @CsvSource({"0,6,0", "1,12,0", "0,6,-1", "1,12,-1"})
    void splashBaseRemainsSixOrTwelve(int amplifier, float raw, int protection) {
        Fixture f = new Fixture(protection);
        Potion.getPotion(Potion.HARMING + amplifier).setSplash(true).applyPotion(f.player, 1);
        assertEquals(raw, onlyMagic(f.player).getFinalDamage(), .0001f);
    }

    @ParameterizedTest @CsvSource({"4,false", "10,false", "4,true", "10,true"})
    void splashProtectionUsesSameCoefficients(int protection, boolean vanilla) {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(vanilla);
        Fixture f = new Fixture(protection);
        Potion.getPotion(Potion.HARMING_II).setSplash(true).applyPotion(f.player, 1);
        assertProtected(onlyMagic(f.player), 12f, protection, vanilla);
    }

    @ParameterizedTest @CsvSource({"0,false", "4,false", "10,false", "0,true", "4,true", "10,true"})
    void cloudDamageUsesTheSameProtectionPath(int protection, boolean sourced) throws Exception {
        Fixture f = new Fixture(protection);
        EntityAreaEffectCloud cloud = mock(EntityAreaEffectCloud.class, CALLS_REAL_METHODS);
        field(cloud, "level", f.level);
        field(cloud, "mobEffects", payload(1));
        field(cloud, "victims", new Long2LongOpenHashMap());
        if (sourced) field(cloud, "owner", mock(Entity.class));
        doReturn(1L).when(cloud).getSpawnTick();
        doReturn(600).when(cloud).getDuration();
        doReturn(0).when(cloud).getWaitTime();
        doReturn(3f).when(cloud).getRadius();
        doNothing().when(cloud).setRadius(anyFloat(), eq(false));
        doReturn(0f).when(cloud).getRadiusPerTick();
        doReturn(new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1)).when(cloud).getBoundingBox();
        // Exercise existing cloud dispatch, without changing its separate spatial filter.
        doReturn(16d).when(cloud).distanceSquared(f.player);
        when(f.level.getCurrentTick()).thenReturn(26L);
        when(f.level.getNearbyEntities(any(), eq(cloud))).thenReturn(new Entity[]{f.player});
        cloud.onUpdate(26);
        assertProtected(onlyMagic(f.player), 6f, protection, false);
    }

    @ParameterizedTest @CsvSource({"ENTITY_ATTACK,0", "PROJECTILE,0", "ENTITY_ATTACK,-1", "PROJECTILE,-1", "ENTITY_ATTACK,4", "PROJECTILE,4", "ENTITY_ATTACK,10", "PROJECTILE,10"})
    void plainSwordAndArrowKeepLegacyDamage(EntityDamageEvent.DamageCause cause, int protection) {
        Fixture f = new Fixture(protection);
        EntityDamageEvent event = new EntityDamageEvent(f.player, cause, 12f);
        assertTrue(f.player.attack(event));
        if (protection == 0) assertEquals(12f, event.getFinalDamage(), .0001f);
        else if (protection == -1) assertEquals(2.4f, event.getFinalDamage(), .0001f);
        else {
            assertEquals(-9.6f, event.getDamage(ARMOR), .0001f);
            assertLegacyNumbers(event.getFinalDamage(), protection, .2f);
        }
    }

    @Test
    void waterDamageKeepsItsUnmarkedMagicOrigin() {
        cn.nukkit.entity.mob.EntityEnderman target = mock(cn.nukkit.entity.mob.EntityEnderman.class);
        Potion.getPotion(Potion.WATER).applyPotion(target);
        org.mockito.ArgumentCaptor<EntityDamageEvent> damage = org.mockito.ArgumentCaptor.forClass(EntityDamageEvent.class);
        verify(target).attack(damage.capture());
        assertEquals(MAGIC, damage.getValue().getCause());
        assertEquals(1f, damage.getValue().getDamage());
        assertFalse(damage.getValue().isInstantPotionDamage());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unmarkedMagicKeepsPreviousBehavior(boolean vanilla) {
        MockServer.get().getServerConfig().gameFeatureSettings().vanillaArmorReduction(vanilla);
        Fixture f = new Fixture(4);
        EntityDamageEvent event = new EntityDamageEvent(f.player, MAGIC, 12f);
        assertTrue(f.player.attack(event));
        assertEquals(vanilla ? 4.32f : 12f, event.getFinalDamage(), .0001f);
        assertEquals(0f, event.getDamage(ARMOR), .0001f);
    }

    @ParameterizedTest @ValueSource(ints = {4, 10})
    void sourcedArrowUsesProtectionWithoutLosingItsDamager(int protection) throws Exception {
        Fixture f = new Fixture(protection);
        Entity shooter = mock(Entity.class);
        EntityArrow arrow = arrow(1, 1);
        field(arrow, "shootingEntity", shooter);
        arrow.onCollideWithEntity(f.player);
        EntityDamageEvent magic = onlyMagic(f.player);
        assertSame(shooter, ((cn.nukkit.event.entity.EntityDamageByEntityEvent) magic).getDamager());
        assertProtected(magic, 12f, protection, false);
    }

    @Test void strongerPhysicalImpactKeepsExistingMagicImmunity() throws Exception {
        Fixture f = new Fixture(0);
        arrow(1, 13).onCollideWithEntity(f.player);
        assertEquals(1, f.player.attempts.stream().filter(e -> e.getCause() == MAGIC).count());
        assertEquals(0, f.player.accepted.stream().filter(e -> e.getCause() == MAGIC).count());
        assertEquals(87f, f.player.getHealth(), .0001f);
    }

    private static void assertProtected(EntityDamageEvent event, float raw, int protection, boolean vanilla) {
        assertEquals(raw, event.getDamage(), .0001f);
        assertEquals(0f, event.getDamage(ARMOR), .0001f, "MAGIC must still bypass armor points");
        if (protection == 0) assertEquals(raw, event.getFinalDamage(), .0001f);
        else if (vanilla) assertEquals(raw * (protection == 4 ? .36f : .2f), event.getFinalDamage(), .0001f);
        else assertLegacyNumbers(event.getFinalDamage(), protection, raw / 12f);
    }

    private static void assertLegacyNumbers(float actual, int protection, float scale) {
        // Concrete outcomes of the unchanged 50..99 legacy roll. IVx4: EPF16; Xx4: cap25.
        float[] damage = protection == 4
                ? new float[]{8.16f, 7.68f, 7.2f, 6.72f, 6.24f, 5.76f, 5.28f, 4.8f, 4.32f}
                : new float[]{5.76f, 5.28f, 4.8f, 4.32f, 3.84f, 3.36f, 2.88f, 2.4f};
        for (float expected : damage) if (Math.abs(actual - expected * scale) < .0001f) return;
        fail("Unexpected legacy damage " + actual + " at Protection " + protection + ", scale=" + scale);
    }

    private static EntityDamageEvent onlyMagic(TestPlayer player) {
        List<EntityDamageEvent> magic = player.accepted.stream().filter(e -> e.getCause() == MAGIC).toList();
        assertEquals(1, magic.size());
        return magic.get(0);
    }
    private static Int2ObjectOpenHashMap<Effect> payload(int amplifier) {
        var effects = new Int2ObjectOpenHashMap<Effect>();
        effects.put(Effect.HARMING, Effect.getEffect(Effect.HARMING).setAmplifier(amplifier));
        return effects;
    }
    private static EntityArrow arrow(int amplifier, int impact) throws Exception {
        EntityArrow arrow = mock(EntityArrow.class, CALLS_REAL_METHODS);
        field(arrow, "server", MockServer.get());
        field(arrow, "level", MockServer.get().getDefaultLevel());
        field(arrow, "mobEffects", payload(amplifier));
        doReturn(impact).when(arrow).getResultDamage();
        doNothing().when(arrow).close();
        return arrow;
    }
    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final TestPlayer player;
        Fixture(int protection) {
            Server server = MockServer.get();
            try {
                field(level, "updateEntities", new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<Entity>());
                field(level, "isBeingConverted", true);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            when(server.getDefaultLevel()).thenReturn(level);
            when(server.getViewDistance()).thenReturn(1);
            when(level.getServer()).thenReturn(server);
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
            when(level.getNearbyEntities(any(), any(), anyBoolean(), anyBoolean())).thenReturn(new Entity[0]);
            when(level.getNearbyRideables(any(), any())).thenReturn(new Entity[0]);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(java.util.Collections.emptyMap());
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenReturn(chunk);
            player = new TestPlayer();
            player.initialize(chunk);
            player.setMaxHealth(100);
            player.setHealth(100);
            if (protection != 0) {
                int[] ids = {ItemID.DIAMOND_HELMET, ItemID.DIAMOND_CHESTPLATE, ItemID.DIAMOND_LEGGINGS, ItemID.DIAMOND_BOOTS};
                Item[] armor = new Item[4];
                for (int i = 0; i < 4; i++) {
                    armor[i] = Item.get(ids[i]);
                    if (protection > 0) armor[i].addEnchantment(Enchantment.getEnchantment(Enchantment.ID_PROTECTION_ALL).setLevel(protection, false));
                }
                player.getInventory().setArmorContents(armor);
            }
        }
    }
    private static final class TestPlayer extends Player {
        final List<EntityDamageEvent> attempts = new ArrayList<>();
        final List<EntityDamageEvent> accepted = new ArrayList<>();
        TestPlayer() { super(mock(SourceInterface.class), 1L, new InetSocketAddress("127.0.0.1", 19199)); }
        void initialize(BaseFullChunk chunk) { init(chunk, Entity.getDefaultNBT(new Vector3(0, 64, 0))); }
        @Override public boolean dataPacket(DataPacket packet) { return true; }
        @Override public boolean attack(EntityDamageEvent event) {
            attempts.add(event);
            boolean result = super.attack(event);
            if (result) accepted.add(event);
            return result;
        }
    }
}
