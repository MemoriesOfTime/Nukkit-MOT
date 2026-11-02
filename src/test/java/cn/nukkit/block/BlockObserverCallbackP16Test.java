package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockObserverCallbackP16Test {
    static Stream<Arguments> replacements() {
        var cases = new ArrayList<Arguments>();
        for (boolean powered : new boolean[]{false, true}) {
            for (boolean redstoneUpdate : new boolean[]{false, true}) {
                for (String replacement : new String[]{"air", "stone", "rotated", "power"}) {
                    cases.add(Arguments.of(powered, redstoneUpdate, replacement));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("replacements")
    void preservesBlockChangedByEitherCallbackOnEitherPulseEdge(
            boolean powered, boolean redstoneUpdate, String replacement) {
        Fixture f = new Fixture(BlockFace.NORTH, powered);
        Block expected = switch (replacement) {
            case "air" -> new BlockAir();
            case "stone" -> new BlockStone();
            case "rotated" -> observer(BlockFace.EAST, powered);
            default -> observer(BlockFace.NORTH, !powered);
        };
        doAnswer(i -> {
            Object event = i.getArgument(0);
            if (redstoneUpdate ? event instanceof RedstoneUpdateEvent : event instanceof BlockRedstoneEvent) {
                f.current = expected.clone();
            }
            return null;
        }).when(f.plugins).callEvent(any());
        int result = f.subject.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertEquals(expected.getId(), f.current.getId());
        assertEquals(expected.getDamage(), f.current.getDamage());
        assertEquals(0, result);
        assertEquals(0, f.writes, "Callback owns replacement; observer must not overwrite it");
        assertEquals(0, f.notifications);
        assertTrue(f.delays.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(BlockFace.class)
    void ordinaryPulseStillNotifiesOutputAndTurnsOffAfterTwoTicks(BlockFace facing) {
        Fixture f = new Fixture(facing, false);
        assertEquals(Level.BLOCK_UPDATE_SCHEDULED, f.subject.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
        assertTrue(((BlockObserver) f.current).isPowered());
        assertEquals(List.of(2), f.delays);
        assertEquals(2, f.notifications);
        f.subject = (BlockObserver) f.current.clone();
        assertEquals(Level.BLOCK_UPDATE_SCHEDULED, f.subject.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
        assertFalse(((BlockObserver) f.current).isPowered());
        assertEquals(facing, ((BlockObserver) f.current).getBlockFace());
        assertEquals(List.of(2), f.delays);
        assertEquals(4, f.notifications);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledRedstoneUpdateKeepsExistingState(boolean powered) {
        Fixture f = new Fixture(BlockFace.NORTH, powered);
        doAnswer(i -> {
            Object event = i.getArgument(0);
            if (event instanceof RedstoneUpdateEvent update) update.setCancelled(true);
            return null;
        }).when(f.plugins).callEvent(any());
        assertEquals(0, f.subject.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
        assertEquals(powered, ((BlockObserver) f.current).isPowered());
        assertEquals(0, f.writes);
        assertTrue(f.delays.isEmpty());
    }

    static Stream<Arguments> relocations() {
        var cases = new ArrayList<Arguments>();
        for (boolean powered : new boolean[]{false, true}) {
            for (boolean redstoneUpdate : new boolean[]{false, true}) {
                for (String change : new String[]{"position", "level", "layer"}) {
                    cases.add(Arguments.of(powered, redstoneUpdate, change));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("relocations")
    void callbacksCannotRetargetTheScheduledObserver(boolean powered, boolean redstoneUpdate, String change) {
        Fixture origin = new Fixture(BlockFace.NORTH, powered);
        Fixture destination = new Fixture(BlockFace.NORTH, powered);
        doAnswer(call -> {
            Object event = call.getArgument(0);
            if (redstoneUpdate ? event instanceof RedstoneUpdateEvent : event instanceof BlockRedstoneEvent) {
                switch (change) {
                    case "position" -> origin.subject.x = 32;
                    case "level" -> origin.subject.level = destination.level;
                    case "layer" -> origin.subject.layer = 1;
                    default -> throw new AssertionError(change);
                }
            }
            return null;
        }).when(origin.plugins).callEvent(any());
        assertEquals(0, origin.subject.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
        assertEquals(0, origin.writes + destination.writes, "a callback cannot retarget scheduled writes");
        assertEquals(0, origin.notifications + destination.notifications);
        assertTrue(origin.delays.isEmpty());
        assertTrue(destination.delays.isEmpty());
        verify(destination.level, never()).getBlock(any(Vector3.class), anyInt(), eq(false));
    }

    private static BlockObserver observer(BlockFace facing, boolean powered) {
        BlockObserver observer = new BlockObserver();
        observer.setBlockFace(facing);
        observer.setPowered(powered);
        return observer;
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final List<Integer> delays = new ArrayList<>();
        BlockObserver subject;
        Block current;
        int writes, notifications;
        Fixture(BlockFace facing, boolean powered) {
            subject = observer(facing, powered);
            subject.level = level; subject.x = 0; subject.y = 64; subject.z = 0;
            current = subject.clone();
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(i -> current.getId());
            when(level.getBlock(any(Vector3.class), anyInt(), eq(false))).thenAnswer(i -> current.clone());
            doAnswer(i -> {
                if ((int) i.getArgument(0) == 0 && (int) i.getArgument(1) == 64 && (int) i.getArgument(2) == 0) {
                    return current.clone();
                }
                return new BlockAir() {
                    @Override public int onUpdate(int type) { notifications++; return type; }
                };
            }).when(level).getBlock(anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(i -> { current = ((Block) i.getArgument(1)).clone(); writes++; return true; })
                    .when(level).setBlock(any(Vector3.class), any(Block.class));
            doAnswer(i -> { delays.add(i.getArgument(1)); return null; })
                    .when(level).scheduleUpdate(any(Block.class), anyInt());
            doAnswer(i -> { notifications++; return null; })
                    .when(level).updateAroundRedstone(any(Vector3.class), any(BlockFace.class));
        }
    }
}
