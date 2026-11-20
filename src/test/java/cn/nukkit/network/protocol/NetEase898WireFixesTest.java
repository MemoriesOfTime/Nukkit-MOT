package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网易 898 wire 修复回归：Text 网易参数表上限 12、512 截断 workaround、
 * SetPlayerInventoryOptions 尾部 varint 仅 860 写。
 * <p>
 * Regression tests for the NetEase 898 wire fixes: Text NetEase parameter list cap of 12,
 * the 512-char truncation workaround, and the SetPlayerInventoryOptions trailing varint
 * being 860-only.
 */
public class NetEase898WireFixesTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    private static TextPacket decodeNetEaseV898Chat(BinaryStream s) {
        TextPacket pk = new TextPacket();
        pk.protocol = ProtocolInfo.v1_21_130;
        pk.gameVersion = GameVersion.V1_21_130_NETEASE;
        pk.setBuffer(s.getBuffer());
        pk.decode();
        return pk;
    }

    /** decode expects the body only; reset() wrote an unsigned-varint packet id header. */
    private static TextPacket roundTrip(TextPacket encoded, GameVersion gameVersion, int protocol) {
        BinaryStream temp = new BinaryStream(encoded.getBuffer());
        temp.getUnsignedVarInt();
        TextPacket pk = new TextPacket();
        pk.protocol = protocol;
        pk.gameVersion = gameVersion;
        pk.setBuffer(temp.getBuffer(), temp.getOffset());
        pk.decode();
        return pk;
    }

    private static BinaryStream netEaseV898ChatFrame(String source, String message, int paramCount) {
        BinaryStream s = new BinaryStream();
        s.putBoolean(false); // isLocalized
        s.putByte((byte) 1); // AuthorAndMessage
        s.putString("chat");
        s.putString("whisper");
        s.putString("announcement");
        s.putByte(TextPacket.TYPE_CHAT);
        s.putString(source);
        s.putString(message);
        s.putUnsignedVarInt(paramCount);
        for (int i = 0; i < paramCount; i++) {
            s.putString("p" + i);
        }
        s.putString(""); // xboxUserId
        s.putString(""); // platformChatId
        s.putBoolean(false); // no filteredMessage
        return s;
    }

    @Test
    public void netEaseChatAcceptsTwelveParameters() {
        TextPacket pk = assertDoesNotThrow(() -> decodeNetEaseV898Chat(netEaseV898ChatFrame("tester", "hello", 12)));
        assertEquals(12, pk.parameters.length, "12 entries fit the NetEase AuthorAndMessage MaxItems");
    }

    @Test
    public void netEaseChatRejectsThirteenParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> decodeNetEaseV898Chat(netEaseV898ChatFrame("tester", "hello", 13)),
                "13 entries exceed the NetEase AuthorAndMessage MaxItems of 12");
    }

    @Test
    public void netEaseEncodeTruncatesLongChat() {
        TextPacket pk = new TextPacket();
        pk.protocol = ProtocolInfo.v1_21_130;
        pk.gameVersion = GameVersion.V1_21_130_NETEASE;
        pk.type = TextPacket.TYPE_CHAT;
        pk.source = "tester"; // 6 chars
        pk.message = "x".repeat(600);
        pk.encode();

        TextPacket decoded = roundTrip(pk, GameVersion.V1_21_130_NETEASE, ProtocolInfo.v1_21_130);
        assertEquals(505, decoded.message.length(),
                "message must be truncated to 511 - source length (client 512 limit)");
    }

    @Test
    public void standardEncodeKeepsLongChat() {
        TextPacket pk = new TextPacket();
        pk.protocol = ProtocolInfo.v1_21_130;
        pk.gameVersion = GameVersion.V1_21_130;
        pk.type = TextPacket.TYPE_CHAT;
        pk.source = "tester";
        pk.message = "x".repeat(600);
        pk.encode();

        TextPacket decoded = roundTrip(pk, GameVersion.V1_21_130, ProtocolInfo.v1_21_130);
        assertEquals(600, decoded.message.length(), "no truncation outside NetEase");
    }

    @Test
    public void netEaseEncodeTruncatesMessageOnlyBody() {
        TextPacket pk = new TextPacket();
        pk.protocol = ProtocolInfo.v1_21_130;
        pk.gameVersion = GameVersion.V1_21_130_NETEASE;
        pk.type = TextPacket.TYPE_TIP; // MessageOnly body, no source on the wire
        pk.message = "x".repeat(600);
        pk.encode();

        TextPacket decoded = roundTrip(pk, GameVersion.V1_21_130_NETEASE, ProtocolInfo.v1_21_130);
        assertEquals(511, decoded.message.length(), "MessageOnly caps at 511 with empty source");
    }

    @Test
    public void netEaseLegacyEncodeTruncatesLongChat() {
        TextPacket pk = new TextPacket();
        pk.protocol = ProtocolInfo.v1_21_124; // 860: legacy Text layout
        pk.gameVersion = GameVersion.V1_21_124_NETEASE;
        pk.type = TextPacket.TYPE_CHAT;
        pk.source = "tester";
        pk.message = "x".repeat(600);
        pk.encode();

        TextPacket decoded = roundTrip(pk, GameVersion.V1_21_124_NETEASE, ProtocolInfo.v1_21_124);
        assertEquals(505, decoded.message.length(), "legacy-format NetEase chat is truncated too");
    }

    @Test
    public void inventoryOptionsTailOnlyOn860() {
        byte[] v860 = encodeInventoryOptions(GameVersion.V1_21_124_NETEASE, ProtocolInfo.v1_21_124);
        byte[] v898 = encodeInventoryOptions(GameVersion.V1_21_130_NETEASE, ProtocolInfo.v1_21_130);
        byte[] standard = encodeInventoryOptions(GameVersion.V1_21_130, ProtocolInfo.v1_21_130);

        assertEquals(v898.length, standard.length, "898 NetEase writes no trailing varint");
        assertEquals(v898.length + 1, v860.length, "860 NetEase carries the trailing varint byte");
    }

    private static byte[] encodeInventoryOptions(GameVersion gameVersion, int protocol) {
        SetPlayerInventoryOptionsPacket pk = new SetPlayerInventoryOptionsPacket();
        pk.protocol = protocol;
        pk.gameVersion = gameVersion;
        pk.leftTab = cn.nukkit.network.protocol.types.inventory.InventoryTabLeft.NONE;
        pk.rightTab = cn.nukkit.network.protocol.types.inventory.InventoryTabRight.NONE;
        pk.filtering = false;
        pk.layout = cn.nukkit.network.protocol.types.inventory.InventoryLayout.NONE;
        pk.craftingLayout = cn.nukkit.network.protocol.types.inventory.InventoryLayout.NONE;
        pk.encode();
        assertTrue(pk.getBuffer().length > 0);
        return pk.getBuffer();
    }
}
