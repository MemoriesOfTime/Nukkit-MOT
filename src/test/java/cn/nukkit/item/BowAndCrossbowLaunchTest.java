package cn.nukkit.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression: a fully drawn bow launched its arrow at 3.36 blocks per tick and never made it
 * critical (the check wanted a force of exactly 2 out of 2.8), and a crossbow multiplied the aim
 * by 3.15 and then by a force of 3.5, launching arrows at 11 blocks per tick for 21 damage.
 * Vanilla launches player arrows at 3.0 from a bow, critical at full draw, and at 3.15 from a
 * crossbow, always critical.
 */
class BowAndCrossbowLaunchTest {

    /** Aim jitter is 0.0075 per axis, a few hundredths of the speed at most. */
    private static final double SPEED_TOLERANCE = 0.15;

    private final List<EntityShootBowEvent> shots = new ArrayList<>();

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void captureShots() {
        MockServer.reset();
        Server server = MockServer.get();
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof EntityShootBowEvent shot) {
                shots.add(shot);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        when(server.getPluginManager()).thenReturn(pluginManager);
        // A crossbow shoots only once the charge is more than 10 ticks old.
        when(server.getTick()).thenReturn(1000);
    }

    @AfterEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void fullDrawLaunchesACriticalArrowAtVanillaSpeed() {
        assertTrue(new ItemBow().onRelease(shooter(), 20));

        EntityShootBowEvent shot = onlyShot();
        EntityArrow arrow = (EntityArrow) shot.getProjectile();
        assertEquals(3.0, shot.getForce(), 1e-9);
        assertEquals(3.0, arrow.getMotion().length(), SPEED_TOLERANCE);
        assertTrue(arrow.isCritical());
    }

    @Test
    void holdingLongerStaysAtFullDraw() {
        new ItemBow().onRelease(shooter(), 200);

        EntityArrow arrow = (EntityArrow) onlyShot().getProjectile();
        assertEquals(3.0, arrow.getMotion().length(), SPEED_TOLERANCE);
        assertTrue(arrow.isCritical());
    }

    @Test
    void partialDrawIsSlowerAndNotCritical() {
        new ItemBow().onRelease(shooter(), 10);

        EntityShootBowEvent shot = onlyShot();
        EntityArrow arrow = (EntityArrow) shot.getProjectile();
        // p = 0.5: (0.25 + 1) / 3 of the full speed.
        assertEquals(1.25, shot.getForce(), 1e-9);
        assertEquals(1.25, arrow.getMotion().length(), SPEED_TOLERANCE / 2);
        assertFalse(arrow.isCritical());
    }

    @Test
    void drawIsFullAfterOneSecond() {
        assertTrue(ItemBow.drawStrength(19) < 1);
        assertEquals(1, ItemBow.drawStrength(20), 1e-9);
        assertEquals(1, ItemBow.drawStrength(72000), 1e-9);
    }

    @Test
    void crossbowLaunchesACriticalArrowAtVanillaSpeed() {
        ItemCrossbow crossbow = new ItemCrossbow();
        crossbow.createNamedTag(Item.get(Item.ARROW), 1);

        assertTrue(crossbow.launchArrow(shooter()));

        EntityShootBowEvent shot = onlyShot();
        EntityArrow arrow = (EntityArrow) shot.getProjectile();
        assertEquals(3.15, shot.getForce(), 1e-9);
        assertEquals(3.15, arrow.getMotion().length(), SPEED_TOLERANCE);
        assertTrue(arrow.isCritical());
    }

    @Test
    void multishotLaunchesThreeCriticalArrowsAtVanillaSpeed() {
        ItemCrossbow crossbow = new ItemCrossbow();
        crossbow.createNamedTag(Item.get(Item.ARROW), 3);

        assertTrue(crossbow.launchArrow(shooter()));

        assertEquals(3, shots.size());
        for (EntityShootBowEvent shot : shots) {
            EntityArrow arrow = (EntityArrow) shot.getProjectile();
            assertEquals(3.15, arrow.getMotion().length(), SPEED_TOLERANCE);
            assertTrue(arrow.isCritical());
        }
    }

    private EntityShootBowEvent onlyShot() {
        assertEquals(1, shots.size());
        return shots.get(0);
    }

    private static Player shooter() {
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
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);

        Player player = mock(Player.class);
        player.chunk = chunk;
        player.yaw = 90;
        player.pitch = 0;
        lenient().when(player.getEyePosition()).thenReturn(new Vector3(0.5, 65.62, 0.5));
        lenient().when(player.getLevel()).thenReturn(level);
        // Creative: the bow and the crossbow take a free arrow and leave the inventory alone.
        lenient().when(player.isCreative()).thenReturn(true);
        lenient().when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        lenient().when(player.getOffhandInventory()).thenReturn(mock(PlayerOffhandInventory.class));
        return player;
    }
}
