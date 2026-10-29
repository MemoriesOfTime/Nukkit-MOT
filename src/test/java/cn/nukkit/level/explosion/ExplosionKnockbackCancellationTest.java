package cn.nukkit.level.explosion;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.StrongExplosion;
import cn.nukkit.level.WeakExplosion;
import cn.nukkit.level.util.ExplosionSource;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real blast methods and EntityLiving.attack; only world access and event delivery are controlled. */
class ExplosionKnockbackCancellationTest {
    enum Route { ORDINARY, WEAK, STRONG }
    private static final double EPSILON = 1e-10;

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    private static final class WeakBlockBlast extends WeakExplosion {
        WeakBlockBlast(Position center, Block source) {
            super(center, 1, new ExplosionSource.BlockSource(source));
        }
    }

    private static final class StrongBlockBlast extends StrongExplosion {
        StrongBlockBlast(Position center, Block source) {
            super(center, 1, new ExplosionSource.BlockSource(source));
        }
    }

    private static void field(Entity entity, String name, Object value) throws Exception {
        Field field = Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final EntityLiving target = mock(EntityLiving.class);
        final AtomicReference<EntityDamageEvent> received = new AtomicReference<>();
        final PluginManager plugins = mock(PluginManager.class);
        boolean accepted;
        float health = 100;

        Fixture(boolean cancelled, boolean immunity, boolean clearCancellation) throws Exception {
            Server server = mock(Server.class);
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getServer()).thenReturn(server);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getNearbyEntities(any(AxisAlignedBB.class), nullable(Entity.class)))
                    .thenReturn(new Entity[]{target});
            when(level.getBlockDensity(any(Vector3.class), any())).thenReturn(0f);
            target.level = level;
            target.motionX = .25;
            target.motionY = .125;
            target.motionZ = -.5;
            target.noDamageTicks = immunity ? 10 : 0;
            field(target, "server", server);
            field(target, "health", 100f);
            field(target, "hasSpawned", new HashMap<>());
            when(target.distance(any(Vector3.class))).thenReturn(.5);
            when(target.subtract(any(Vector3.class))).thenReturn(new Location(1, 0, 0, level));
            when(target.getHealth()).thenAnswer(call -> health);
            doAnswer(call -> {
                health = call.getArgument(0);
                field(target, "health", health);
                return null;
            }).when(target).setHealth(anyFloat());
            doAnswer(call -> {
                Vector3 motion = call.getArgument(0);
                target.motionX = motion.x;
                target.motionY = motion.y;
                target.motionZ = motion.z;
                return true;
            }).when(target).setMotion(any(Vector3.class));
            doAnswer(call -> {
                Event event = call.getArgument(0);
                if (event instanceof EntityDamageEvent damage) {
                    damage.setCancelled(cancelled);
                    if (clearCancellation) damage.setCancelled(false);
                }
                return null;
            }).when(plugins).callEvent(any(Event.class));
            doAnswer(call -> {
                received.set(call.getArgument(0));
                accepted = (boolean) call.callRealMethod();
                return accepted;
            }).when(target).attack(any(EntityDamageEvent.class));
        }

        void blast(Route route) {
            Position center = new Position(0, 64, 0, level);
            Block source = Block.get(Block.AIR);
            Explosion blast = switch (route) {
                case ORDINARY -> new Explosion(center, 1, source);
                case WEAK -> new WeakBlockBlast(center, source);
                case STRONG -> new StrongBlockBlast(center, source);
            };
            assertTrue(blast.explodeB());
            assertNotNull(received.get());
            verify(target, times(1)).attack(any(EntityDamageEvent.class));
        }

        void untouched() {
            assertEquals(.25, target.motionX, EPSILON);
            assertEquals(.125, target.motionY, EPSILON);
            assertEquals(-.5, target.motionZ, EPSILON);
            verify(target, never()).setMotion(any(Vector3.class));
        }

        void pushed(Route route) {
            if (route == Route.ORDINARY) {
                assertEquals(1, target.motionX, EPSILON);
                assertEquals(.125, target.motionY, EPSILON);
                assertEquals(-.5, target.motionZ, EPSILON);
                verify(target, never()).setMotion(any(Vector3.class));
            } else {
                assertEquals(.75, target.motionX, EPSILON);
                assertEquals(0, target.motionY, EPSILON);
                assertEquals(0, target.motionZ, EPSILON);
                verify(target).setMotion(any(Vector3.class));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void finalCancelledDamagePreservesOriginalMotion(Route route) throws Exception {
        Fixture fixture = new Fixture(true, false, false);
        fixture.blast(route);
        assertTrue(fixture.received.get().isCancelled());
        assertFalse(fixture.accepted);
        assertEquals(100, fixture.health);
        fixture.untouched();
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void acceptedDamagePreservesOriginalBlastPhysics(Route route) throws Exception {
        Fixture fixture = new Fixture(false, false, false);
        fixture.blast(route);
        assertTrue(fixture.accepted);
        assertTrue(fixture.health < 100);
        fixture.pushed(route);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void nativeImmunityWithoutCancellationStillPushes(Route route) throws Exception {
        Fixture fixture = new Fixture(false, true, false);
        fixture.blast(route);
        assertFalse(fixture.received.get().isCancelled());
        assertFalse(fixture.accepted);
        assertEquals(100, fixture.health);
        fixture.pushed(route);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void clearedCancellationUsesFinalEventState(Route route) throws Exception {
        Fixture fixture = new Fixture(true, false, true);
        fixture.blast(route);
        assertFalse(fixture.received.get().isCancelled());
        assertTrue(fixture.accepted);
        fixture.pushed(route);
    }
}
