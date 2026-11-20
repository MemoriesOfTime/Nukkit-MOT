package cn.nukkit.level.format.generic;

import cn.nukkit.MockServer;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real chunk loader boundary, including its original NBT and unknown-tile custody. */
class BlockEntityLegacyIdTest {
    private BaseFullChunk chunk;

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() throws Exception {
        chunk = mock(BaseFullChunk.class, CALLS_REAL_METHODS);
        Field changes = BaseFullChunk.class.getDeclaredField("changes");
        changes.setAccessible(true);
        changes.set(chunk, new AtomicLong());
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(mock(Level.class));
        chunk.setProvider(provider);
    }

    private static CompoundTag tile(String id, int x) {
        return new CompoundTag().putString("id", id).putInt("x", x).putInt("y", 64).putInt("z", 0);
    }

    static Stream<Arguments> ids() {
        return Stream.of(
                Arguments.of("Chest", "Chest"),
                Arguments.of("BlockEntityChest", "Chest"),
                Arguments.of("CustomBlockEntityChest", "CustomChest"),
                Arguments.of("BlockEntityBlockEntityChest", "BlockEntityChest"),
                Arguments.of("ChestBlockEntity", "Chest"),
                Arguments.of("BlockEntity", ""),
                Arguments.of("", ""),
                Arguments.of("blockentityChest", "blockentityChest"),
                Arguments.of("minecraft:BlockEntityChest", "minecraft:Chest"),
                Arguments.of("§a😀BlockEntityСундук", "§a😀Сундук"),
                Arguments.of("\ud800BlockEntity\udfff", "\ud800\udfff"),
                Arguments.of("\\$BlockEntity$\\", "\\$$\\"));
    }

    @ParameterizedTest
    @MethodSource("ids")
    void preservesFirstOccurrenceFactoryArgumentAndRawUnknownNbt(String id, String expected) {
        CompoundTag nbt = tile(id, 0);
        chunk.NBTtiles = new ArrayList<>(List.of(nbt));
        try (MockedStatic<BlockEntity> factory = mockStatic(BlockEntity.class)) {
            chunk.initChunk();
            factory.verify(() -> BlockEntity.createBlockEntity(expected, chunk, nbt));
            factory.verifyNoMoreInteractions();
        }
        assertEquals(1, chunk.getUnknownTiles().size());
        assertSame(nbt, chunk.getUnknownTiles().iterator().next());
        assertEquals(id, nbt.getString("id"));
        assertFalse(chunk.hasChanged());
        assertNull(chunk.NBTtiles);
    }

    @Test
    void keepsGuardedInputOrderAndCallsFactoryOnlyOncePerValidTile() {
        CompoundTag first = tile("BlockEntitySales", 0);
        CompoundTag number = tile("unused", 1).putInt("id", 42);
        CompoundTag missingId = new CompoundTag().putInt("x", 2).putInt("z", 0);
        CompoundTag outside = tile("BlockEntitySales", 16);
        List<CompoundTag> seen = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        chunk.NBTtiles = new ArrayList<>(Arrays.asList(null, missingId, first, outside, number));
        try (MockedStatic<BlockEntity> factory = mockStatic(BlockEntity.class, call -> {
            assertEquals("createBlockEntity", call.getMethod().getName());
            assertSame(chunk, call.getArgument(1));
            ids.add(call.getArgument(0));
            seen.add(call.getArgument(2));
            return null;
        })) {
            chunk.initChunk();
            chunk.initChunk();
        }
        assertEquals(List.of("Sales", "42"), ids);
        assertEquals(2, seen.size());
        assertSame(first, seen.get(0));
        assertSame(number, seen.get(1));
        assertEquals(2, chunk.getUnknownTiles().size());
        assertTrue(chunk.getUnknownTiles().containsAll(seen));
        assertEquals("BlockEntitySales", first.getString("id"));
        assertTrue(chunk.hasChanged());
        assertNull(chunk.NBTtiles);
    }

    @Test
    void successfulFactoryResultDoesNotRetainUnknownNbt() {
        CompoundTag nbt = tile("CustomBlockEntitySales", 0);
        chunk.NBTtiles = new ArrayList<>(List.of(nbt));
        BlockEntity entity = mock(BlockEntity.class);
        try (MockedStatic<BlockEntity> factory = mockStatic(BlockEntity.class)) {
            factory.when(() -> BlockEntity.createBlockEntity("CustomSales", chunk, nbt)).thenReturn(entity);
            chunk.initChunk();
            factory.verify(() -> BlockEntity.createBlockEntity("CustomSales", chunk, nbt));
            factory.verifyNoMoreInteractions();
        }
        assertTrue(chunk.getUnknownTiles().isEmpty());
        assertFalse(chunk.hasChanged());
        assertEquals("CustomBlockEntitySales", nbt.getString("id"));
    }

    @Test
    void malformedTagTypeStillFailsBeforeFactory() {
        CompoundTag nbt = tile("unused", 0).putCompound("id", new CompoundTag());
        chunk.NBTtiles = new ArrayList<>(List.of(nbt));
        try (MockedStatic<BlockEntity> factory = mockStatic(BlockEntity.class)) {
            assertThrows(ClassCastException.class, chunk::initChunk);
            factory.verifyNoInteractions();
        }
        assertSame(nbt, chunk.NBTtiles.get(0));
        assertTrue(chunk.getUnknownTiles().isEmpty());
        assertFalse(chunk.hasChanged());
    }

    @Test
    void nullStringTagStillFailsBeforeFactory() {
        CompoundTag nbt = tile("unused", 0);
        StringTag id = new StringTag("id");
        id.data = null;
        nbt.put("id", id);
        chunk.NBTtiles = new ArrayList<>(List.of(nbt));
        try (MockedStatic<BlockEntity> factory = mockStatic(BlockEntity.class)) {
            assertThrows(NullPointerException.class, chunk::initChunk);
            factory.verifyNoInteractions();
        }
        assertSame(nbt, chunk.NBTtiles.get(0));
        assertTrue(chunk.getUnknownTiles().isEmpty());
        assertFalse(chunk.hasChanged());
    }
}
