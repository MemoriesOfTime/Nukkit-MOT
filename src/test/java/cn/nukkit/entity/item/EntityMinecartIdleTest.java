package cn.nukkit.entity.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.CollisionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityMinecartIdleTest {
    @Test
    void flatIdleTicksMatchOriginalPhysicsAndWireBytes() throws Exception {
        for (int meta : new int[]{0, 1}) {
            for (double along : new double[]{-16.75, -0.0, 0.125, 15.875, 16.25}) {
                World actual = new World(meta, false, along);
                World original = new World(meta, true, along);
                for (int tick = 0; tick < 40; tick++) {
                    assertEquals(original.cart.entityBaseTick(1), actual.cart.entityBaseTick(1));
                    assertState(original.cart, actual.cart);
                }
                assertWire(original.cart, actual.cart);
                verify(actual.collisions, times(80)).getCollisionBlocks();
            }
        }
    }

    @Test
    void idleTicksHalveBlockMaterialisationsAndCutRailIdReadsByTwoThirds() throws Exception {
        World actual = new World(0, false, 0.5);
        World original = new World(0, true, 0.5);
        for (int tick = 0; tick < 100; tick++) {
            actual.cart.entityBaseTick(1);
            original.cart.entityBaseTick(1);
        }
        assertEquals(400, original.blockReads);
        assertEquals(300, original.idReads);
        assertEquals(200, actual.blockReads, "idle carts need only the rail and support blocks each tick");
        assertEquals(100, actual.idReads, "the two redundant getNextRail scans must be skipped");
        assertState(original.cart, actual.cart);
    }

    @Test
    void idleSupportAvoidsDerivedPositionWorkAndMatchesOriginal() throws Exception {
        World actualWorld = new World(0, false, 0.5);
        World originalWorld = new World(0, true, 0.5);
        EntityMinecartEmpty actual = spy(actualWorld.cart);
        EntityMinecartEmpty original = spy(originalWorld.cart);
        for (int tick = 0; tick < 100; tick++) {
            assertEquals(original.entityBaseTick(1), actual.entityBaseTick(1));
            assertState(original, actual);
        }
        verify(original, times(100)).getPosition();
        verify(original, times(100)).down();
        verify(actual, never()).getPosition();
        verify(actual, never()).down();
        assertEquals(200, actualWorld.blockReads);
        assertEquals(100, actualWorld.idReads);
        verify(actualWorld.collisions, times(200)).getCollisionBlocks();
        assertWire(original, actual);
    }

    @Test
    void replacingAnIdleFlatRailWithASlopeStartsMotionOnTheNextTick() throws Exception {
        World actual = new World(0, false, 0.5);
        World original = new World(0, true, 0.5);
        tickAndCompare(actual, original);
        assertEquals(2, actual.blockReads, "the cart must first take the idle path");

        actual.railMeta = original.railMeta = 2;
        tickAndCompare(actual, original);
        assertTrue(actual.cart.motionX != 0 || actual.cart.motionZ != 0);
    }

    @ParameterizedTest(name = "idle flat {0} to rail shape {1}")
    @CsvSource({
            "0, 3", "0, 4", "0, 5", "0, 6", "0, 7", "0, 8", "0, 9",
            "1, 2", "1, 3", "1, 4", "1, 5", "1, 6", "1, 7", "1, 8", "1, 9"
    })
    void remainingSlopeAndCurveChangesKeepOriginalPhysics(int flatMeta, int changedMeta) throws Exception {
        World actual = new World(flatMeta, false, 0.5);
        World original = new World(flatMeta, true, 0.5);
        tickAndCompare(actual, original);
        assertEquals(2, actual.blockReads, "the cart must first take the idle path");

        actual.railMeta = original.railMeta = changedMeta;
        int actualReads = actual.idReads;
        int originalReads = original.idReads;
        for (int tick = 0; tick < 4; tick++) {
            tickAndCompare(actual, original);
        }
        assertEquals(original.idReads - originalReads, actual.idReads - actualReads,
                "slopes and curves must retain the original rail scans");
    }

    @Test
    void poweringAReplacementRailStartsAnIdleCartOnTheNextTick() throws Exception {
        World actual = new World(0, false, 0.5);
        World original = new World(0, true, 0.5);
        tickAndCompare(actual, original);
        assertEquals(2, actual.blockReads, "the cart must first take the idle path");

        for (World world : new World[]{actual, original}) {
            world.railId = Block.POWERED_RAIL;
            world.endStop = true;
        }
        tickAndCompare(actual, original);
        assertEquals(0, actual.cart.motionX);
        assertEquals(0, actual.cart.motionZ);

        actual.railMeta = original.railMeta = 8;
        tickAndCompare(actual, original);
        assertEquals(0.02D, actual.cart.motionX, "power must start the cart without waiting for a cadence");
    }

    @Test
    void movingAnIdleCartToAnotherChunkKeepsRailChecksLive() throws Exception {
        World actual = new World(0, false, 0.5);
        World original = new World(0, true, 0.5);
        tickAndCompare(actual, original);
        assertEquals(2, actual.blockReads, "the cart must first take the idle path");

        for (World world : new World[]{actual, original}) {
            BaseFullChunk next = mock(BaseFullChunk.class);
            LevelProvider provider = world.chunk.getProvider();
            when(next.getX()).thenReturn(1);
            when(next.getProvider()).thenReturn(provider);
            when(world.level.getChunk(1, 0, true)).thenReturn(next);
            when(world.level.getChunkIfLoaded(1, 0)).thenReturn(next);
            world.cart.setPosition(new Vector3(16.5, 64.5, 0.5));
            assertSame(next, world.cart.chunk);
            verify(world.chunk).removeEntity(world.cart);
            verify(next).addEntity(world.cart);
        }
        tickAndCompare(actual, original);
        actual.railMeta = original.railMeta = 2;
        tickAndCompare(actual, original);
        assertTrue(actual.cart.motionX != 0 || actual.cart.motionZ != 0);
    }

    @Test
    void mountingALivingPassengerLetsAnIdleCartMoveOnTheNextTick() throws Exception {
        World actual = new World(0, false, 0.5);
        World original = new World(0, true, 0.5);
        tickAndCompare(actual, original);
        assertEquals(2, actual.blockReads, "the cart must first take the idle path");

        for (World world : new World[]{actual, original}) {
            EntityPig passenger = new EntityPig(world.chunk,
                    Entity.getDefaultNBT(new Vector3(0.5, 64.5, 0.5)));
            assertTrue(world.cart.mountEntity(passenger));
            assertSame(world.cart, passenger.riding);
            world.cart.setCurrentSpeed(1);
        }
        tickAndCompare(actual, original);
        assertEquals(1, actual.cart.passengers.size());
        assertTrue(actual.cart.z > 0.5 && actual.cart.motionZ > 0,
                "a mounted living passenger must resume normal movement immediately");
        assertEquals(original.cart.getPassenger().getSeatPosition(), actual.cart.getPassenger().getSeatPosition());
    }

    @Test
    void pushesRailChangesRemovalAndCollisionEffectsStayImmediate() throws Exception {
        World actual = new World(0, false, 0.5);
        World original = new World(0, true, 0.5);
        for (int step = 0; step < 12; step++) {
            for (World world : new World[]{actual, original}) {
                if (step == 1) {
                    Entity pusher = mock(Entity.class);
                    pusher.x = world.cart.x;
                    pusher.z = world.cart.z + 0.3;
                    world.cart.applyEntityCollision(pusher);
                }
                if (step == 2) {
                    world.cart.setMotion(new Vector3(0, 0, 0));
                    assertTrue(world.cart.attack(new EntityDamageEvent(world.cart,
                            EntityDamageEvent.DamageCause.CONTACT, 0.1f)));
                }
                if (step == 3) world.railMeta = 1;
                if (step == 4) world.railMeta = 2;
                if (step == 5) { world.railId = Block.POWERED_RAIL; world.railMeta = 8; }
                if (step == 6) { world.railId = Block.ACTIVATOR_RAIL; world.railMeta = 8; }
                if (step == 7) { world.railId = Block.RAIL; world.railMeta = 0; }
                if (step == 8) world.touching = new Block[]{Block.get(Block.NETHER_PORTAL)};
                if (step == 9) world.support = false;
                if (step == 10) world.railId = Block.AIR;
                if (step == 11) world.cart.setPosition(new Vector3(17.5, 64.5, 0.5));
            }
            assertEquals(original.cart.entityBaseTick(1), actual.cart.entityBaseTick(1));
            assertState(original.cart, actual.cart);
            assertWire(original.cart, actual.cart);
        }
        assertTrue(actual.cart.y < 64.5, "a removed rail must take the falling path immediately");
    }

    @Test
    void passengersVerticalMotionMisalignmentAndSpeedModifiersKeepOriginalPath() throws Exception {
        for (int state = 0; state < 7; state++) {
            World actual = new World(0, false, 0.5);
            World original = new World(0, true, 0.5);
            for (World world : new World[]{actual, original}) {
                if (state == 0) world.cart.passengers.add(mock(Entity.class));
                if (state == 1) world.cart.motionY = 0.1;
                if (state == 2) world.cart.x = 0.25;
                if (state >= 3) world.cart.setMaximumSpeed(new double[]{0, -0.0, -0.4, Double.NaN}[state - 3]);
            }
            assertEquals(original.cart.entityBaseTick(1), actual.cart.entityBaseTick(1));
            assertState(original.cart, actual.cart);
        }
    }

    private static void assertState(EntityMinecartEmpty expected, EntityMinecartEmpty actual) {
        double[] before = {expected.x, expected.y, expected.z, expected.motionX, expected.motionY,
                expected.motionZ, expected.yaw, expected.pitch, expected.headYaw, expected.lastX,
                expected.lastY, expected.lastZ, expected.fallDistance};
        double[] after = {actual.x, actual.y, actual.z, actual.motionX, actual.motionY,
                actual.motionZ, actual.yaw, actual.pitch, actual.headYaw, actual.lastX,
                actual.lastY, actual.lastZ, actual.fallDistance};
        for (int i = 0; i < before.length; i++) {
            assertEquals(Double.doubleToLongBits(before[i]), Double.doubleToLongBits(after[i]), "state field " + i);
        }
        assertArrayEquals(new double[]{expected.boundingBox.getMinX(), expected.boundingBox.getMinY(),
                        expected.boundingBox.getMinZ(), expected.boundingBox.getMaxX(),
                        expected.boundingBox.getMaxY(), expected.boundingBox.getMaxZ()},
                new double[]{actual.boundingBox.getMinX(), actual.boundingBox.getMinY(),
                        actual.boundingBox.getMinZ(), actual.boundingBox.getMaxX(),
                        actual.boundingBox.getMaxY(), actual.boundingBox.getMaxZ()});
        assertEquals(expected.onGround, actual.onGround);
        assertEquals(expected.age, actual.age);
        assertEquals(expected.ticksLived, actual.ticksLived);
        assertEquals(expected.inPortalTicks, actual.inPortalTicks);
        assertEquals(expected.getHealth(), actual.getHealth());
        assertEquals(expected.getRollingAmplitude(), actual.getRollingAmplitude());
        assertEquals(expected.getDataProperties().getMap(), actual.getDataProperties().getMap());
    }

    private static void tickAndCompare(World actual, World original) throws Exception {
        assertEquals(original.cart.entityBaseTick(1), actual.cart.entityBaseTick(1));
        assertState(original.cart, actual.cart);
        assertWire(original.cart, actual.cart);
    }

    private static void assertWire(EntityMinecartEmpty expected, EntityMinecartEmpty actual) throws Exception {
        Method create = Entity.class.getDeclaredMethod("createAddEntityPacket");
        create.setAccessible(true);
        for (GameVersion version : GameVersion.getValues()) {
            if (version.isNetEase() || version.getProtocol() < ProtocolInfo.v1_20_0
                    || version.getProtocol() > ProtocolInfo.v1_26_50) continue;
            AddEntityPacket before = (AddEntityPacket) create.invoke(expected);
            AddEntityPacket after = (AddEntityPacket) create.invoke(actual);
            before.entityUniqueId = after.entityUniqueId = 1;
            before.entityRuntimeId = after.entityRuntimeId = 1;
            for (AddEntityPacket packet : new AddEntityPacket[]{before, after}) {
                for (int i = 0; i < packet.links.length; i++) {
                    packet.links[i].fromEntityUniquieId = 1;
                    packet.links[i].toEntityUniquieId = i + 2;
                }
            }
            before.protocol = after.protocol = version.getProtocol();
            before.gameVersion = after.gameVersion = version;
            before.encode();
            after.encode();
            assertArrayEquals(before.getBuffer(), after.getBuffer(), version.toString());
        }
    }

    private static final class World {
        final Level level = mock(Level.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final CollisionHelper collisions = mock(CollisionHelper.class);
        final EntityMinecartEmpty cart;
        int railId = Block.RAIL;
        int railMeta;
        boolean support = true;
        boolean endStop;
        Block[] touching = Block.EMPTY_ARRAY;
        int blockReads;
        int idReads;

        World(int meta, boolean original, double along) throws Exception {
            MockServer.init();
            railMeta = meta;
            double x = meta == 0 ? 0.5 : along;
            double z = meta == 1 ? 0.5 : along;
            when(level.getServer()).thenReturn(MockServer.get());
            when(level.getGameRules()).thenReturn(GameRules.getDefault());
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenReturn(chunk);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> {
                Vector3 at = call.getArgument(0);
                return block(at.getFloorX(), at.getFloorY(), at.getFloorZ());
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                    block(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            when(level.getBlock(any(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenAnswer(call -> block(call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                idReads++;
                return id(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            });
            when(collisions.getCollisionBlocks()).thenAnswer(call -> touching);
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(chunk.getProvider()).thenReturn(provider);
            when(chunk.getX()).thenReturn((int) Math.floor(x) >> 4);
            when(chunk.getZ()).thenReturn((int) Math.floor(z) >> 4);
            level.isBeingConverted = true;
            CompoundTag nbt = Entity.getDefaultNBT(new Vector3(x, 64.5, z));
            cart = original ? new OriginalCart(chunk, nbt) : new EntityMinecartEmpty(chunk, nbt);
            Field helper = Entity.class.getDeclaredField("collisionHelper");
            helper.setAccessible(true);
            helper.set(cart, collisions);
            cart.motionX = cart.motionY = cart.motionZ = 0;
        }

        private int id(int x, int y, int z) {
            if (endStop && x == -1 && y == 64 && z == 0) return Block.STONE;
            return y == 64 ? railId : y == 63 && support ? Block.STONE : Block.AIR;
        }

        private Block block(int x, int y, int z) {
            blockReads++;
            int id = id(x, y, z);
            return Block.get(id, y == 64 && id == railId ? railMeta : 0, level, x, y, z);
        }
    }

    /** A subclass deliberately retains the original path, including both rail projection scans. */
    private static final class OriginalCart extends EntityMinecartEmpty {
        OriginalCart(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }
    }
}
