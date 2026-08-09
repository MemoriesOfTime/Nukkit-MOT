package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 BossEventPacket 解码阶段对超长 title 的拒绝（伪造包的作弊客户端）。
 * <p>
 * Verifies BossEventPacket inbound decode rejects over-length titles from cheating clients; the
 * thrown {@link IllegalArgumentException} triggers the existing "malformed packet" disconnect path.
 */
public class BossEventDecodeLimitTest {

    private static final int PROTO_V1_26_30 = GameVersion.V1_26_30.getProtocol();
    private static final int PROTO_LEGACY = 137; // v1_2_0

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

    private static String repeat(String unit, int times) {
        StringBuilder b = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            b.append(unit);
        }
        return b.toString();
    }
}
