package cn.nukkit.entity.projectile;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Impact damage of the player's ranged weapons against vanilla. A thrown trident struck for its
 * speed times 8, 19 after a full throw, where the vanilla {@code minecraft:thrown_trident} deals a
 * fixed 8. Player arrows deal speed times 2 rounded up, plus the critical bonus: 6 from a fully
 * drawn bow and 7 from a crossbow, where the doubled crossbow speed dealt 21.
 */
class VanillaProjectileImpactDamageTest {

    /** Launch speed after the first tick of drag, which applies before the hit. */
    private static final double DRAG = 0.99;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServerBeforeTest() {
        MockServer.reset();
    }

    @AfterEach
    void resetServerAfterTest() {
        MockServer.reset();
    }

    @Test
    void fullyDrawnBowArrowDealsSixAndSixToTenWhenCritical() {
        assertEquals(6, arrowDamage(3.0 * DRAG, false));
        for (int i = 0; i < 500; i++) {
            int critical = arrowDamage(3.0 * DRAG, true);
            assertTrue(critical >= 6 && critical <= 10, "critical bow arrow dealt " + critical);
        }
    }

    @Test
    void crossbowArrowDealsSevenAndSevenToElevenWhenCritical() {
        assertEquals(7, arrowDamage(3.15 * DRAG, false));
        for (int i = 0; i < 500; i++) {
            int critical = arrowDamage(3.15 * DRAG, true);
            assertTrue(critical >= 7 && critical <= 11, "critical crossbow arrow dealt " + critical);
        }
    }

    @Test
    void thrownTridentDealsEightWhateverItsSpeed() {
        assertEquals(8, trident(Item.get(Item.TRIDENT), 2.5 * DRAG).getResultDamage());
        assertEquals(8, trident(Item.get(Item.TRIDENT), 0.6).getResultDamage());
    }

    @Test
    void thrownTridentHitsForEight() {
        Entity target = target(false);
        trident(Item.get(Item.TRIDENT), 2.5 * DRAG).onCollideWithEntity(target);
        assertEquals(8, damageDealtTo(target), 1e-6);
    }

    @Test
    void impalingAddsTwoAndAHalfPerLevelAgainstATargetInWater() {
        Item item = Item.get(Item.TRIDENT);
        item.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_TRIDENT_IMPALING).setLevel(5));
        Entity target = target(true);
        trident(item, 2.5 * DRAG).onCollideWithEntity(target);
        assertEquals(8 + 2.5 * 5, damageDealtTo(target), 1e-6);
    }

    private static int arrowDamage(double speed, boolean critical) {
        EntityArrow arrow = mock(EntityArrow.class, CALLS_REAL_METHODS);
        doReturn(2.0).when(arrow).getDamage();
        doReturn(critical).when(arrow).isCritical();
        arrow.motionX = speed;
        arrow.motionY = 0;
        arrow.motionZ = 0;
        return arrow.getResultDamage();
    }

    private static EntityThrownTrident trident(Item item, double speed) {
        EntityThrownTrident trident = new EntityThrownTrident(chunk(level()), nbt());
        trident.setItem(item);
        trident.motionX = speed;
        trident.motionY = 0;
        trident.motionZ = 0;
        return trident;
    }

    private static Entity target(boolean inWater) {
        Level level = level();
        Entity target = mock(Entity.class);
        lenient().when(target.isInsideOfWater()).thenReturn(inWater);
        lenient().when(target.getLevel()).thenReturn(level);
        return target;
    }

    private static float damageDealtTo(Entity target) {
        ArgumentCaptor<EntityDamageEvent> hit = ArgumentCaptor.forClass(EntityDamageEvent.class);
        verify(target).attack(hit.capture());
        return hit.getValue().getDamage();
    }

    private static Level level() {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(MockServer.get());
        lenient().when(level.getGameRules()).thenReturn(GameRules.getDefault());
        lenient().when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        try {
            Field converting = Level.class.getDeclaredField("isBeingConverted");
            converting.setAccessible(true);
            converting.setBoolean(level, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return level;
    }

    private static FullChunk chunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static CompoundTag nbt() {
        return new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
    }
}
