package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockItemFrame;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.blockentity.BlockEntityItemFrameGlow;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.ItemFrameDropItemEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real LevelDB tile lifecycle; Level terrain and outbound delivery are adapters. */
@Isolated("native Server.instance and storage configuration")
class FrameChunkReloadTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,true,false", "true,false,false", "true,true,false",
            "false,false,true", "false,true,true", "true,false,true", "true,true,true"})
    void diskReloadPreservesTileAndWireNbt(boolean glow, boolean filled, boolean deferred) throws Exception {
        try (World world = new World(directory)) {
            BlockEntityItemFrame original = world.create(glow, filled);
            CompoundTag item = original.namedTag.getCompound("Item").copy();
            BlockEntityItemFrame restored = world.reopen(deferred);
            assertTrue(original.closed);
            assertNotSame(original, restored);
            assertEquals(glow, restored instanceof BlockEntityItemFrameGlow);
            assertTrue(restored.isBlockEntityValid());
            assertEquals(glow ? Block.GLOW_FRAME : Block.ITEM_FRAME_BLOCK, world.cell().getId());
            assertEquals(2, world.cell().getDamage());
            assertStored(restored, item, 7, 0.375f);
            world.chunk.initChunk();
            assertSame(restored, world.frame());
            assertEquals(1, world.chunk.getBlockEntities().size());

            for (int protocol : ProtocolInfo.SUPPORTED_PROTOCOLS.stream()
                    .filter(p -> p >= 589 && p <= ProtocolInfo.CURRENT_PROTOCOL).distinct().toList()) {
                byte[] packet = NetworkChunkSerializer.serializeBlockEntities(List.of(restored), protocol);
                CompoundTag wire = NBTIO.read(packet, ByteOrder.LITTLE_ENDIAN, true);
                assertEquals(glow ? BlockEntity.GLOW_ITEM_FRAME : BlockEntity.ITEM_FRAME, wire.getString("id"));
                assertEquals(filled, !NBTIO.getItemHelper(wire.getCompound("Item").copy()).isNull());
                if (filled) {
                    assertEquals(7, wire.getCompound("Item").getByte("Count"));
                    assertEquals(7, wire.getByte("ItemRotation"));
                    CompoundTag nested = wire.getCompound("Item").getCompound("tag").getCompound("nested");
                    assertEquals(51, nested.getInt("receipt"));
                    nested.putInt("receipt", 99);
                }
                assertStored(restored, item, 7, 0.375f);
            }
            assertStored(world.reopen(!deferred), item, 7, 0.375f);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledExtractionPreservesItemAcrossAnotherDiskReload(boolean glow) throws Exception {
        try (World world = new World(directory)) {
            world.create(glow, true);
            BlockEntityItemFrame frame = world.reopen(false);
            CompoundTag item = frame.namedTag.getCompound("Item").copy();
            PluginManager plugins = mock(PluginManager.class);
            doAnswer(call -> {
                if (call.getArgument(0) instanceof ItemFrameDropItemEvent event) event.setCancelled(true);
                return null;
            }).when(plugins).callEvent(any(Event.class));
            when(world.server.getPluginManager()).thenReturn(plugins);
            Player viewer = mock(Player.class);
            viewer.protocol = ProtocolInfo.CURRENT_PROTOCOL;
            assertTrue(frame.dropItem(viewer));
            assertTrue(world.drops.isEmpty());
            assertStored(world.reopen(true), item, 7, 0.375f);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulExtractionPersistsEmptyTileAndDoesNotDropTwice(boolean glow) throws Exception {
        try (World world = new World(directory)) {
            BlockEntityItemFrame frame = world.create(glow, true);
            CompoundTag item = NBTIO.putItemHelper(frame.getItem());
            frame.setItemDropChance(1.0f);
            frame = world.reopen(false);
            assertEquals(item, NBTIO.putItemHelper(frame.getItem()));
            assertTrue(frame.dropItem(null));
            assertEquals(1, world.drops.size());
            assertEquals(item, NBTIO.putItemHelper(world.drops.get(0)));
            frame = world.reopen(true);
            assertTrue(frame.getItem().isNull());
            assertEquals(0, frame.getItemRotation());
            assertEquals(1.0f, frame.getItemDropChance());
            assertFalse(frame.dropItem(null));
            assertEquals(1, world.drops.size());
            assertTrue(world.reopen(false).getItem().isNull());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingTileFallbackKeepsTheBlockFrameType(boolean glow) throws Exception {
        try (World world = new World(directory)) {
            world.createBlock(glow);
            BlockEntityItemFrame created = ((BlockItemFrame) world.cell()).getOrCreateBlockEntity();
            assertEquals(glow, created instanceof BlockEntityItemFrameGlow);
            assertEquals(glow ? BlockEntity.GLOW_ITEM_FRAME : BlockEntity.ITEM_FRAME,
                    created.getSpawnCompound(ProtocolInfo.CURRENT_PROTOCOL).getString("id"));
            assertEquals(glow, world.reopen(true) instanceof BlockEntityItemFrameGlow);
        }
    }

    @Test
    void compatibleExistingTileIsNeverReplacedWithAnEmptyGlowTile() throws Exception {
        try (World world = new World(directory)) {
            world.createBlock(true);
            BlockEntityItemFrame existing = world.createTile(false, true);
            CompoundTag item = existing.namedTag.getCompound("Item").copy();
            assertSame(existing, ((BlockItemFrame) world.cell()).getOrCreateBlockEntity());
            assertFalse(existing.closed);
            assertEquals(1, world.chunk.getBlockEntities().size());
            assertStored(existing, item, 7, 0.375f);
        }
    }

    private static void assertStored(BlockEntityItemFrame frame, CompoundTag item, int rotation, float chance) {
        assertEquals(item, frame.namedTag.getCompound("Item"));
        assertEquals(rotation, frame.getItemRotation());
        assertEquals(chance, frame.getItemDropChance());
    }

    private static final class World implements AutoCloseable {
        final Path path;
        final Server server;
        final boolean previousAsync;
        final boolean previousNative;
        final int previousCache;
        final int previousPending;
        final PluginManager previousPlugins;
        final List<Item> drops = new ArrayList<>();
        Level level;
        LevelDBProvider provider;
        LevelDBChunk chunk;

        World(Path path) throws Exception {
            MockServer.init();
            this.path = path;
            server = Server.getInstance();
            previousAsync = server.asyncChunkSending;
            previousNative = server.useNativeLevelDB;
            previousCache = server.levelDbCache;
            previousPending = server.maxPendingChunkWrites;
            previousPlugins = server.getPluginManager();
            server.asyncChunkSending = false;
            server.useNativeLevelDB = false;
            server.levelDbCache = 8;
            server.maxPendingChunkWrites = 128;
            BlockEntity.registerBlockEntity(BlockEntity.ITEM_FRAME, BlockEntityItemFrame.class);
            BlockEntity.registerBlockEntity(BlockEntity.GLOW_ITEM_FRAME, BlockEntityItemFrameGlow.class);
            LevelDBProvider.generate(path.toString(), "frame-reload-test", 1107L, Flat.class);
            open();
        }

        void open() throws Exception {
            level = mock(Level.class);
            when(level.getServer()).thenReturn(server);
            when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
            when(level.getProvider()).thenAnswer(call -> provider);
            when(level.getChunk(anyInt(), anyInt())).thenAnswer(call -> provider.getChunk(0, 0, false));
            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> provider.getChunk(0, 0, false));
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> cell());
            when(level.getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call -> chunk.getTile(8, 64, 8));
            when(level.getPendingBlockUpdates(any(FullChunk.class))).thenReturn(Set.of());
            doAnswer(call -> { drops.add(((Item) call.getArgument(1)).clone()); return null; })
                    .when(level).dropItem(any(Vector3.class), any(Item.class));
            provider = new LevelDBProvider(level, path.toString());
        }

        void createBlock(boolean glow) {
            chunk = provider.getEmptyChunk(0, 0);
            chunk.setGenerated(true);
            chunk.setBlock(8, 64, 8, glow ? Block.GLOW_FRAME : Block.ITEM_FRAME_BLOCK, 2);
            provider.setChunk(0, 0, chunk);
            chunk.initChunk();
        }

        BlockEntityItemFrame create(boolean glow, boolean filled) {
            createBlock(glow);
            return createTile(glow, filled);
        }

        BlockEntityItemFrame createTile(boolean glow, boolean filled) {
            CompoundTag tag = new CompoundTag().putString("id", glow ? BlockEntity.GLOW_ITEM_FRAME : BlockEntity.ITEM_FRAME)
                    .putInt("x", 8).putInt("y", 64).putInt("z", 8)
                    .putByte("ItemRotation", 7).putFloat("ItemDropChance", 0.375f);
            if (filled) {
                Item item = Item.get(Item.DIAMOND, 0, 7);
                item.setNamedTag(new CompoundTag().putString("opaque", "qa1107-sentinel")
                        .putCompound("nested", new CompoundTag().putInt("receipt", 51)));
                tag.putCompound("Item", NBTIO.putItemHelper(item));
            }
            return (BlockEntityItemFrame) BlockEntity.createBlockEntity(tag.getString("id"), chunk, tag);
        }

        BlockEntityItemFrame reopen(boolean deferred) throws Exception {
            provider.saveChunkSync(0, 0, chunk);
            assertTrue(provider.unloadChunk(0, 0, false));
            assertFalse(provider.isChunkLoaded(0, 0));
            provider.close();
            open();
            if (deferred) {
                var reader = Executors.newSingleThreadExecutor();
                try {
                    chunk = (LevelDBChunk) reader.submit(() -> provider.readChunkOffThread(0, 0)).get(5, TimeUnit.SECONDS);
                } finally {
                    reader.shutdownNow();
                    assertTrue(reader.awaitTermination(5, TimeUnit.SECONDS));
                }
                assertNotNull(chunk);
                assertNull(provider.putChunkIfAbsent(0, 0, chunk));
            } else {
                chunk = (LevelDBChunk) provider.getChunk(0, 0, false);
            }
            assertNotNull(chunk);
            chunk.initChunk();
            return frame();
        }

        BlockEntityItemFrame frame() {
            return assertInstanceOf(BlockEntityItemFrame.class, chunk.getTile(8, 64, 8));
        }

        Block cell() {
            Block block = Block.get(chunk.getBlockId(8, 64, 8), chunk.getBlockData(8, 64, 8));
            block.x = 8; block.y = 64; block.z = 8; block.level = level;
            return block;
        }

        @Override public void close() {
            try { if (provider != null) provider.close(); }
            finally {
                when(server.getPluginManager()).thenReturn(previousPlugins);
                server.asyncChunkSending = previousAsync;
                server.useNativeLevelDB = previousNative;
                server.levelDbCache = previousCache;
                server.maxPendingChunkWrites = previousPending;
            }
        }
    }
}
