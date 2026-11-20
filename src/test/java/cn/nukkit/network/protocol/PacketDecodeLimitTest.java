package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.netease.SyncSkinPacket;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证解码阶段对超长字符串（BossEventPacket title / TextPacket 消息）与超大集合的拒绝（伪造超长包的作弊客户端）。
 * <p>
 * Verifies inbound decode rejects over-length strings (BossEventPacket titles, TextPacket messages)
 * and oversized collection counts from cheating clients; the thrown {@link IllegalArgumentException}
 * triggers the existing "malformed packet" disconnect path.
 */
class PacketDecodeLimitTest {

    private static final int PROTO_V1_26_30 = GameVersion.V1_26_30.getProtocol();
    private static final int PROTO_LEGACY = 137; // v1_2_0

    // Unsigned VarInt 0x20000000: an ArrayList capacity that requests roughly 2 GiB
    // of references on a 64-bit JVM before the decoder reads any element bytes.
    private static final byte[] TWO_GIB_ITEM_COUNT = {
            (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x02
    };

    @Test
    public void modernBranchRejectsOverLengthTitle() {
        BinaryStream s = new BinaryStream();
        s.putEntityUniqueId(1);            // bossEid
        s.putEntityUniqueId(2);            // playerEid
        s.putByte((byte) BossEventPacket.TYPE_SHOW); // type
        s.putString(repeat("x", 257));     // title over the 256-byte cap
        // remaining fields not reached — decode throws at the oversized title read
        BossEventPacket pk = new BossEventPacket();
        pk.protocol = PROTO_V1_26_30;
        pk.setBuffer(s.getBuffer());
        assertThrows(IllegalArgumentException.class, pk::decode,
                "modern branch must reject an over-length title at decode time");
    }

    @Test
    public void modernBranchRejectsOverLengthFilteredTitle() {
        BinaryStream s = new BinaryStream();
        s.putEntityUniqueId(1);
        s.putEntityUniqueId(2);
        s.putByte((byte) BossEventPacket.TYPE_SHOW);
        s.putString("ok");                 // title within limit
        s.putString(repeat("f", 257));     // filteredTitle over the cap
        BossEventPacket pk = new BossEventPacket();
        pk.protocol = PROTO_V1_26_30;
        pk.setBuffer(s.getBuffer());
        assertThrows(IllegalArgumentException.class, pk::decode,
                "modern branch must reject an over-length filteredTitle at decode time");
    }

    @Test
    public void legacyShowBranchRejectsOverLengthTitle() {
        BinaryStream s = new BinaryStream();
        s.putEntityUniqueId(1);            // bossEid
        s.putUnsignedVarInt(BossEventPacket.TYPE_SHOW); // type
        s.putString(repeat("x", 257));     // title over the cap
        BossEventPacket pk = new BossEventPacket();
        pk.protocol = PROTO_LEGACY;
        pk.setBuffer(s.getBuffer());
        assertThrows(IllegalArgumentException.class, pk::decode,
                "legacy TYPE_SHOW must reject an over-length title at decode time");
    }

    @Test
    public void legacyTitleBranchRejectsOverLengthTitle() {
        BinaryStream s = new BinaryStream();
        s.putEntityUniqueId(1);            // bossEid
        s.putUnsignedVarInt(BossEventPacket.TYPE_TITLE); // type
        s.putString(repeat("t", 257));     // title over the cap
        BossEventPacket pk = new BossEventPacket();
        pk.protocol = PROTO_LEGACY;
        pk.setBuffer(s.getBuffer());
        assertThrows(IllegalArgumentException.class, pk::decode,
                "legacy TYPE_TITLE must reject an over-length title at decode time");
    }

    @Test
    public void decodeAcceptsTitleAtLimit() {
        String title = repeat("a", 256);
        BossEventPacket pk = buildModernShow(title, "");
        pk.decode();
        assertEquals(title, pk.title);
    }

    @Test
    public void decodeAccepts256CjkChars() {
        // 关键用例：256 个中文字符 = 256 code points（在 256 字符上限内），但其 UTF-8 字节为 768。
        // 按字符语义应通过；若误按字节限则会被误截/误拒。
        // Key case: 256 CJK chars = 256 code points (within the 256-char limit) but 768 UTF-8 bytes.
        // Under character semantics this passes; a mistaken byte limit would reject/truncate it.
        String title = repeat("好", 256);
        BossEventPacket pk = buildModernShow(title, "");
        pk.decode();
        assertEquals(title, pk.title);
    }

    private static BossEventPacket buildModernShow(String title, String filteredTitle) {
        BinaryStream s = new BinaryStream();
        s.putEntityUniqueId(1);
        s.putEntityUniqueId(2);
        s.putByte((byte) BossEventPacket.TYPE_SHOW);
        s.putString(title);
        s.putString(filteredTitle);        // filteredTitle
        s.putLFloat(1.0f);                 // healthPercent
        s.putByte((byte) 0);               // color
        s.putByte((byte) 0);               // overlay
        BossEventPacket pk = new BossEventPacket();
        pk.protocol = PROTO_V1_26_30;
        pk.setBuffer(s.getBuffer());
        return pk;
    }

    @Test
    public void boundedGetStringAcceptsAtLimit() {
        BinaryStream s = new BinaryStream();
        String exactly = repeat("a", 256);
        s.putString(exactly);
        assertEquals(exactly, s.getString(256));
    }

    @Test
    public void boundedGetStringRejectsOverLimit() {
        BinaryStream s = new BinaryStream();
        // 257 code points > 256 cap. Must throw before returning the oversized value.
        s.putString(repeat("b", 257));
        assertThrows(IllegalArgumentException.class, () -> s.getString(256));
    }

    @Test
    public void boundedGetStringCountsCodePointsNotBytes() {
        BinaryStream s = new BinaryStream();
        // 每个中文字符是 1 个 code point（3 字节）；6 字符 = 6 code points ≤ 16 cap，应通过。
        // Each CJK char is 1 code point (3 bytes); 6 code points ≤ 16 cap, should pass.
        s.putString("你好你好你好");
        assertEquals("你好你好你好", s.getString(16));
    }

    @Test
    public void decodeRejectsOverLengthMessage() {
        TextPacket pk = buildLegacyMessageOnly(repeat("x", 65537), 137 /* v1_2_0 */);
        assertThrows(IllegalArgumentException.class, pk::decode,
                "over-length chat message must be rejected at decode time");
    }

    @Test
    public void decodeRejectsOverLengthSource() {
        // Legacy AuthorAndMessage (TYPE_CHAT): source then message, with legacy fields gated off by protocol.
        BinaryStream s = new BinaryStream();
        s.putByte(TextPacket.TYPE_CHAT);
        s.putBoolean(false); // isLocalized, present since v1_2_0
        s.putString(repeat("p", 257)); // source over the 256 cap
        s.putString("hi"); // message (short)
        // protocol < 223 so no xboxUserId / platformChatId / filteredMessage trailer
        TextPacket pk = new TextPacket();
        pk.protocol = 137;
        pk.setBuffer(s.getBuffer());
        assertThrows(IllegalArgumentException.class, pk::decode,
                "over-length chat source must be rejected at decode time");
    }

    @Test
    public void decodeAcceptsValidMessageAtLimit() {
        String msg = repeat("y", 65536);
        TextPacket pk = buildLegacyMessageOnly(msg, 137);
        pk.decode();
        assertEquals(msg, pk.message);
    }

    private static TextPacket buildLegacyMessageOnly(String message, int protocol) {
        BinaryStream s = new BinaryStream();
        s.putByte(TextPacket.TYPE_RAW);
        s.putBoolean(false); // isLocalized
        s.putString(message);
        TextPacket pk = new TextPacket();
        pk.protocol = protocol;
        pk.setBuffer(s.getBuffer());
        return pk;
    }

    private static String repeat(String unit, int times) {
        StringBuilder b = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            b.append(unit);
        }
        return b.toString();
    }

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
