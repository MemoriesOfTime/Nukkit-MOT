package cn.nukkit.level.explosion;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.StrongExplosion;
import cn.nukkit.level.WeakExplosion;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.Hash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExplosionUpdateMembershipTest {
    enum Route { ORDINARY, WEAK, STRONG }

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void overlappingNeighborsKeepCallbackOrderLayersAndCancellation(Route route) {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(level.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        when(level.getNearbyEntities(any(AxisAlignedBB.class), nullable(Entity.class)))
                .thenReturn(new Entity[0]);
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));

        Set<Block> affected = new LinkedHashSet<>();
        affected.add(at(level, -1, 0, 0, 0));
        affected.add(at(level, 1, 0, 0, 0));
        affected.add(at(level, 0, 0, 1, 0));
        affected.add(at(level, 4, -2, -4, 0));
        affected.add(at(level, 10, 0, 10, 1));
        List<String> trace = new ArrayList<>();
        when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                at(level, call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(level.getBlock(any(Vector3.class))).thenAnswer(call -> tracked(level, call.getArgument(0), 0, trace));
        when(level.getBlock(any(Vector3.class), eq(1))).thenAnswer(call -> tracked(level, call.getArgument(0), 1, trace));
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof EntityExplodeEvent explosion) {
                explosion.setBlockList(new ArrayList<>(affected));
                explosion.setYield(0);
            } else if (event instanceof BlockUpdateEvent update) {
                trace.add("event:" + key(update.getBlock(), update.getBlock().layer));
                if (cancelled(update.getBlock())) update.setCancelled();
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));

        doAnswer(call -> {
            Block block = call.getArgument(1);
            trace.add("xray:" + key(block, block.layer));
            return null;
        }).when(level).antiXrayOnBlockChange(isNull(), any(Block.class), eq(0));

        List<String> expected = new ArrayList<>();
        Collection<Block> membership = route == Route.ORDINARY ? affected : new ArrayList<>(affected);
        Set<Long> visited = new HashSet<>();
        for (Block block : affected) {
            if (route == Route.ORDINARY && block.layer != 0) continue;
            Vector3 position = new Vector3(block.x, block.y, block.z);
            for (BlockFace face : BlockFace.values()) {
                Vector3 neighbor = position.getSide(face);
                long hash = Hash.hashBlock(neighbor.getFloorX(), neighbor.getFloorY(), neighbor.getFloorZ());
                if (membership.contains(neighbor) || !visited.add(hash)) continue;
                for (int layer = 0; layer <= (route == Route.ORDINARY ? 1 : 0); layer++) {
                    // processBlockUpdate(Vector3) re-reads layer 0 even when passed a layer-1 Block.
                    expected.add("event:" + key(neighbor, 0));
                    if (!cancelled(neighbor)) expected.add("update:" + key(neighbor, 0));
                }
                if (route != Route.ORDINARY) expected.add("xray:" + key(block, block.layer));
            }
        }
        assertTrue(visited.contains(Hash.hashBlock(0, 0, 0)), "cover zero-coordinate neighbor");
        assertTrue(visited.size() < (route == Route.ORDINARY ? 4 : 5) * BlockFace.values().length,
                "fixture must overlap neighbors");
        Position center = new Position(0, 0, 0, level);
        Explosion explosion = switch (route) {
            case ORDINARY -> new Explosion(center, 4, (Entity) null);
            case WEAK -> new WeakExplosion(center, 4, null);
            case STRONG -> new StrongExplosion(center, 4, null);
        };
        assertTrue(explosion.explodeB());
        assertEquals(expected, trace);
        if (route == Route.ORDINARY) {
            verify(level, times(affected.size())).setBlock(any(Vector3.class), anyInt(), any(Block.class));
            verify(level, times(visited.size())).getBlock(any(Vector3.class), eq(1));
        } else {
            verify(level, times(affected.size())).setBlockAt(anyInt(), anyInt(), anyInt(), eq(Block.AIR));
        }
    }

    private static Block at(Level level, int x, int y, int z, int layer) {
        Block block = Block.get(Block.STONE, 0, level, x, y, z);
        block.layer = layer;
        return block;
    }

    private static Block tracked(Level level, Vector3 position, int layer, List<String> trace) {
        Block block = spy(at(level, position.getFloorX(), position.getFloorY(), position.getFloorZ(), layer));
        doAnswer(call -> {
            assertEquals(Level.BLOCK_UPDATE_NORMAL, (int) call.getArgument(0));
            trace.add("update:" + key(block, layer));
            return 0;
        }).when(block).onUpdate(anyInt());
        return block;
    }

    private static boolean cancelled(Vector3 position) {
        return position.getFloorY() < 0;
    }

    private static String key(Vector3 position, int layer) {
        return position.getFloorX() + "," + position.getFloorY() + "," + position.getFloorZ() + ":" + layer;
    }
}
