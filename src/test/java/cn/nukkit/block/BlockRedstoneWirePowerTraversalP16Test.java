package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockRedstoneWirePowerTraversalP16Test {
    static Stream<Vector3> origins() {
        return Stream.of(new Vector3(0,64,0), new Vector3(-0.1,64,-0.1), new Vector3(15.999,64,16),
                new Vector3(-16,64,-16.1), new Vector3(2147483647.75,64,-2147483648.25),
                new Vector3(-2147483648.25,64,2147483647.75), new Vector3(2,-64.25,3),
                new Vector3(2,319.75,3));
    }

    @ParameterizedTest @MethodSource("origins")
    void retainsFreshPositionArgumentsAndLegacyFloorCoordinates(Vector3 origin) throws Exception {
        Fixture f = new Fixture(); f.wire.x=origin.x; f.wire.y=origin.y; f.wire.z=origin.z;
        assertEquals(0, power(f.wire));
        assertEquals(6,f.reads.size());
        Location snapshot = new Location(origin.x,origin.y,origin.z,f.level);
        Set<Vector3> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i=0; i<6; i++) {
            Vector3 actual = f.reads.get(i), expected = snapshot.getSide(BlockFace.values()[i]);
            assertEquals(Position.class,actual.getClass()); assertSame(f.level,((Position)actual).getLevel());
            assertEquals(expected.x,actual.x); assertEquals(expected.y,actual.y); assertEquals(expected.z,actual.z);
            assertEquals(expected.getFloorX(),actual.getFloorX()); assertEquals(expected.getFloorY(),actual.getFloorY());
            assertEquals(expected.getFloorZ(),actual.getFloorZ()); assertTrue(identities.add(actual),"Do not reuse retained lookup arguments");
        }
        verify(f.level,never()).getBlock(anyInt(),anyInt(),anyInt());
    }

    @ParameterizedTest @EnumSource(BlockFace.class)
    void keepsWeakPowerFaceOrderAndFirstFifteenExit(BlockFace powered) throws Exception {
        Fixture f = new Fixture(); Block source = mock(Block.class);
        when(source.getWeakPower(any(BlockFace.class))).thenAnswer(call -> call.getArgument(0)==powered ? 15 : 0);
        f.block = source;
        assertEquals(15,power(f.wire));
        assertEquals(powered.ordinal()+1,f.reads.size());
        var order = inOrder(source);
        for (int i=0;i<=powered.ordinal();i++) order.verify(source).getWeakPower(BlockFace.values()[i]);
        verify(source,times(powered.ordinal()+1)).getWeakPower(any(BlockFace.class));
    }

    @ParameterizedTest @EnumSource(BlockFace.class)
    void keepsAllSixStrongPowerDirectionsThroughNormalNeighbor(BlockFace powered) throws Exception {
        Fixture f = new Fixture(); Block normal = mock(Block.class), source = mock(Block.class);
        when(normal.getId()).thenReturn(BlockID.STONE); when(normal.isNormalBlock()).thenReturn(true);
        when(source.getStrongPower(any(BlockFace.class))).thenAnswer(call -> call.getArgument(0)==powered ? 14 : 0);
        when(f.level.getBlock(any(Vector3.class))).thenAnswer(call -> {
            Vector3 pos = call.getArgument(0); f.reads.add(pos);
            return pos.x==0 && pos.y==63 && pos.z==0 ? normal : source;
        });
        assertEquals(14,power(f.wire));
        var order = inOrder(source);
        for (BlockFace face : BlockFace.values()) order.verify(source).getStrongPower(face);
        verify(source,times(6)).getStrongPower(any(BlockFace.class));
    }

    @Test void adjacentWireStillProvidesNoIndirectPower() throws Exception {
        Fixture f = new Fixture(); Block wire = mock(Block.class);
        when(wire.getId()).thenReturn(BlockID.REDSTONE_WIRE); f.block=wire;
        assertEquals(0,power(f.wire)); assertEquals(6,f.reads.size());
        verify(wire,never()).isNormalBlock(); verify(wire,never()).getWeakPower(any()); verify(wire,never()).getStrongPower(any());
    }

    @Test void originSnapshotSurvivesNestedCalculationAndReceiverLevelChange() throws Exception {
        Fixture outer = new Fixture(), nested = new Fixture();
        outer.wire.position(new Position(2,64,3,outer.level)); nested.wire.position(new Position(100,70,200,nested.level));
        Block first = mock(Block.class), next = mock(Block.class);
        when(next.getWeakPower(any(BlockFace.class))).thenReturn(9); nested.block=next;
        when(first.getWeakPower(any(BlockFace.class))).thenAnswer(call -> {
            assertEquals(BlockFace.DOWN,call.getArgument(0));
            assertEquals(9,power(nested.wire));
            outer.wire.position(new Position(900,80,900,nested.level));
            return 7;
        });
        outer.block=first;
        assertEquals(9,power(outer.wire));
        assertEquals(1,outer.reads.size()); assertEquals(11,nested.reads.size());
        Location snapshot = new Location(2,64,3,outer.level);
        List<Vector3> outerReads = new ArrayList<>(outer.reads); outerReads.addAll(nested.reads.subList(6,11));
        for (int i=0;i<6;i++) {
            Vector3 expected=snapshot.getSide(BlockFace.values()[i]), actual=outerReads.get(i);
            assertEquals(expected.x,actual.x); assertEquals(expected.y,actual.y); assertEquals(expected.z,actual.z);
            assertSame(outer.level,((Position)actual).getLevel(),"Position context stays captured; lookup receiver is live");
        }
    }

    @Test void respectsCustomLocationSideOverrideAndVirtualOrigin() throws Exception {
        Fixture f = new Fixture(); List<BlockFace> sides = new ArrayList<>();
        Location custom = new Location(20.5,72,-30.5,f.level) {
            @Override public Position getSide(BlockFace face) {
                sides.add(face); return new Position(20.5+3*face.getXOffset(),72+3*face.getYOffset(),-30.5+3*face.getZOffset(),f.level);
            }
        };
        BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return custom; } };
        wire.position(new Position(999,99,999,f.level));
        assertEquals(0,power(wire)); assertEquals(Arrays.asList(BlockFace.values()),sides);
        for (int i=0;i<6;i++) {
            BlockFace face=BlockFace.values()[i]; Vector3 pos=f.reads.get(i);
            assertEquals(20.5+3*face.getXOffset(),pos.x); assertEquals(72+3*face.getYOffset(),pos.y);
            assertEquals(-30.5+3*face.getZOffset(),pos.z);
        }
    }

    @Test void respectsCustomPositionSideOverrideDuringStrongPowerScan() throws Exception {
        Fixture f = new Fixture(); List<BlockFace> sides = new ArrayList<>();
        Position custom = new Position(0,63,0,f.level) {
            @Override public Position getSide(BlockFace face) {
                sides.add(face); return new Position(2*face.getXOffset(),63+2*face.getYOffset(),2*face.getZOffset(),f.level);
            }
        };
        Location origin = new Location(0,64,0,f.level) {
            @Override public Position getSide(BlockFace face) { return face==BlockFace.DOWN ? custom : super.getSide(face); }
        };
        BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return origin; } };
        wire.position(new Position(0,64,0,f.level));
        Block normal=mock(Block.class); when(normal.getId()).thenReturn(BlockID.STONE); when(normal.isNormalBlock()).thenReturn(true);
        when(f.level.getBlock(any(Vector3.class))).thenAnswer(call -> {
            Vector3 pos=call.getArgument(0); f.reads.add(pos); return pos==custom ? normal : new BlockAir();
        });
        assertEquals(0,power(wire)); assertEquals(Arrays.asList(BlockFace.values()),sides);
        for (int i=0;i<6;i++) {
            Vector3 pos=f.reads.get(i+1); BlockFace face=BlockFace.values()[i];
            assertEquals(2*face.getXOffset(),pos.x); assertEquals(63+2*face.getYOffset(),pos.y); assertEquals(2*face.getZOffset(),pos.z);
        }
    }

    @Test void strongPowerScanAlsoExcludesAdjacentWire() throws Exception {
        Fixture f=new Fixture(); Block normal=mock(Block.class), wire=mock(Block.class);
        when(normal.getId()).thenReturn(BlockID.STONE); when(normal.isNormalBlock()).thenReturn(true);
        when(wire.getId()).thenReturn(BlockID.REDSTONE_WIRE);
        when(f.level.getBlock(any(Vector3.class))).thenAnswer(call -> {
            Vector3 pos=call.getArgument(0); f.reads.add(pos);
            return pos.x==0 && pos.y==63 && pos.z==0 ? normal : wire;
        });
        assertEquals(0,power(f.wire)); assertEquals(12,f.reads.size());
        verify(wire,never()).getStrongPower(any()); verify(wire,never()).getWeakPower(any());
    }

    @Test void invalidCapturedPositionStillFailsBeforeAnyLookup() {
        Fixture f = new Fixture();
        BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return new Location(0,64,0); } };
        wire.position(new Position(0,64,0,f.level));
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,() -> power(wire));
        assertInstanceOf(cn.nukkit.utils.LevelException.class,thrown.getCause());
        assertTrue(f.reads.isEmpty());
    }

    @Test void customSideFailureStillPropagatesWithoutLookup() {
        Fixture f = new Fixture();
        Location custom = new Location(0,64,0,f.level) { @Override public Position getSide(BlockFace face) { throw new IllegalArgumentException("custom side"); } };
        BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return custom; } };
        wire.position(new Position(0,64,0,f.level));
        InvocationTargetException thrown=assertThrows(InvocationTargetException.class,() -> power(wire));
        assertInstanceOf(IllegalArgumentException.class,thrown.getCause()); assertEquals("custom side",thrown.getCause().getMessage());
        assertTrue(f.reads.isEmpty());
    }

    private static int power(BlockRedstoneWire wire) throws Exception {
        Method method=BlockRedstoneWire.class.getDeclaredMethod("getIndirectPower"); method.setAccessible(true);
        return (int)method.invoke(wire);
    }

    private static final class Fixture {
        final Level level=mock(Level.class);
        final BlockRedstoneWire wire=new BlockRedstoneWire();
        final List<Vector3> reads=new ArrayList<>();
        Block block=new BlockAir();
        Fixture() {
            wire.position(new Position(0,64,0,level));
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> { Vector3 pos=call.getArgument(0); reads.add(pos); return block; });
        }
    }
}
