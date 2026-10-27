package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityEnderChest;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.Item;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.BlockEntityDataPacket;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise native refusal handlers and wire packets, without loading a world or opening a container. */
class PlayerDeniedContainerVisualTest {
    enum Gesture { RIGHT_CLICK, START_BREAK, LEGACY_BREAK, COMPLETE_BREAK }

    @BeforeAll static void init() { MockServer.init(); }

    static Stream<Arguments> refusals() {
        return Arrays.stream(new int[]{685, 2193}).boxed().flatMap(protocol ->
                Stream.of(false, true).flatMap(creative ->
                        Arrays.stream(new int[]{BlockID.CHEST, BlockID.SHULKER_BOX, BlockID.ENDER_CHEST})
                                .boxed().flatMap(block -> Arrays.stream(Gesture.values())
                                        .map(gesture -> Arguments.of(protocol, creative, block, gesture)))));
    }

    static Stream<Arguments> acceptedClicks() {
        return Arrays.stream(new int[]{685, 2193}).boxed().flatMap(protocol ->
                Stream.of(false, true).flatMap(creative ->
                        Arrays.stream(new int[]{BlockID.CHEST, BlockID.SHULKER_BOX, BlockID.ENDER_CHEST})
                                .mapToObj(block -> Arguments.of(protocol, creative, block))));
    }

    static Stream<Arguments> doubleChests() {
        return Arrays.stream(new int[]{685, 2193}).boxed().flatMap(protocol ->
                Arrays.stream(Gesture.values()).map(gesture -> Arguments.of(protocol, gesture)));
    }

    static Stream<Arguments> unavailablePairs() {
        return Arrays.stream(new int[]{685, 2193}).boxed().flatMap(protocol ->
                Stream.of("cold", "missing", "nonreciprocal", "closed", "distant")
                        .map(state -> Arguments.of(protocol, state)));
    }

    static Stream<Arguments> reentrantRefusals() {
        return Arrays.stream(new int[]{685, 2193}).boxed().flatMap(protocol ->
                Stream.of(Gesture.RIGHT_CLICK, Gesture.START_BREAK).flatMap(gesture ->
                        Stream.of("world", "unload", "replace")
                                .map(change -> Arguments.of(protocol, gesture, change))));
    }

