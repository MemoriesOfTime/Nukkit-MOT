package cn.nukkit.blockentity;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemFrameSpawnReadOnlyTest {
    @BeforeAll
    static void boot() {
        MockServer.init();
    }

    static Stream<Integer> protocols() {
        return ProtocolInfo.SUPPORTED_PROTOCOLS.stream()
                .filter(p -> p >= ProtocolInfo.v1_20_0 && p <= ProtocolInfo.CURRENT_PROTOCOL)
                .distinct();
    }

    @ParameterizedTest
    @MethodSource("protocols")
    void missingItemKeepsAirFallbackPacketWithoutWorldWrites(int protocol) {
        BlockEntityItemFrame frame = frame(new CompoundTag().putByte("ItemRotation", 3));
        CompoundTag before = frame.namedTag.copy();
        CompoundTag airItem = new CompoundTag("Item").putByte("Count", 1)
                .putShort("Damage", 0).putString("Name", "minecraft:air");
        CompoundTag expected = new CompoundTag().putString("id", BlockEntity.ITEM_FRAME)
                .putInt("x", 2).putInt("y", 64).putInt("z", 3)
                .putCompound("Item", airItem).putByte("ItemRotation", 3);
        CompoundTag packet = frame.getSpawnCompound(protocol);

        assertEquals(expected, packet);
        assertTrue(packet.contains("Item"));
        assertEquals(3, packet.getByte("ItemRotation"));
        assertEquals(before, frame.namedTag, "Network serialization must not repair persisted NBT");
        verifyNoInteractions(frame.level);
        verify(frame, never()).setItem(any(Item.class), anyBoolean());
    }

    @ParameterizedTest
    @MethodSource("protocols")
    void populatedFrameCopiesPacketMetadataWithoutMutatingSavedItem(int protocol) {
        Item item = Item.get(Item.DIAMOND, 0, 1);
        item.setNamedTag(new CompoundTag().putString("opaque", "keep")
                .putCompound("nested", new CompoundTag().putInt("value", 7)));
        BlockEntityItemFrame frame = frame(new CompoundTag().putByte("ItemRotation", 5)
                .putCompound("Item", NBTIO.putItemHelper(item)));
        CompoundTag before = frame.namedTag.copy();
        CompoundTag packet = frame.getSpawnCompound(protocol);

        assertTrue(packet.contains("Item"));
        assertEquals(5, packet.getByte("ItemRotation"));
        packet.getCompound("Item").getCompound("tag").getCompound("nested").putInt("value", 99);
        assertEquals(before, frame.namedTag);
        verifyNoInteractions(frame.level);
    }

    private static BlockEntityItemFrame frame(CompoundTag tag) {
        BlockEntityItemFrame frame = mock(BlockEntityItemFrame.class, CALLS_REAL_METHODS);
        frame.namedTag = tag;
        frame.level = mock(Level.class);
        frame.x = 2;
        frame.y = 64;
        frame.z = 3;
        return frame;
    }
}
