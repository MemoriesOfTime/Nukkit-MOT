package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDoor;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A refused prediction must restore visible blocks without changing the server's world. */
class PlayerCancelledBlockVisualTest {
    private static Player player(Level level, int protocol) throws Exception {
        Player player = mock(Player.class);
        Server server = mock(Server.class);
        server.doNotLimitInteractions = true;
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItemInHand()).thenReturn(Item.get(Item.STONE));
        when(player.getInventory()).thenReturn(inventory);
        when(player.isAlive()).thenReturn(true);
        when(player.isCreative()).thenReturn(true);
        set(player, "server", server);
        set(player, "inventory", inventory);
        player.level = level;
        player.y = 64;
        player.spawned = true;
        player.protocol = protocol;
        doCallRealMethod().when(player).handleInventoryTransactionPacket(any());
        return player;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = Player.class; type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static InventoryTransactionPacket click(int protocol) {
        return click(protocol, new BlockVector3(0, 64, 0));
    }

    private static InventoryTransactionPacket click(int protocol, BlockVector3 blockPos) {
        UseItemData data = new UseItemData();
        data.actionType = InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_BLOCK;
        data.blockPos = blockPos;
        data.face = BlockFace.UP;
        data.clickPos = new Vector3f();
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.protocol = protocol;
        packet.transactionType = InventoryTransactionPacket.TYPE_USE_ITEM;
        packet.transactionData = data;
        packet.actions = new NetworkInventoryAction[0];
        return packet;
    }

    @ParameterizedTest
    @CsvSource({"786,false", "786,true", "2193,false", "2193,true"})
    void deniedClickRebuildsBothLayersAndDoorBottom(int protocol, boolean upperDoor) throws Exception {
        Level level = mock(Level.class);
        Player player = player(level, protocol);
        Block target = upperDoor ? mock(BlockDoor.class) : mock(Block.class);
        Block adjacent = mock(Block.class);
        Block bottom = mock(Block.class);
        Block liquid = mock(Block.class);
        when(target.getSide(BlockFace.UP)).thenReturn(adjacent);
        when(target.getLevelBlockAtLayer(1)).thenReturn(liquid);
        when(adjacent.getLevelBlockAtLayer(1)).thenReturn(liquid);
        when(bottom.getLevelBlockAtLayer(1)).thenReturn(liquid);
        when(level.getBlock(any(Vector3.class))).thenReturn(target);
        if (upperDoor) {
            when(target.getDamage()).thenReturn(8);
            when(target.getId()).thenReturn(Block.WOODEN_DOOR_BLOCK);
            when(bottom.getId()).thenReturn(Block.WOODEN_DOOR_BLOCK);
            when(target.down()).thenReturn(bottom);
        }
        player.handleInventoryTransactionPacket(click(protocol));
        verify(level).useItemOn(any(Vector3.class), any(Item.class), eq(BlockFace.UP),
                anyFloat(), anyFloat(), anyFloat(), same(player));
        verify(level).sendBlocks(any(Player[].class), eq(new Block[]{target, adjacent}),
                eq(UpdateBlockPacket.FLAG_ALL_PRIORITY));
        verify(level).sendBlocks(any(Player[].class), eq(new Block[]{liquid, liquid}),
                eq(UpdateBlockPacket.FLAG_ALL_PRIORITY), eq(1));
        if (upperDoor) {
            verify(level).sendBlocks(any(Player[].class), eq(new Block[]{bottom}),
                    eq(UpdateBlockPacket.FLAG_ALL_PRIORITY));
            verify(level).sendBlocks(any(Player[].class), eq(new Block[]{liquid}),
                    eq(UpdateBlockPacket.FLAG_ALL_PRIORITY), eq(1));
        }
        verify(level, never()).sendBlocks(any(Player[].class), any(Vector3[].class), eq(UpdateBlockPacket.FLAG_NOGRAPHIC));
    }

    @ParameterizedTest
    @ValueSource(ints = {786, 2193})
    void deniedClickResyncsBlockEntityAtBothSentPositions(int protocol) throws Exception {
        Level level = mock(Level.class);
        Player player = player(level, protocol);
        Block target = mock(Block.class);
        Block adjacent = mock(Block.class);
        Block liquid = mock(Block.class);
        when(target.getSide(BlockFace.UP)).thenReturn(adjacent);
        when(target.getLevelBlockAtLayer(1)).thenReturn(liquid);
        when(adjacent.getLevelBlockAtLayer(1)).thenReturn(liquid);
        when(level.getBlock(any(Vector3.class))).thenReturn(target);
        BlockEntitySpawnable targetTile = mock(BlockEntitySpawnable.class);
        BlockEntitySpawnable adjacentTile = mock(BlockEntitySpawnable.class);
        when(level.getBlockEntity(new Vector3(3, 64, 5))).thenReturn(targetTile);
        when(level.getBlockEntity(new Vector3(3, 65, 5))).thenReturn(adjacentTile);
        player.handleInventoryTransactionPacket(click(protocol, new BlockVector3(3, 64, 5)));
        InOrder order = inOrder(level, targetTile, adjacentTile);
        order.verify(level).sendBlocks(any(Player[].class), eq(new Block[]{target, adjacent}),
                eq(UpdateBlockPacket.FLAG_ALL_PRIORITY));
        order.verify(targetTile).spawnTo(player);
        order.verify(adjacentTile).spawnTo(player);
    }

    @ParameterizedTest
    @ValueSource(ints = {786, 2193})
    void successfulClickKeepsNoRollback(int protocol) throws Exception {
        Level level = mock(Level.class);
        Player player = player(level, protocol);
        when(level.useItemOn(any(Vector3.class), any(Item.class), any(BlockFace.class),
                anyFloat(), anyFloat(), anyFloat(), same(player))).thenReturn(Item.get(Item.STONE));
        player.handleInventoryTransactionPacket(click(protocol));
        verify(level, never()).sendBlocks(any(Player[].class), any(Vector3[].class), anyInt());
        verify(level, never()).sendBlocks(any(Player[].class), any(Vector3[].class), anyInt(), anyInt());
    }

    private static java.util.stream.Stream<Integer> supportedProtocols() {
        return java.util.Arrays.stream(GameVersion.values())
                .filter(version -> !version.isNetEase()
                        && version.getProtocol() >= ProtocolInfo.v1_20_0
                        && version.getProtocol() <= ProtocolInfo.v1_26_50)
                .map(GameVersion::getProtocol).distinct();
    }

    @ParameterizedTest
    @MethodSource("supportedProtocols")
    void priorityFlagsAndBothLayersUseExistingWireFields(int protocol) {
        for (int layer : new int[]{0, 1}) {
            UpdateBlockPacket packet = new UpdateBlockPacket();
            packet.protocol = protocol;
            packet.gameVersion = GameVersion.byProtocol(protocol, false);
            packet.x = -3;
            packet.y = 64;
            packet.z = 5;
            packet.blockRuntimeId = 7;
            packet.flags = UpdateBlockPacket.FLAG_ALL_PRIORITY;
            packet.dataLayer = layer;
            packet.encode();
            BinaryStream stream = new BinaryStream(packet.getBuffer());
            assertEquals(ProtocolInfo.UPDATE_BLOCK_PACKET & 0xff, stream.getUnsignedVarInt());
            assertEquals(new BlockVector3(-3, 64, 5), stream.getBlockVector3(packet.gameVersion));
            assertEquals(7, stream.getUnsignedVarInt());
            assertEquals(11, stream.getUnsignedVarInt());
            assertEquals(layer, stream.getUnsignedVarInt());
            assertEquals(0, stream.readableBytes());
        }
    }
}