    static Stream<Arguments> floodedContainers() {
        return Stream.of(Arguments.of(685), Arguments.of(2193));
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void refusalRestoresContainerThenItsPublicActorNbt(int protocol, boolean creative,
                                                       int blockId, Gesture gesture) throws Exception {
        Fixture f = new Fixture(protocol, creative, blockId);
        f.act(gesture);
        f.assertVisuals(List.of(f.target));
        f.assertReadOnly();
    }

    @ParameterizedTest
    @MethodSource("acceptedClicks")
    void acceptedClickDoesNotSendRollback(int protocol, boolean creative, int blockId) throws Exception {
        Fixture f = new Fixture(protocol, creative, blockId);
        when(f.level.useItemOn(any(Vector3.class), any(Item.class), eq(BlockFace.UP),
                anyFloat(), anyFloat(), anyFloat(), same(f.player))).thenReturn(f.held);
        f.act(Gesture.RIGHT_CLICK);
        assertTrue(f.packets.isEmpty(), "Successful interaction must not generate refusal packets");
        f.assertReadOnly();
    }

    @ParameterizedTest
    @MethodSource("doubleChests")
    void loadedReciprocalPairAcrossChunkBorderRestoresBothActorsAfterAllBlocks(
            int protocol, Gesture gesture) throws Exception {
        Fixture f = new Fixture(protocol, true, BlockID.CHEST);
        BlockEntityChest pair = f.addPair(16, true);
        f.act(gesture);
        f.assertVisuals(List.of(f.target, pair));
        f.assertReadOnly();
    }

    @ParameterizedTest
    @MethodSource("unavailablePairs")
    void refusalDoesNotLoadRepairOrRebindUnavailablePair(int protocol, String state) throws Exception {
        Fixture f = new Fixture(protocol, true, BlockID.CHEST);
        int pairX = state.equals("distant") ? 100 : 16;
        BlockEntityChest pair = f.addPair(pairX, !state.equals("nonreciprocal"));
        if (state.equals("cold")) f.chunks.remove(1);
        if (state.equals("missing")) f.actors.remove(key(16, 64, 0));
        if (state.equals("closed")) pair.closed = true;
        f.act(Gesture.RIGHT_CLICK);
        f.assertVisuals(List.of(f.target));
        assertFalse(f.packets.stream().anyMatch(packet -> packet instanceof UpdateBlockPacket b && b.x == pairX),
                "Unavailable or stale pair must not receive speculative block updates");
        f.assertReadOnly();
    }

    @ParameterizedTest
    @MethodSource("reentrantRefusals")
    void packetCallbackInvalidatingWorldOrChunkStopsRemainingCorrection(
            int protocol, Gesture gesture, String change) throws Exception {
        Fixture f = new Fixture(protocol, true, BlockID.CHEST);
        f.addPair(16, true);
        f.onFirstBlock = packet -> {
            switch (change) {
                case "world" -> f.player.level = mock(Level.class);
                case "unload" -> f.chunks.remove(0);
                case "replace" -> f.chunks.put(0, mock(BaseFullChunk.class));
                default -> throw new AssertionError(change);
            }
        };

        f.act(gesture);

        assertEquals(1, f.packets.size(), "Callback invalidated the origin; no later block/actor may be sent");
        assertInstanceOf(UpdateBlockPacket.class, f.packets.get(0));
        assertTrue(f.firstBlockCallbackRan, "Test must reach the native correction packet");
        f.assertReadOnly();
    }

    @ParameterizedTest
    @MethodSource("floodedContainers")
    void refusalPreservesRealWaterLayerAndStorage(int protocol) throws Exception {
        Fixture f = new Fixture(protocol, false, BlockID.CHEST);
        Block water = Block.get(BlockID.WATER, 0);
        water.x = 15;
        water.y = 64;
        water.level = f.level;
        water.layer = 1;
        f.liquids.put(key(water), water);

        f.act(Gesture.RIGHT_CLICK);

        f.assertVisuals(List.of(f.target));
        assertTrue(f.packets.stream().anyMatch(packet -> packet instanceof UpdateBlockPacket b
                && b.x == 15 && b.y == 64 && b.z == 0 && b.dataLayer == 1
                && b.blockRuntimeId == GlobalBlockPalette.getOrCreateRuntimeId(f.version, BlockID.WATER, 0)),
                "Water must be restored from the actual layer, not replaced with air");
        assertSame(water, f.liquids.get(key(water)));
        f.assertReadOnly();
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final PluginManager plugins = mock(PluginManager.class);
        final Item held = Item.get(Item.STONE, 0, 7);
        final Item initialHeld = held.clone();
        final GameVersion version;
        final Map<String, Block> blocks = new HashMap<>();
        final Map<String, Block> liquids = new HashMap<>();
        final Map<String, BlockEntitySpawnable> actors = new HashMap<>();
        final Map<Integer, BaseFullChunk> chunks = new HashMap<>();
        final Map<BlockEntitySpawnable, CompoundTag> before = new LinkedHashMap<>();
        final List<DataPacket> packets = new ArrayList<>();
        final BlockEntitySpawnable target;
        Map<String, String> storageBefore;
        Consumer<DataPacket> onFirstBlock;
        boolean firstBlockCallbackRan;

        Fixture(int protocol, boolean creative, int blockId) throws Exception {
            version = GameVersion.byProtocol(protocol, false);
            Server server = mock(Server.class);
            server.doNotLimitInteractions = true;
            when(server.getPluginManager()).thenReturn(plugins);
            when(player.getServer()).thenReturn(server);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getLevel()).thenAnswer(call -> player.level);
            when(player.getGameVersion()).thenReturn(version);
            when(player.isAlive()).thenReturn(true);
            when(player.isCreative()).thenReturn(creative);
            when(player.isSurvival()).thenReturn(!creative);
            when(player.canInteract(any(Vector3.class), anyDouble())).thenReturn(true);
            when(inventory.getItemInHand()).thenReturn(held);
            when(inventory.getItemInHandFast()).thenReturn(held);
            set(player, "server", server);
            set(player, "inventory", inventory);
            set(player, "lastBreakPosition", new BlockVector3());
            player.level = level;
            player.x = 15;
            player.y = 64;
            player.spawned = true;
            player.protocol = protocol;
            doCallRealMethod().when(player).handleInventoryTransactionPacket(any());
            doAnswer(call -> {
                DataPacket packet = ((DataPacket) call.getArgument(0)).clone();
                packet.protocol = protocol;
                packet.gameVersion = version;
                packet.encode();
                packets.add(packet);
                if (packet instanceof UpdateBlockPacket && onFirstBlock != null && !firstBlockCallbackRan) {
                    firstBlockCallbackRan = true;
                    onFirstBlock.accept(packet);
                }
                return true;
            }).when(player).dataPacket(any(DataPacket.class));
            doAnswer(call -> { ((PlayerInteractEvent) call.getArgument(0)).setCancelled(); return null; })
                    .when(plugins).callEvent(any(PlayerInteractEvent.class));

            when(level.isYInRange(anyInt())).thenReturn(true);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(call ->
                    ((int) call.getArgument(1)) == 0 ? chunks.get((int) call.getArgument(0)) : null);
            doCallRealMethod().when(level).getBlockEntityIfLoaded(any(Vector3.class));
            doCallRealMethod().when(level).getBlockEntityIfLoaded(nullable(FullChunk.class), any(Vector3.class));
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(call -> actors.get(key(call.getArgument(0))));
            when(level.getBlock(any(Vector3.class))).thenAnswer(call -> block(call.getArgument(0), 0));
            when(level.getBlock(any(Vector3.class), anyBoolean())).thenAnswer(call -> block(call.getArgument(0), 0));
            when(level.getBlock(any(Vector3.class), anyInt(), anyBoolean())).thenAnswer(call ->
                    block(call.getArgument(0), call.getArgument(1)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call ->
                    block(new Vector3((int) call.getArgument(0), (int) call.getArgument(1), (int) call.getArgument(2)), call.getArgument(3)));
            when(level.getBlock(any(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), eq(false))).thenAnswer(call ->
                    block(new Vector3((int) call.getArgument(1), (int) call.getArgument(2), (int) call.getArgument(3)), call.getArgument(4)));
            when(level.getBlockRuntimeId(any(GameVersion.class), anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(call -> {
                        Block b = block(new Vector3((int) call.getArgument(1), (int) call.getArgument(2), (int) call.getArgument(3)),
                                call.getArgument(4));
                        return GlobalBlockPalette.getOrCreateRuntimeId(call.getArgument(0), b.getId(), b.getDamage());
                    });
            doCallRealMethod().when(level).sendBlocks(any(Player[].class), any(Vector3[].class), anyInt());
            doCallRealMethod().when(level).sendBlocks(any(Player[].class), any(Vector3[].class), anyInt(), anyInt());
            doCallRealMethod().when(level).sendBlocks(any(Player[].class), any(Vector3[].class), anyInt(), anyInt(), anyBoolean());
            target = addActor(blockId, 15);
            snapshot();
        }

        private Block block(Vector3 pos, int layer) {
            Block existing = (layer == 0 ? blocks : liquids).get(key(pos));
            int id = existing != null ? existing.getId() : BlockID.AIR;
            int meta = existing != null ? existing.getDamage() : 0;
            Block result = mock(Block.class, CALLS_REAL_METHODS);
            result.x = pos.x;
            result.y = pos.y;
            result.z = pos.z;
            result.level = level;
            result.layer = layer;
            doReturn(id).when(result).getId();
            doReturn(meta).when(result).getDamage();
            doAnswer(call -> block(pos.getSide(call.getArgument(0)), layer)).when(result).getSide(any(BlockFace.class));
            doAnswer(call -> block(pos, call.getArgument(0))).when(result).getLevelBlockAtLayer(anyInt());
            return result;
        }

        private BlockEntitySpawnable addActor(int blockId, int x) {
            Block b = Block.get(blockId, blockId == BlockID.SHULKER_BOX ? 1 : 2);
            b.x = x;
            b.y = 64;
            b.level = level;
            blocks.put(key(b), b);
            BlockEntitySpawnable actor = blockId == BlockID.CHEST ? mock(BlockEntityChest.class, CALLS_REAL_METHODS)
                    : blockId == BlockID.SHULKER_BOX ? mock(BlockEntityShulkerBox.class, CALLS_REAL_METHODS)
                    : mock(BlockEntityEnderChest.class, CALLS_REAL_METHODS);
            actor.x = x;
            actor.y = 64;
            actor.level = level;
            actor.namedTag = new CompoundTag().putInt("x", x).putInt("y", 64).putInt("z", 0)
                    .putByte("facing", 1).putString("CustomName", "Private container")
                    .putList(new ListTag<CompoundTag>("Items").add(new CompoundTag()
                            .putByte("Slot", 0).putShort("id", Item.DIAMOND).putByte("Count", 23)));
            actors.put(key(actor), actor);
            BaseFullChunk chunk = chunks.computeIfAbsent(x >> 4, chunkX -> {
                BaseFullChunk value = mock(BaseFullChunk.class);
                when(value.getX()).thenReturn(chunkX);
                when(value.getZ()).thenReturn(0);
                when(value.getTile(anyInt(), anyInt(), anyInt())).thenAnswer(call -> actors.get(
                        key((chunkX << 4) + (int) call.getArgument(0), call.getArgument(1), call.getArgument(2))));
                return value;
            });
            actor.chunk = chunk;
            return actor;
        }

        BlockEntityChest addPair(int x, boolean reciprocal) {
            BlockEntityChest pair = (BlockEntityChest) addActor(BlockID.CHEST, x);
            target.namedTag.putInt("pairx", x).putInt("pairz", 0).putBoolean("pairlead", true);
            pair.namedTag.putInt("pairx", reciprocal ? 15 : 17).putInt("pairz", 0).putBoolean("pairlead", false);
            snapshot();
            return pair;
        }

        private void snapshot() {
            for (BlockEntitySpawnable actor : actors.values()) before.put(actor, actor.namedTag.copy());
        }

        void act(Gesture gesture) throws Exception {
            storageBefore = storageSnapshot();
            if (gesture == Gesture.START_BREAK) {
                invoke(player, "onBlockBreakStart", new Class<?>[]{Vector3.class, BlockFace.class},
                        new Vector3(15, 64, 0), BlockFace.UP);
            } else if (gesture == Gesture.COMPLETE_BREAK) {
                invoke(player, "onBlockBreakComplete", new Class<?>[]{BlockVector3.class, BlockFace.class},
                        new BlockVector3(15, 64, 0), BlockFace.UP);
            } else {
                UseItemData data = new UseItemData();
                data.actionType = gesture == Gesture.RIGHT_CLICK ? InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_BLOCK
                        : InventoryTransactionPacket.USE_ITEM_ACTION_BREAK_BLOCK;
                data.blockPos = new BlockVector3(15, 64, 0);
                data.face = BlockFace.UP;
                data.clickPos = new Vector3f();
                data.itemInHand = held.clone();
                InventoryTransactionPacket packet = new InventoryTransactionPacket();
                packet.protocol = version.getProtocol();
                packet.transactionType = InventoryTransactionPacket.TYPE_USE_ITEM;
                packet.transactionData = data;
                packet.actions = new NetworkInventoryAction[0];
                player.handleInventoryTransactionPacket(packet);
            }
        }

        void assertVisuals(List<BlockEntitySpawnable> expected) throws Exception {
            List<BlockEntityDataPacket> actorPackets = packets.stream().filter(BlockEntityDataPacket.class::isInstance)
                    .map(BlockEntityDataPacket.class::cast).toList();
            assertEquals(expected.size(), actorPackets.size(), "Every live container needs its public actor NBT");
            int lastBlock = -1;
            int firstActor = Integer.MAX_VALUE;
            for (int i = 0; i < packets.size(); i++) {
                DataPacket packet = packets.get(i);
                if (packet instanceof UpdateBlockPacket b) {
                    lastBlock = i;
                    BinaryStream wire = new BinaryStream(b.getBuffer());
                    assertEquals(ProtocolInfo.UPDATE_BLOCK_PACKET & 0xff, wire.getUnsignedVarInt());
                    assertEquals(new BlockVector3(b.x, b.y, b.z), wire.getBlockVector3(version));
                    assertEquals(b.blockRuntimeId, wire.getUnsignedVarInt());
                    assertEquals(b.flags, wire.getUnsignedVarInt());
                    assertEquals(b.dataLayer, wire.getUnsignedVarInt());
                    assertEquals(0, wire.readableBytes());
                    if (b.dataLayer == 1) {
                        Block liquid = liquids.get(key(b.x, b.y, b.z));
                        int runtime = GlobalBlockPalette.getOrCreateRuntimeId(version,
                                liquid == null ? BlockID.AIR : liquid.getId(), liquid == null ? 0 : liquid.getDamage());
                        assertEquals(runtime, b.blockRuntimeId, "Liquid layer must match authoritative storage");
                    }
                } else if (packet instanceof BlockEntityDataPacket) firstActor = Math.min(firstActor, i);
            }
            assertTrue(lastBlock >= 0 && lastBlock < firstActor, "All block layers must precede actor NBT");
            for (BlockEntitySpawnable actor : expected) {
                Block b = blocks.get(key(actor));
                for (int layer : new int[]{0, 1}) {
                    Block stored = layer == 0 ? b : liquids.get(key(actor));
                    int runtime = GlobalBlockPalette.getOrCreateRuntimeId(version,
                            stored == null ? BlockID.AIR : stored.getId(), stored == null ? 0 : stored.getDamage());
                    assertTrue(packets.stream().anyMatch(packet -> packet instanceof UpdateBlockPacket update
                            && update.x == actor.x && update.y == actor.y && update.z == actor.z
                            && update.dataLayer == layer && update.blockRuntimeId == runtime),
                            "Missing authoritative layer " + layer + " at " + key(actor));
                }
                BlockEntityDataPacket packet = actorPackets.stream().filter(p -> p.x == actor.x && p.y == actor.y && p.z == actor.z)
                        .findFirst().orElseThrow();
                BinaryStream wire = new BinaryStream(packet.getBuffer());
                assertEquals(ProtocolInfo.BLOCK_ENTITY_DATA_PACKET & 0xff, wire.getUnsignedVarInt());
                assertEquals(new BlockVector3(packet.x, packet.y, packet.z), wire.getBlockVector3(version));
                CompoundTag spawn = NBTIO.read(wire.get(), ByteOrder.LITTLE_ENDIAN, true);
                assertEquals(actor.getSpawnCompound(version.getProtocol()), spawn);
                assertFalse(spawn.contains("Items"), "A refused viewer must not receive stored items");
                assertEquals((int) actor.x, spawn.getInt("x"));
                assertEquals(64, spawn.getInt("y"));
                if (actor instanceof BlockEntityShulkerBox) assertEquals(1, spawn.getByte("facing"));
            }
        }

        void assertReadOnly() {
            assertEquals(storageBefore, storageSnapshot(), "Block or liquid storage changed on visual repair");
            assertTrue(held.equalsExact(initialHeld), "Held stack changed on visual repair");
            verify(inventory, never()).setItemInHand(any(Item.class));
            assertFalse(packets.stream().anyMatch(p -> p instanceof ContainerOpenPacket || p instanceof InventoryContentPacket),
                    "Visual repair must not open or publish a container inventory");
            for (var entry : before.entrySet()) {
                assertEquals(entry.getValue(), entry.getKey().namedTag, "Stored items/pairing metadata changed");
                if (entry.getKey() instanceof BlockEntityChest chest) verify(chest, never()).getInventory();
                if (entry.getKey() instanceof BlockEntityShulkerBox shulker) verify(shulker, never()).getInventory();
            }
            verify(level, never()).getBlockEntity(any(Vector3.class));
            verify(level, never()).getChunk(anyInt(), anyInt());
            verify(level, never()).getChunk(anyInt(), anyInt(), eq(true));
            verify(level, never()).getBlock(any(Vector3.class), eq(true));
            verify(level, never()).getBlock(any(Vector3.class), anyInt(), eq(true));
        }

        private Map<String, String> storageSnapshot() {
            Map<String, String> snapshot = new HashMap<>();
            blocks.forEach((pos, block) -> snapshot.put("0:" + pos, block.getId() + ":" + block.getDamage()));
            liquids.forEach((pos, block) -> snapshot.put("1:" + pos, block.getId() + ":" + block.getDamage()));
            return snapshot;
        }
    }

    private static String key(Vector3 pos) { return key(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()); }
    private static String key(int x, int y, int z) { return x + ":" + y + ":" + z; }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = Player.class; type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void invoke(Player player, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = Player.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            method.invoke(player, arguments);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            if (e.getCause() instanceof Error cause) throw cause;
            throw e;
        }
    }
}
