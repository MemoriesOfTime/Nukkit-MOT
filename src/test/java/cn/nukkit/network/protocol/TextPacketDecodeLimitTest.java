package cn.nukkit.network.protocol;

import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 TextPacket 解码阶段对超长字符串的拒绝（伪造超长包的作弊客户端）。
 * <p>
 * Verifies TextPacket inbound decode rejects over-length strings from cheating clients; the thrown
 * {@link IllegalArgumentException} triggers the existing "malformed packet" disconnect path.
 */
public class TextPacketDecodeLimitTest {

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
}
