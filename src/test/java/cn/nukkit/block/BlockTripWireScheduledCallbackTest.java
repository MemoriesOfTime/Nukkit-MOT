package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.BlockUpdateEntry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockTripWireScheduledCallbackTest {
    @ParameterizedTest
    @ValueSource(ints = {Block.TRIPWIRE, Block.AIR, Block.STONE})
    void connectedScheduledCallbacksConvergeAfterFiniteChanges(int replacementId) {
        Fixture f = new Fixture();
        Entity occupant = mock(Entity.class);
        when(occupant.doesTriggerPressurePlate()).thenReturn(true);
        when(f.level.getCollidingEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[]{occupant});
        AtomicInteger stage = new AtomicInteger();
        doAnswer(invocation -> {
            BlockRedstoneEvent event = invocation.getArgument(0);
            if (stage.get() == 0 && event.getNewPower() == 15) {
                f.put(f.wirePos, new BlockTripWire(4));
                stage.set(1);
            } else if (stage.get() == 1 && event.getNewPower() == 0) {
                Block replacement = switch (replacementId) {
                    case Block.TRIPWIRE -> new BlockTripWire(5);
                    case Block.AIR -> new BlockAir();
                    default -> new BlockStone();
                };
                f.put(f.wirePos, replacement);
                stage.set(2);
            }
            return null;
        }).when(f.manager).callEvent(any(BlockRedstoneEvent.class));

        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(Block.TRIPWIRE_HOOK)).thenAnswer(i -> new BlockTripWireHook());
            // Begin consistent: connected hooks and wire are unpowered, with no pending updates.
            assertEquals(0, f.scheduler.getPendingCount());
            assertFalse(((BlockTripWireHook) f.at(f.hookPos)).isPowered());
            assertFalse(((BlockTripWire) f.at(f.wirePos)).isPowered());
            ((BlockTripWire) f.at(f.wirePos)).onEntityCollide(occupant);
            assertEquals(1, stage.get());
            assertTrue(((BlockTripWireHook) f.at(f.hookPos)).isPowered());
            assertFalse(((BlockTripWire) f.at(f.wirePos)).isPowered());
            assertTrue(f.scheduler.getPendingCount() > 0);

            f.advanceTo(10);
            assertEquals(2, stage.get(), "the real queued hook invokes the OFF callback");
            assertEquals(replacementId, f.at(f.wirePos).getId());
            for (int tick = 20; tick <= 50; tick += 10) f.advanceTo(tick);

            assertEquals(replacementId, f.at(f.wirePos).getId(), "keep the callback-owned block");
            boolean connectedAndPowered = replacementId == Block.TRIPWIRE;
            for (Vector3 position : new Vector3[]{f.hookPos, f.peerPos}) {
                BlockTripWireHook hook = (BlockTripWireHook) f.at(position);
                assertEquals(connectedAndPowered, hook.isAttached());
                assertEquals(connectedAndPowered, hook.isPowered(),
                        "hooks converge after finite callbacks even while the occupant remains");
            }
            if (connectedAndPowered) assertTrue(((BlockTripWire) f.at(f.wirePos)).isPowered());
        }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final PluginManager manager = mock(PluginManager.class);
        final BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        final Map<String, Block> world = new HashMap<>();
        final Vector3 hookPos = new Vector3(0, 64, 0);
        final Vector3 wirePos = new Vector3(1, 64, 0);
        final Vector3 peerPos = new Vector3(2, 64, 0);
        long tick;

        Fixture() {
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);
            when(level.isAreaLoaded(any(AxisAlignedBB.class))).thenReturn(true);
            when(level.getBlock(any(Vector3.class))).thenAnswer(i -> at(i.getArgument(0)));
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(i -> at(i.getArgument(0)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    at(new Vector3((int) i.getArgument(0), (int) i.getArgument(1), (int) i.getArgument(2))));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(i -> {
                        put(i.getArgument(0), i.getArgument(1));
                        return true;
                    });
            doAnswer(i -> {
                schedule(i.getArgument(0), i.getArgument(0), i.getArgument(1));
                return null;
            }).when(level).scheduleUpdate(any(Block.class), anyInt());
            doAnswer(i -> {
                schedule(i.getArgument(0), i.getArgument(1), i.getArgument(2));
                return null;
            }).when(level).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());

            BlockTripWireHook hook = new BlockTripWireHook();
            hook.setFace(BlockFace.EAST);
            hook.setAttached(true);
            put(hookPos, hook);
            BlockTripWireHook peer = new BlockTripWireHook();
            peer.setFace(BlockFace.WEST);
            peer.setAttached(true);
            put(peerPos, peer);
            put(wirePos, new BlockTripWire(4));
        }

        void advanceTo(long currentTick) {
            tick = currentTick;
            scheduler.tick(currentTick);
        }

        void schedule(Block block, Vector3 position, int delay) {
            var entry = new BlockUpdateEntry(position.floor(), block, tick + delay, 0);
            if (!scheduler.contains(entry)) scheduler.add(entry);
        }

        Block at(Vector3 position) {
            Block block = world.getOrDefault(key(position), new BlockAir()).clone();
            block.x = position.getFloorX();
            block.y = position.getFloorY();
            block.z = position.getFloorZ();
            block.level = level;
            return block;
        }

        void put(Vector3 position, Block block) {
            Block copy = block.clone();
            copy.x = position.getFloorX();
            copy.y = position.getFloorY();
            copy.z = position.getFloorZ();
            copy.level = level;
            world.put(key(position), copy);
        }

        String key(Vector3 position) {
            return position.getFloorX() + ":" + position.getFloorY() + ":" + position.getFloorZ();
        }
    }
}
