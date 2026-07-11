package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityBlazeFireBall;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EntityMinecartTNTTest {

    private static Server serverMock;

    @BeforeAll
    static void init() {
        MockServer.init();
        serverMock = MockServer.get();
    }

    @BeforeEach
    void resetServerBeforeTest() {
        MockServer.reset();
        serverMock = MockServer.get();
    }

    @AfterEach
    void resetServerAfterTest() {
        MockServer.reset();
    }

    @Test
    void lethalFlamingProjectileExplodesInsteadOfDroppingItem() {
        GameRules gameRules = GameRules.getDefault();
        Level level = newMockLevel(gameRules);
        FullChunk chunk = newMockChunk(level);
        TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
        EntityArrow arrow = newArrow(chunk, true);

        minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                EntityDamageEvent.DamageCause.PROJECTILE, 5));

        assertTrue(minecart.exploded, "Flaming projectile should detonate the TNT minecart");
        assertTrue(minecart.aliveWhenExploded,
                "Flaming projectile should detonate before normal damage kills the minecart");
        assertFalse(minecart.closedWhenExploded, "Flaming projectile should detonate before the minecart is closed");
        verify(level, never()).dropItem(any(Vector3.class), any(Item.class));
    }

    @Test
    void flamingFireChargeProjectileDoesNotUseInstantArrowDetonation() {
        Level level = newMockLevel(GameRules.getDefault());
        FullChunk chunk = newMockChunk(level);
        TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
        EntityBlazeFireBall fireBall = newBlazeFireBall(chunk, true);

        minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), fireBall, minecart,
                EntityDamageEvent.DamageCause.PROJECTILE, 0));

        assertFalse(minecart.exploded,
                "Fire charge projectiles should not use the flaming-arrow instant detonation path");
    }

    @Test
    void flamingProjectileDoesNotExplodeWhenTntExplodesIsDisabled() {
        GameRules gameRules = GameRules.getDefault();
        gameRules.setGameRule(GameRule.TNT_EXPLODES, false);
        Level level = newMockLevel(gameRules);
        FullChunk chunk = newMockChunk(level);
        TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
        EntityArrow arrow = newArrow(chunk, true);

        minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                EntityDamageEvent.DamageCause.PROJECTILE, 5));

        assertFalse(minecart.exploded, "tntExplodes=false should suppress flaming-projectile detonation");
    }

    @Test
    void cancelledFlamingProjectileDamageDoesNotExplode() {
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityDamageEvent damageEvent) {
                damageEvent.setCancelled();
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        when(serverMock.getPluginManager()).thenReturn(pluginManager);

        Level level = newMockLevel(GameRules.getDefault());
        FullChunk chunk = newMockChunk(level);
        TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
        EntityArrow arrow = newArrow(chunk, true);

        boolean damaged = minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                EntityDamageEvent.DamageCause.PROJECTILE, 5));

        assertFalse(damaged, "Cancelled flaming-projectile damage should not be applied");
        assertFalse(minecart.exploded,
                "Cancelled flaming-projectile damage should not detonate the TNT minecart");
    }

    @Test
    void flamingProjectileKeepsOriginalDamageVisibleToDamageEvent() {
        PluginManager pluginManager = mock(PluginManager.class);
        AtomicReference<Float> finalDamage = new AtomicReference<>();
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityDamageEvent damageEvent) {
                finalDamage.set(damageEvent.getFinalDamage());
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        when(serverMock.getPluginManager()).thenReturn(pluginManager);

        Level level = newMockLevel(GameRules.getDefault());
        FullChunk chunk = newMockChunk(level);
        TestableTNTMinecart minecart = new TestableTNTMinecart(chunk, baseNbt());
        EntityArrow arrow = newArrow(chunk, true);

        minecart.attack(new EntityDamageByChildEntityEvent(mock(Entity.class), arrow, minecart,
                EntityDamageEvent.DamageCause.PROJECTILE, 5));

        assertEquals(75, finalDamage.get(),
                "Flaming-projectile damage event should keep minecart's normal x15 damage scale");
    }

    private static Level newMockLevel(GameRules gameRules) {
        Level level = mock(Level.class);
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getServer()).thenReturn(serverMock);
        lenient().when(level.getGameRules()).thenReturn(gameRules);
        lenient().when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        lenient().when(serverMock.getTick()).thenReturn(0);
        lenient().doNothing().when(level).addEntity(any());
        try {
            Field f = Level.class.getDeclaredField("isBeingConverted");
            f.setAccessible(true);
            f.setBoolean(level, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return level;
    }

    private static FullChunk newMockChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().doNothing().when(chunk).addEntity(any());
        lenient().when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
    }

    private static EntityArrow newArrow(FullChunk chunk, boolean onFire) {
        CompoundTag nbt = baseNbt()
                .putShort("Fire", onFire ? 45 * 60 : 0)
                .putDouble("damage", 5);
        EntityArrow arrow = new EntityArrow(chunk, nbt);
        arrow.motionX = 1.2;
        arrow.motionY = 0;
        arrow.motionZ = 0;
        return arrow;
    }

    private static EntityBlazeFireBall newBlazeFireBall(FullChunk chunk, boolean onFire) {
        CompoundTag nbt = baseNbt().putShort("Fire", onFire ? 2 : 0);
        EntityBlazeFireBall fireBall = new EntityBlazeFireBall(chunk, nbt);
        fireBall.motionX = 1.2;
        fireBall.motionY = 0;
        fireBall.motionZ = 0;
        return fireBall;
    }

    private static final class TestableTNTMinecart extends EntityMinecartTNT {
        private boolean exploded;
        private boolean aliveWhenExploded;
        private boolean closedWhenExploded;

        private TestableTNTMinecart(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }

        @Override
        public void explode(double square) {
            this.aliveWhenExploded = isAlive();
            this.closedWhenExploded = isClosed();
            this.exploded = true;
        }
    }
}
