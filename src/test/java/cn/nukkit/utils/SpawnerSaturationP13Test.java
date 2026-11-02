package cn.nukkit.utils;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityEnderman;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.entity.passive.EntityCod;
import cn.nukkit.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnerSaturationP13Test {
    @Test
    void saturatedCowCapStopsBeforeTheUnrelatedWorldTail() {
        Fixture f = new Fixture(Level.DIMENSION_OVERWORLD);
        Entity tail = mob(EntityWitch.NETWORK_ID, 0, 64, 0, true);
        Entity[] entities = new Entity[1002];
        Arrays.fill(entities, tail);
        entities[0] = mob(EntityCow.NETWORK_ID, 1, 64, 0, true);
        entities[1] = mob(EntityCow.NETWORK_ID, -1, 64, 0, true);
        when(f.level.getEntities()).thenReturn(entities);
        assertFalse(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
        verify(tail, never()).isAlive();
    }

    @Test
    void equalityRejectsAllExistingSpeciesCaps() {
        int[][] cases = {
                {Level.DIMENSION_OVERWORLD, EntityWitch.NETWORK_ID, 1},
                {Level.DIMENSION_OVERWORLD, EntityCow.NETWORK_ID, 2},
                {Level.DIMENSION_OVERWORLD, EntityCod.NETWORK_ID, 4},
                {Level.DIMENSION_THE_END, EntityEnderman.NETWORK_ID, 10}
        };
        for (int[] test : cases) {
            Fixture f = new Fixture(test[0]);
            Entity[] entities = new Entity[test[2]];
            for (int i = 0; i < entities.length; i++) {
                entities[i] = mob(test[1], i, 64, 0, true);
            }
            when(f.level.getEntities()).thenReturn(entities);
            assertFalse(SpawnerTask.entitySpawnAllowed(f.level, test[1], f.player));
            when(f.level.getEntities()).thenReturn(Arrays.copyOf(entities, entities.length - 1));
            assertTrue(SpawnerTask.entitySpawnAllowed(f.level, test[1], f.player));
        }
    }

    @Test
    void radiusRemainsStrictHorizontal128AndFiltersRemainUnchanged() {
        Fixture f = new Fixture(Level.DIMENSION_OVERWORLD);
        f.player.x = -128;
        f.player.z = -128;
        Entity[] outsideAndFiltered = {
                mob(EntityCow.NETWORK_ID, -127, 10000, -128, true),
                mob(EntityCow.NETWORK_ID, 0, 64, -128, true),
                mob(EntityCow.NETWORK_ID, -128, 64, -256, true),
                mob(EntityCow.NETWORK_ID, -128, 64, -128, false),
                mob(EntityWitch.NETWORK_ID, -128, 64, -128, true)
        };
        when(f.level.getEntities()).thenReturn(outsideAndFiltered);
        assertTrue(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
        Entity[] inside = {
                mob(EntityCow.NETWORK_ID, -127, 10000, -128, true),
                mob(EntityCow.NETWORK_ID, -0.01, -10000, -128, true)
        };
        when(f.level.getEntities()).thenReturn(inside);
        assertFalse(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
    }

    @Test
    void disabledDimensionDoesNotReadWorldEntities() {
        Fixture f = new Fixture(Level.DIMENSION_NETHER);
        assertFalse(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
        verify(f.level, never()).getEntities();
    }

    private static Entity mob(int id, double x, double y, double z, boolean alive) {
        Entity entity = mock(Entity.class);
        entity.x = x;
        entity.y = y;
        entity.z = z;
        when(entity.isAlive()).thenReturn(alive);
        when(entity.getNetworkId()).thenReturn(id);
        return entity;
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        Fixture(int dimension) {
            when(level.getDimension()).thenReturn(dimension);
        }
    }
}
