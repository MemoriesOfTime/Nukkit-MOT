package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteOrder;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlowFrameSpawnReplayTest {
    @BeforeAll
    static void boot() {
        MockServer.init();
        BlockEntity.registerBlockEntity(BlockEntity.ITEM_FRAME, BlockEntityItemFrame.class);
        BlockEntity.registerBlockEntity(BlockEntity.GLOW_ITEM_FRAME, BlockEntityItemFrameGlow.class);
    }

    static Stream<Arguments> frames() {
        return ProtocolInfo.SUPPORTED_PROTOCOLS.stream()
                .filter(p -> p >= 589 && p <= ProtocolInfo.CURRENT_PROTOCOL).distinct()
                .flatMap(protocol -> Stream.of(false, true).flatMap(glow -> Stream.of(false, true)
                        .map(filled -> Arguments.of(protocol, glow, filled))));
    }

    @ParameterizedTest(name = "protocol={0}, glow={1}, filled={2}")
    @MethodSource("frames")
    void savedFrameReplaysCorrectTypeAndItemOverNetwork(int protocol, boolean glow, boolean filled) throws Exception {
        BaseChunk chunk = mock(BaseChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        Level level = mock(Level.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        when(level.getServer()).thenReturn(MockServer.get());

        String id = glow ? BlockEntity.GLOW_ITEM_FRAME : BlockEntity.ITEM_FRAME;
        CompoundTag source = new CompoundTag().putString("id", id)
                .putInt("x", 2).putInt("y", 64).putInt("z", 3).putByte("ItemRotation", 3);
        if (filled) {
            Item diamond = Item.get(Item.DIAMOND, 0, 7);
            diamond.setNamedTag(new CompoundTag().putString("opaque", "qa1107-sentinel")
                    .putCompound("nested", new CompoundTag().putInt("receipt", 51)));
            source.putCompound("Item", NBTIO.putItemHelper(diamond));
        }
        BlockEntityItemFrame frame = glow ? new BlockEntityItemFrameGlow(chunk, source)
                : new BlockEntityItemFrame(chunk, source);
        frame.saveNBT();
        byte[] persisted = NBTIO.write(frame.namedTag, ByteOrder.LITTLE_ENDIAN);
        CompoundTag saved = NBTIO.read(persisted, ByteOrder.LITTLE_ENDIAN);
        assertEquals(id, saved.getString("id"));
        BlockEntityItemFrame restored = assertInstanceOf(BlockEntityItemFrame.class,
                BlockEntity.createBlockEntity(saved.getString("id"), chunk, saved));
        assertEquals(glow, restored instanceof BlockEntityItemFrameGlow);
        if (filled) assertEquals(7, restored.getItem().getCount());
        else assertTrue(restored.getItem().isNull(), "Empty native air item retains its existing count semantics");
        assertEquals(3, restored.getItemRotation());
        CompoundTag beforeReplay = restored.namedTag.copy();

        byte[] wireBytes = NetworkChunkSerializer.serializeBlockEntities(List.of(restored), protocol);
        CompoundTag wire = NBTIO.read(wireBytes, ByteOrder.LITTLE_ENDIAN, true);
        assertEquals(id, wire.getString("id"), "Network tile type must match the saved frame type");
        assertTrue(wire.contains("Item"));
        if (!filled) {
            CompoundTag empty = wire.getCompound("Item");
            assertEquals("minecraft:air", empty.getString("Name"));
            assertTrue(NBTIO.getItemHelper(empty.copy()).isNull());
            assertEquals(beforeReplay.getCompound("Item").getByte("Count"), empty.getByte("Count"));
            assertEquals(3, wire.getByte("ItemRotation"));
        }
        if (filled) {
            assertEquals("minecraft:diamond", wire.getCompound("Item").getString("Name"));
            assertEquals(7, wire.getCompound("Item").getByte("Count"));
            assertEquals(3, wire.getByte("ItemRotation"));
            assertEquals("qa1107-sentinel", wire.getCompound("Item").getCompound("tag").getString("opaque"));
            wire.getCompound("Item").getCompound("tag").getCompound("nested").putInt("receipt", 99);
        }
        assertEquals(beforeReplay, restored.namedTag, "Wire NBT belongs to the packet, not the saved item");
    }
}
