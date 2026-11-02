package cn.nukkit.entity.projectile;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real arrow construction, move, loaded-block collision scan and ProjectileHitEvent.
 * Only terrain storage, chunk membership bookkeeping and unrelated fall damage are fixtures. */
class SlenderProjectileBlockIdentityTest {
    private record Cell(int x, int y, int z) {}
    private record Contact(String name, Vector3 start, Vector3 movement, Cell actual, Cell unrelated) {}
    private record Run(Contact contact, int protocol) {
        @Override public String toString() { return contact.name + " / protocol " + protocol; }
    }

    private Level level;
    private BaseFullChunk chunk;
    private Player shooter;
    private final Map<Cell, Integer> cells = new HashMap<>();
    private final Map<Long, Entity> entities = new HashMap<>();
    private final List<ProjectileHitEvent> hits = new ArrayList<>();
    private boolean cancelHit;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.reset();
        Server server = MockServer.get();
        level = mock(Level.class);
        Field updates = Level.class.getDeclaredField("updateEntities");
        updates.setAccessible(true);
        updates.set(level, new Long2ObjectNonBlockingMap<>());
        when(level.getServer()).thenReturn(server);
        when(level.getFolderName()).thenReturn("projectile-test");
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        level.gameRules = GameRules.getDefault();
        when(level.getGameRules()).thenReturn(level.gameRules);
        chunk = mock(BaseFullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenReturn(chunk);
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(i ->
                (int) i.getArgument(0) == 0 && (int) i.getArgument(1) == 0 ? chunk : null);
        when(level.getChunkEntities(anyInt(), anyInt(), eq(false))).thenAnswer(i ->
                (int) i.getArgument(0) == 0 && (int) i.getArgument(1) == 0 ? entities : Map.of());
        when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenAnswer(i ->
                cells.getOrDefault(new Cell(i.getArgument(0), i.getArgument(1), i.getArgument(2)), BlockID.AIR));
        when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                block(new Cell(i.getArgument(0), i.getArgument(1), i.getArgument(2))));
        when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                block(new Cell(i.getArgument(0), i.getArgument(1), i.getArgument(2))));
        when(level.getBlock(any(FullChunk.class), anyInt(), anyInt(), anyInt(), eq(0), eq(false)))
                .thenAnswer(i -> block(new Cell(i.getArgument(1), i.getArgument(2), i.getArgument(3))));
        shooter = mock(Player.class);
        shooter.protocol = 649;
        when(shooter.getUniqueId()).thenReturn(UUID.fromString("43e1c855-30a3-49c0-82e5-0123686fa709"));
        PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        doAnswer(i -> {
            if (i.getArgument(0) instanceof ProjectileHitEvent hit) {
                hits.add(hit);
                if (cancelHit) hit.setCancelled();
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
    }

    private Block block(Cell cell) {
        Block result = Block.get(cells.getOrDefault(cell, BlockID.AIR));
        result.x = cell.x;
        result.y = cell.y;
        result.z = cell.z;
        result.level = level;
        return result;
    }

    private static List<Contact> contacts() {
        return List.of(
                new Contact("floor", new Vector3(.5, 64.45, .5), new Vector3(0, -.9, 0),
                        new Cell(0, 63, 0), new Cell(0, 64, 1)),
                new Contact("roof", new Vector3(.5, 64.45, .5), new Vector3(0, .9, 0),
                        new Cell(0, 65, 0), new Cell(0, 64, 1)),
                new Contact("east wall", new Vector3(.5, 64.45, .5), new Vector3(.9, 0, 0),
                        new Cell(1, 64, 0), new Cell(0, 63, 0)),
                new Contact("west wall", new Vector3(2.5, 64.45, .5), new Vector3(-.9, 0, 0),
                        new Cell(1, 64, 0), new Cell(2, 63, 0)),
                new Contact("south wall", new Vector3(.5, 64.45, .5), new Vector3(0, 0, .9),
                        new Cell(0, 64, 1), new Cell(0, 63, 0)),
                new Contact("north wall", new Vector3(.5, 64.45, 2.5), new Vector3(0, 0, -.9),
                        new Cell(0, 64, 1), new Cell(0, 63, 2)),
                new Contact("corner grazing floor", new Vector3(.99, 64.45, .99), new Vector3(0, -.9, 0),
                        new Cell(1, 63, 1), new Cell(0, 65, 0)),
                new Contact("diagonal wall", new Vector3(.5, 64.45, .5), new Vector3(.9, 0, .9),
                        new Cell(1, 64, 1), new Cell(0, 63, 0)));
    }

    private static Stream<Run> allContacts() {
        return contacts().stream().flatMap(c -> Stream.of(589, 649, 827, 2193).map(p -> new Run(c, p)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allContacts")
    void eventKeepsActualAabbContactDespiteNonIntersectingNeighbor(Run run) {
        Contact contact = run.contact;
        shooter.protocol = run.protocol;
        cells.put(contact.actual, BlockID.STONE);
        cells.put(contact.unrelated, BlockID.WOOL);
        NativeArrow arrow = arrow(contact.start);
        // Both endpoints and the whole movement envelope miss the unrelated block.
        assertFalse(block(contact.unrelated).collidesWithBB(
                arrow.boundingBox.clone().addCoord(contact.movement.x, contact.movement.y, contact.movement.z)));
        if (contact.name.equals("corner grazing floor")) {
            assertNull(block(contact.actual).getBoundingBox().calculateIntercept(
                    contact.start, contact.start.add(contact.movement)), "center-ray-only fixes miss this AABB contact");
        }
        move(arrow, contact.movement);
        assertBlockHit(contact.actual);
        assertTrue(arrow.hadCollision);
        assertEquals(1, arrow.hitCallbacks);
        assertEquals(1, arrow.groundCallbacks);
    }

    @Test
    void floorWithoutNeighborStillReportsStone() {
        cells.put(new Cell(0, 63, 0), BlockID.STONE);
        move(arrow(new Vector3(.5, 64.45, .5)), new Vector3(0, -.9, 0));
        assertBlockHit(new Cell(0, 63, 0));
    }

    @Test
    void cancelledKnownContactKeepsIdentityAndSuppressesBothCallbacks() {
        cells.put(new Cell(0, 63, 0), BlockID.STONE);
        cells.put(new Cell(0, 64, 1), BlockID.WOOL);
        cancelHit = true;
        NativeArrow arrow = arrow(new Vector3(.5, 64.45, .5));
        move(arrow, new Vector3(0, -.9, 0));
        assertBlockHit(new Cell(0, 63, 0));
        assertTrue(hits.get(0).isCancelled());
        assertEquals(0, arrow.hitCallbacks);
        assertEquals(0, arrow.groundCallbacks);
        assertTrue(arrow.hadCollision);
    }

    @Test
    void repeatedStoppedMovementDoesNotDispatchSecondHit() {
        cells.put(new Cell(0, 63, 0), BlockID.STONE);
        NativeArrow arrow = arrow(new Vector3(.5, 64.45, .5));
        move(arrow, new Vector3(0, -.9, 0));
        move(arrow, new Vector3(0, -.9, 0));
        assertBlockHit(new Cell(0, 63, 0));
        assertEquals(1, arrow.hitCallbacks);
        assertEquals(1, arrow.groundCallbacks);
    }

    @Test
    void unknownCollisionRetainsLegacyNeighborFallback() {
        // Explicit synthetic collision flag, with no block in the real scan, covers the old fallback.
        cells.put(new Cell(0, 64, 1), BlockID.WOOL);
        NativeArrow arrow = arrow(new Vector3(.5, 64.45, .5));
        arrow.forceUnknownCollision = true;
        move(arrow, new Vector3(0, -.01, 0));
        assertBlockHit(new Cell(0, 64, 1));
    }

    @Test
    void passThroughBarrierStillHitsStoneBehindIt() {
        cells.put(new Cell(1, 64, 0), BlockID.BARRIER);
        cells.put(new Cell(2, 64, 0), BlockID.STONE);
        NativeArrow arrow = arrow(new Vector3(.5, 64.45, .5));
        arrow.setPassThroughBarrier(true);
        move(arrow, new Vector3(1.8, 0, 0));
        assertBlockHit(new Cell(2, 64, 0));
    }

    @Test
    void solidBarrierStillStopsOrdinaryArrow() {
        cells.put(new Cell(1, 64, 0), BlockID.BARRIER);
        cells.put(new Cell(2, 64, 0), BlockID.STONE);
        move(arrow(new Vector3(.5, 64.45, .5)), new Vector3(1.8, 0, 0));
        assertBlockHit(new Cell(1, 64, 0));
    }

    @Test
    void entityContactStillUsesNativeEntityHitAndDamagePath() {
        EntityLiving target = mock(EntityLiving.class);
        target.boundingBox = new SimpleAxisAlignedBB(.8, 64, .4, 1, 65, .6);
        when(target.getBoundingBox()).thenReturn(target.boundingBox);
        when(target.attack(any())).thenReturn(true);
        entities.put(1L, target);
        NativeArrow arrow = arrow(new Vector3(.5, 64.45, .5));
        move(arrow, new Vector3(.9, 0, 0));
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).getMovingObjectPosition().typeOfHit);
        assertSame(target, hits.get(0).getMovingObjectPosition().entityHit);
        verify(target).attack(any());
        assertEquals(1, arrow.hitCallbacks);
        assertEquals(0, arrow.groundCallbacks);
    }

    @Test
    void noContactProducesNoHitOrCallbacks() {
        NativeArrow arrow = arrow(new Vector3(.5, 64.45, .5));
        move(arrow, new Vector3(.9, 0, 0));
        assertTrue(hits.isEmpty());
        assertFalse(arrow.hadCollision);
        assertEquals(0, arrow.hitCallbacks);
        assertEquals(0, arrow.groundCallbacks);
    }

    @Test
    void actualTridentKeepsContactIdentityAndExistingGroundCallback() {
        cells.put(new Cell(0, 63, 0), BlockID.STONE);
        cells.put(new Cell(0, 64, 1), BlockID.WOOL);
        NativeTrident trident = new NativeTrident(chunk, new Vector3(.5, 64.45, .5), shooter);
        move(trident, new Vector3(0, -.9, 0));
        assertBlockHit(new Cell(0, 63, 0));
        assertEquals(1, trident.hitCallbacks);
        assertEquals(1, trident.groundCallbacks);
        assertEquals(0, trident.getStuckToBlockPos().x);
        assertEquals(63, trident.getStuckToBlockPos().y);
        assertEquals(0, trident.getStuckToBlockPos().z);
    }

    @Test
    void cancelledTridentDoesNotRunGroundOrStickCallback() {
        cells.put(new Cell(0, 63, 0), BlockID.STONE);
        cells.put(new Cell(0, 64, 1), BlockID.WOOL);
        cancelHit = true;
        NativeTrident trident = new NativeTrident(chunk, new Vector3(.5, 64.45, .5), shooter);
        move(trident, new Vector3(0, -.9, 0));
        assertBlockHit(new Cell(0, 63, 0));
        assertEquals(0, trident.hitCallbacks);
        assertEquals(0, trident.groundCallbacks);
        assertEquals(0, trident.getStuckToBlockPos().y);
    }

    private NativeArrow arrow(Vector3 start) { return new NativeArrow(chunk, start, shooter); }

    private static void move(EntitySlenderProjectile arrow, Vector3 movement) {
        arrow.motionX = movement.x;
        arrow.motionY = movement.y;
        arrow.motionZ = movement.z;
        assertTrue(arrow.move(movement.x, movement.y, movement.z));
    }

    private void assertBlockHit(Cell expected) {
        assertEquals(1, hits.size(), "exactly one native hit event");
        MovingObjectPosition hit = hits.get(0).getMovingObjectPosition();
        assertEquals(0, hit.typeOfHit);
        assertEquals(expected, new Cell(hit.blockX, hit.blockY, hit.blockZ));
    }

    private static final class NativeArrow extends EntityArrow {
        int hitCallbacks;
        int groundCallbacks;
        boolean forceUnknownCollision;
        NativeArrow(FullChunk chunk, Vector3 start, Entity shooter) {
            super(chunk, Entity.getDefaultNBT(start), shooter);
        }
        @Override protected void checkChunks() {} // No chunk-membership scheduler in this bounded terrain fixture.
        @Override protected void updateFallState(boolean ground) {} // Fall damage is unrelated to block identity.
        @Override protected void checkGroundState(double mx, double my, double mz, double x, double y, double z) {
            super.checkGroundState(mx, my, mz, x, y, z);
            if (forceUnknownCollision) isCollided = true;
        }
        @Override public void onHit() { hitCallbacks++; super.onHit(); }
        @Override protected void onHitGround(Vector3 position) { groundCallbacks++; super.onHitGround(position); }
    }

    private static final class NativeTrident extends EntityThrownTrident {
        int hitCallbacks;
        int groundCallbacks;
        NativeTrident(FullChunk chunk, Vector3 start, Entity shooter) {
            super(chunk, Entity.getDefaultNBT(start), shooter);
        }
        @Override protected void checkChunks() {}
        @Override protected void updateFallState(boolean ground) {}
        @Override public void onHit() { hitCallbacks++; super.onHit(); }
        @Override public void onHitGround(Vector3 position) { groundCallbacks++; super.onHitGround(position); }
    }
}
