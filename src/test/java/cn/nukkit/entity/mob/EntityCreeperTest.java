package cn.nukkit.entity.mob;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityCreeperTest {

    /** Exercises the real creeper update and ignition state, isolating movement and the final explosion. */
    @Nested
    @Isolated("shared MockServer state")
    class TargetLoss {
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

    @Nested
    class ManualFuseActivation {
        static final class Creeper extends EntityCreeper {
            int blasts, updates;

            Creeper(FullChunk chunk, CompoundTag tag) {
                super(chunk, tag);
            }

            @Override
            protected boolean isInTickingRange(double range) {
                return false;
            }

            @Override
            public boolean entityBaseTick(int delta) {
                updates++;
                return false;
            }

            @Override
            public Vector3 updateMove(int delta) {
                return null;
            }

            @Override
            public void explode() {
                blasts++;
                close();
            }
        }

        @Test
        void acceptedManualIgnitionWakesSleepingFuseForEveryUpdate() throws Exception {
            for (boolean charged : new boolean[]{false, true}) {
                SparseMobConstructionWorld w = new SparseMobConstructionWorld();
                double previous = w.server.entityActivationRangeSquared;
                w.server.entityActivationRangeSquared = 64;
                Creeper actor = new Creeper(w.chunk, Entity.getDefaultNBT(new Vector3(.5, 64, .5)).putBoolean("powered", charged));
                try {
                    int phase = 1020 - Math.floorMod((int) actor.getId(), 20);
                    assertFalse(actor.isActivationThrottled(phase));
                    assertTrue(actor.isActivationAsleep());
                    assertTrue(actor.onInteract(mock(Player.class), Item.get(Item.FLINT_AND_STEEL), new Vector3()));
                    for (int i = 1; i <= 31; i++) {
                        int tick = phase + i;
                        if (!actor.isActivationThrottled(tick)) actor.onUpdate(tick);
                    }
                    assertEquals(1, actor.blasts, "Manual fuse must detonate after 31 updates despite empty activation range");
                    assertTrue(actor.closed);
                    assertFalse(actor.onUpdate(phase + 32));
                    assertEquals(1, actor.blasts);
                } finally {
                    actor.close();
                    w.server.entityActivationRangeSquared = previous;
                }
            }
        }

        @Test
        void unlitCreeperRetainsSleepingCadence() throws Exception {
            SparseMobConstructionWorld w = new SparseMobConstructionWorld();
            double previous = w.server.entityActivationRangeSquared;
            w.server.entityActivationRangeSquared = 64;
            Creeper actor = new Creeper(w.chunk, Entity.getDefaultNBT(new Vector3(.5, 64, .5)));
            try {
                int phase = 1020 - Math.floorMod((int) actor.getId(), 20);
                actor.isActivationThrottled(phase);
                for (int i = 1; i <= 31; i++) if (!actor.isActivationThrottled(phase + i)) actor.onUpdate(phase + i);
                assertEquals(0, actor.blasts);
                assertTrue(actor.updates < 5, "Unlit native mob keeps activation optimization");
            } finally {
                actor.close();
                w.server.entityActivationRangeSquared = previous;
            }
        }
    }
}
