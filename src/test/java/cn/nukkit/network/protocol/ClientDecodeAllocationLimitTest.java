package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;
import cn.nukkit.network.protocol.netease.SyncSkinPacket;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientDecodeAllocationLimitTest {

    // Unsigned VarInt 0x20000000: an ArrayList capacity that requests roughly 2 GiB
    // of references on a 64-bit JVM before the decoder reads any element bytes.
    private static final byte[] TWO_GIB_ITEM_COUNT = {
            (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x02
    };

    @Test
    void cameraSplineRejectsOversizedTopLevelCountBeforeAllocating() {
        CameraSplinePacket packet = new CameraSplinePacket();
        packet.setBuffer(TWO_GIB_ITEM_COUNT);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void voxelShapesRejectsOversizedTopLevelCountBeforeAllocating() {
        VoxelShapesPacket packet = new VoxelShapesPacket();
        packet.setBuffer(TWO_GIB_ITEM_COUNT);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void cameraAimAssistRejectsOversizedPriorityCountBeforeAllocating() {
        CameraAimAssistActorPriorityPacket packet = new CameraAimAssistActorPriorityPacket();
        packet.setBuffer(TWO_GIB_ITEM_COUNT);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void textureShiftRejectsOversizedStepCountBeforeAllocating() {
        BinaryStream stream = new BinaryStream();
        stream.putByte((byte) 0);
        stream.putString("");
        stream.putString("");
        stream.putString("");
        stream.put(TWO_GIB_ITEM_COUNT);

        ClientboundTextureShiftPacket packet = new ClientboundTextureShiftPacket();
        packet.setBuffer(stream.getBuffer());

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void itemStackRequestRejectsTooManyRequestsBeforeBuildingObjects() {
        BinaryStream stream = new BinaryStream();
        stream.putUnsignedVarInt(129);

        ItemStackRequestPacket packet = itemStackRequestPacket(stream);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void itemStackRequestRejectsTooManyActionsBeforeBuildingObjects() {
        BinaryStream stream = new BinaryStream();
        stream.putUnsignedVarInt(1);
        stream.putVarInt(0);
        stream.putUnsignedVarInt(129);

        ItemStackRequestPacket packet = itemStackRequestPacket(stream);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void itemStackRequestRejectsTooManyFilteredStringsBeforeBuildingStrings() {
        BinaryStream stream = new BinaryStream();
        stream.putUnsignedVarInt(1);
        stream.putVarInt(0);
        stream.putUnsignedVarInt(0);
        stream.putUnsignedVarInt(129);

        ItemStackRequestPacket packet = itemStackRequestPacket(stream);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void itemStackRequestRejectsTooManyRecipeIngredientsBeforeBuildingObjects() {
        BinaryStream stream = new BinaryStream();
        stream.putUnsignedVarInt(1);
        stream.putVarInt(0);
        stream.putUnsignedVarInt(1);
        stream.putUnsignedVarInt(11); // v1.26.40+ compact CRAFT_RECIPE_AUTO action id
        stream.putByte((byte) 11); // duplicate type byte
        stream.putUnsignedVarInt(0); // recipe id
        stream.putByte((byte) 1); // requested crafts
        stream.putUnsignedVarInt(129);

        ItemStackRequestPacket packet = itemStackRequestPacket(stream);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void itemStackRequestRejectsTooManyCraftResultsBeforeBuildingObjects() {
        BinaryStream stream = new BinaryStream();
        stream.putUnsignedVarInt(1);
        stream.putVarInt(0);
        stream.putUnsignedVarInt(1);
        stream.putUnsignedVarInt(17); // v1.26.40+ compact CRAFT_RESULTS action id
        stream.putByte((byte) 17); // duplicate type byte
        stream.putUnsignedVarInt(129);

        ItemStackRequestPacket packet = itemStackRequestPacket(stream);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    private static ItemStackRequestPacket itemStackRequestPacket(BinaryStream stream) {
        ItemStackRequestPacket packet = new ItemStackRequestPacket();
        packet.protocol = ProtocolInfo.v1_26_50;
        packet.gameVersion = GameVersion.byProtocol(packet.protocol, false);
        packet.setBuffer(stream.getBuffer());
        return packet;
    }

    @Test
    void netEaseSkinRejectsOversizedEntryCountBeforeAllocating() {
        SyncSkinPacket packet = new SyncSkinPacket();
        packet.setBuffer(TWO_GIB_ITEM_COUNT);

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void bookEditRejectsOversizedPageBeforeBuildingString() {
        BinaryStream stream = new BinaryStream();
        stream.putVarInt(0);
        stream.putUnsignedVarInt(BookEditPacket.Action.ADD_PAGE.ordinal());
        stream.putVarInt(0);
        stream.putString("x".repeat(257));

        BookEditPacket packet = new BookEditPacket();
        packet.protocol = ProtocolInfo.v1_26_0;
        packet.gameVersion = GameVersion.byProtocol(packet.protocol, false);
        packet.setBuffer(stream.getBuffer());

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void legacySubChunkRequestRejectsTooManyOffsetsBeforeBuildingObjects() {
        BinaryStream stream = new BinaryStream();
        stream.putVarInt(0);
        stream.putSignedBlockPosition(new BlockVector3(0, 0, 0));
        stream.putLInt(8193);

        SubChunkRequestPacket packet = new SubChunkRequestPacket();
        packet.protocol = ProtocolInfo.v1_20_0;
        packet.gameVersion = GameVersion.byProtocol(packet.protocol, false);
        packet.setBuffer(stream.getBuffer());

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void modernSubChunkRequestRejectsUnsignedOverflowBeforeBuildingObjects() {
        BinaryStream stream = new BinaryStream();
        stream.putVarInt(0);
        stream.putUnsignedVarInt(0xFFFFFFFFL);

        SubChunkRequestPacket packet = new SubChunkRequestPacket();
        packet.protocol = ProtocolInfo.v1_26_30;
        packet.gameVersion = GameVersion.byProtocol(packet.protocol, false);
        packet.setBuffer(stream.getBuffer());

        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void mapInfoRejectsMorePixelsThanOneVanillaMap() {
        BinaryStream stream = new BinaryStream();
        stream.putVarLong(0);
        stream.putLInt(128 * 128 + 1);

        MapInfoRequestPacket packet = new MapInfoRequestPacket();
        packet.protocol = ProtocolInfo.v1_19_20;
        packet.gameVersion = GameVersion.byProtocol(packet.protocol, false);
        packet.setBuffer(stream.getBuffer());

        assertThrows(IllegalArgumentException.class, packet::decode);
    }
}
