package cn.nukkit.entity.mob;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the real creeper update and ignition state, isolating movement and the final explosion. */
@Isolated("shared MockServer state")
class CreeperTargetLossTest {
    private Creeper creeper;
    private int tick;

    @BeforeEach
    void createCreeper() throws Exception {
        SparseMobConstructionWorld world = new SparseMobConstructionWorld();
        creeper = new Creeper(world.chunk, Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5)));
        tick = creeper.lastUpdate;
    }

    @AfterEach
    void closeCreeper() {
        try {
            if (creeper != null) {
                creeper.close();
            }
        } finally {
            MockServer.reset();
        }
    }

    @Test
    void missingTargetCancelsFuseAndReacquisitionStartsFromZero() {
        assertLossRestartsFuse(null);
    }

    @Test
    void nearbyWanderingWaypointDoesNotKeepACombatFuse() {
        assertLossRestartsFuse(new Vector3(1.5, 64, 0.5));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void manuallyLitFuseContinuesFor31UpdatesAfterTargetLoss(boolean wandering) {
        creeper.target = creatureAtDistanceSquared(4);
        advance(20);
        assertTrue(isIgnited());
        assertTrue(creeper.onInteract(null, Item.get(Item.FLINT_AND_STEEL), new Vector3()));
        creeper.target = wandering ? new Vector3(1.5, 64, 0.5) : null;

        for (int i = 0; i < 30; i++) {
            assertTrue(step());
            assertTrue(isIgnited(), "Target loss must not clear a manual ignition");
            assertEquals(0, creeper.explosions);
        }
        assertFalse(step(), "Manual ignition still expires on update 31");
        assertEquals(1, creeper.explosions);
        assertTrue(creeper.closed);
        assertFalse(step());
        assertEquals(1, creeper.explosions, "A closed creeper must not explode twice");
    }

    @Test
    void existingFourBlockBoundaryAndThirtyTickFuseStayUnchanged() {
        EntityCreature target = creatureAtDistanceSquared(16);
        creeper.target = target;
        advance(20);
        assertTrue(isIgnited(), "Exactly four blocks starts the existing fuse");
        when(target.distanceSquared(any(Vector3.class))).thenReturn(16.0001);
        assertTrue(step());
        assertFalse(isIgnited(), "Moving beyond four blocks keeps the existing reset");
        when(target.distanceSquared(any(Vector3.class))).thenReturn(16.0);
        assertFreshFuse();
    }

    @Test
    void immobileGateKeepsItsExistingBehavior() {
        creeper.target = creatureAtDistanceSquared(4);
        advance(20);
        creeper.setImmobile(true);
        creeper.target = null;
        assertTrue(step());
        assertTrue(isIgnited(), "Disabled movement keeps the preexisting early return");
        creeper.setImmobile(false);
        creeper.target = creatureAtDistanceSquared(4);
        advance(9);
        assertEquals(0, creeper.explosions);
        assertFalse(step());
        assertEquals(1, creeper.explosions);
    }

    private void assertLossRestartsFuse(Vector3 replacementTarget) {
        creeper.target = creatureAtDistanceSquared(4);
        advance(20);
        assertTrue(isIgnited());
        assertEquals(0, creeper.explosions);

        creeper.target = replacementTarget;
        assertTrue(step());
        assertFalse(isIgnited(), "Losing the creature must clear ordinary ignition");
        creeper.target = creatureAtDistanceSquared(4);
        assertFreshFuse();
    }

    private void assertFreshFuse() {
        advance(29);
        assertTrue(isIgnited());
        assertEquals(0, creeper.explosions);
        assertFalse(step(), "A newly acquired target receives a full 30-tick fuse");
        assertEquals(1, creeper.explosions);
    }

    private static EntityCreature creatureAtDistanceSquared(double distance) {
        EntityCreature target = mock(EntityCreature.class);
        when(target.distanceSquared(any(Vector3.class))).thenReturn(distance);
        return target;
    }

    private boolean isIgnited() {
        return creeper.getDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_IGNITED);
    }

    private boolean step() {
        return creeper.onUpdate(++tick);
    }

    private void advance(int count) {
        for (int i = 0; i < count; i++) {
            assertTrue(step());
        }
    }

    private static final class Creeper extends EntityCreeper {
        private Vector3 target;
        private int explosions;

        private Creeper(FullChunk chunk, CompoundTag tag) {
            super(chunk, tag);
        }

        @Override
        public Vector3 updateMove(int tickDiff) {
            return target;
        }

        @Override
        public boolean entityBaseTick(int tickDiff) {
            return false;
        }

        @Override
        public void explode() {
            explosions++;
            close();
        }
    }
}
