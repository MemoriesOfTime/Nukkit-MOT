package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.mob.EntityEnderman;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.passive.EntityAllay;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalTargetSearchRangeTest {
    @BeforeAll
    static void init() { MockServer.init(); }

    @Test
    void vanillaFollowRangesRemainDistinctFromAllayLocalSearch() {
        EntityAllay allay = mock(EntityAllay.class);
        allay.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 0.6, 64.6, 0.6);
        assertEquals(1024, EntityRanges.getFollowRange(allay), "known owner navigation range remains intact");
        assertEquals(32, EntityRanges.getTargetSearchRange(allay), "vanilla pickup_items max_dist");
        AxisAlignedBB box = EntityRanges.createTargetSearchBox(allay);
        assertEquals(-32, box.getMinX());
        assertEquals(32.6, box.getMaxX());
        assertEquals(32, EntityRanges.getTargetSearchRange(mock(EntityZombie.class)));
        assertEquals(64, EntityRanges.getTargetSearchRange(mock(EntityEnderman.class)));
        assertEquals(10, EntityRanges.getTargetSearchRange(mock(EntityCow.class)));
        assertEquals(128, EntityRanges.getTargetSearchRange(mock(EntityVillager.class)));
    }

    @Test
    void cachedCandidatesOutsideRangeNeverReachTargetOption() throws Exception {
        Fixture f = new Fixture(0, 0);
        EntityCreature edge = creature(16, 0);
        EntityCreature outside = creature(16.01, 0);
        EntityCreature diagonal = creature(12, 12);
        when(f.level.getNearbyEntities(any(), same(f.walker), eq(false), eq(true)))
                .thenReturn(new Entity[]{edge, outside, diagonal});
        when(f.walker.targetOption(edge, 256)).thenReturn(true);
        f.scan();
        assertSame(edge, f.walker.followTarget);
        verify(f.walker).targetOption(edge, 256);
        verify(f.walker, never()).targetOption(same(outside), anyDouble());
        verify(f.walker, never()).targetOption(same(diagonal), anyDouble());
    }

    @Test
    void existingZombieAggroAcceptanceIsStillTenBlocks() {
        EntityZombie zombie = mock(EntityZombie.class, CALLS_REAL_METHODS);
        Player player = mock(Player.class);
        player.spawned = true;
        when(player.isAlive()).thenReturn(true);
        when(player.isSurvival()).thenReturn(true);
        assertTrue(zombie.targetOption(player, 100));
        assertFalse(zombie.targetOption(player, 100.01));
        assertEquals(32, EntityRanges.getTargetSearchRange(zombie), "broad phase does not narrow existing aggro");
    }

    @Test
    void existingEndermanAggroAcceptanceIsStill32Blocks() {
        EntityEnderman enderman = mock(EntityEnderman.class, CALLS_REAL_METHODS);
        doReturn(true).when(enderman).isAngry();
        Player player = mock(Player.class);
        player.spawned = true;
        when(player.isAlive()).thenReturn(true);
        when(player.isAdventure()).thenReturn(true);
        assertTrue(enderman.targetOption(player, 1024));
        assertFalse(enderman.targetOption(player, 1024.01));
        assertEquals(64, EntityRanges.getTargetSearchRange(enderman));
    }

    @Test
    void permittedCandidateOrderAndStateChangesStayTheSame() throws Exception {
        Fixture f = new Fixture(0, 0);
        EntityCreature first = creature(3, 0);
        EntityCreature last = creature(4, 0);
        when(f.level.getNearbyEntities(any(), same(f.walker), eq(false), eq(true)))
                .thenReturn(new Entity[]{first, last});
        when(f.walker.targetOption(any(), anyDouble())).thenReturn(true);
        f.walker.stayTime = 50;
        f.walker.moveTime = 80;
        f.scan();
        assertSame(last, f.walker.followTarget, "keep MOT's existing last-eligible-candidate order");
        assertSame(last, f.walker.target);
        assertEquals(0, f.walker.stayTime);
        assertEquals(0, f.walker.moveTime);
    }

    @Test
    void existingWalkingTargetBeyondLocalRangeRemainsRetained() throws Exception {
        Fixture f = new Fixture(0, 0);
        EntityCreature existing = creature(64, 0);
        when(existing.isAlive()).thenReturn(true);
        when(existing.canBeFollowed()).thenReturn(true);
        when(f.walker.targetOption(existing, 4096)).thenReturn(true);
        f.walker.followTarget = existing;
        f.walker.target = existing;
        f.scan();
        assertSame(existing, f.walker.followTarget);
        verify(f.level, never()).getNearbyEntities(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void knownAllayFollowTargetBeyond32RemainsRetained() {
        EntityAllay allay = mock(EntityAllay.class, CALLS_REAL_METHODS);
        EntityCreature owner = creature(128, 0);
        when(owner.isAlive()).thenReturn(true);
        allay.followTarget = owner;
        ((EntityFlying) allay).checkTarget();
        assertSame(owner, allay.followTarget);
        verify(allay, never()).getLevel();
    }

    @Test
    void targetChecksKeepTheirExistingCadenceAndCacheQueryFlags() throws Exception {
        Fixture f = new Fixture(0, 0);
        when(f.level.getNearbyEntities(any(), same(f.walker), eq(false), eq(true))).thenReturn(new Entity[0]);
        f.walker.stayTime = 17;
        f.walker.moveTime = 31;
        f.scan();
        f.scan();
        f.scan();
        verify(f.level, times(3)).getNearbyEntities(any(), same(f.walker), eq(false), eq(true));
        assertEquals(17, f.walker.stayTime);
        assertEquals(31, f.walker.moveTime);
    }

    @Test
    void knockbackAndPassengerShortCircuitsDoNotSearch() throws Exception {
        Fixture f = new Fixture(0, 0);
        when(f.walker.isKnockback()).thenReturn(true);
        f.scan();
        when(f.walker.isKnockback()).thenReturn(false);
        f.walker.passengers.add(mock(Entity.class));
        f.scan();
        verify(f.level, never()).getNearbyEntities(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void actualSpatialQueryVisitsOnlyLoadedNeighbourChunksIncludingNegativeCoordinates() throws Exception {
        Fixture f = new Fixture(-0.5, -0.5);
        Level level = spatialLevel();
        when(f.walker.getLevel()).thenReturn(level);
        EntityCreature nearby = creature(1, 1);
        Map<Long, Entity> neighbours = new LinkedHashMap<>();
        neighbours.put(1L, nearby);
        doReturn(neighbours).when(level).getChunkEntities(0, 0, false);
        when(f.walker.targetOption(nearby, 4.5)).thenReturn(true);
        f.scan();
        assertSame(nearby, f.walker.followTarget);
        verify(level, times(16)).getChunkEntities(anyInt(), anyInt(), eq(false));
        verify(level, never()).getChunkEntities(anyInt(), anyInt(), eq(true));
        verify(level, never()).getEntities();
        verify(level, never()).getChunkEntities(eq(100), anyInt(), anyBoolean());
    }

    @Test
    void allayLocalQueryVisits36ChunksRatherThan16900() throws Exception {
        EntityAllay allay = mock(EntityAllay.class);
        allay.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 0.6, 64.6, 0.6);
        Level level = spatialLevel();
        when(allay.getLevel()).thenReturn(level);
        assertEquals(0, level.getNearbyEntities(EntityRanges.createTargetSearchBox(allay), allay, false, true).length);
        verify(level, times(36)).getChunkEntities(anyInt(), anyInt(), eq(false));
        verify(level, never()).getEntities();
        verify(level, never()).getChunkEntities(anyInt(), anyInt(), eq(true));
    }

    private static Level spatialLevel() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        field(level, Level.class, "nearbyEntitiesCache", Caffeine.newBuilder().build());
        field(level, Level.class, "entityNearbyCacheDirty", Caffeine.newBuilder().build());
        doReturn(Map.of()).when(level).getChunkEntities(anyInt(), anyInt(), anyBoolean());
        return level;
    }

    private static EntityCreature creature(double x, double z) {
        EntityCreature entity = mock(EntityCreature.class);
        entity.x = x;
        entity.y = 64;
        entity.z = z;
        entity.boundingBox = new SimpleAxisAlignedBB(x - 0.3, 64, z - 0.3, x + 0.3, 65, z + 0.3);
        return entity;
    }

    private static void field(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final EntityWalking walker = mock(EntityWalking.class);

        Fixture(double x, double z) throws Exception {
            walker.x = x;
            walker.y = 64;
            walker.z = z;
            walker.boundingBox = new SimpleAxisAlignedBB(x - 0.3, 64, z - 0.3, x + 0.3, 65, z + 0.3);
            field(walker, Entity.class, "passengers", new ArrayList<Entity>());
            when(walker.getLevel()).thenReturn(level);
            when(walker.canTarget(any())).thenReturn(true);
            when(walker.distanceSquared(any())).thenCallRealMethod();
            doCallRealMethod().when(walker).checkTarget();
        }

        void scan() { walker.checkTarget(); }
    }
}
