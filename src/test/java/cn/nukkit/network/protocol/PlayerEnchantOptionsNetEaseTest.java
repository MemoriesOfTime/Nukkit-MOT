package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket.EnchantData;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket.EnchantOptionData;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the NetEase {@link PlayerEnchantOptionsPacket} wire format.
 * 网易客户端的附魔选项包与标准协议不兼容：每个附魔条目多一个 modEnchantIdentifier 字符串，
 * 且每个选项多一个第四组自定义附魔列表。发送标准格式会导致网易客户端反序列化失败并断开。
 * <p>
 * The NetEase enchant-options packet diverges from standard Bedrock: every enchant
 * entry carries an extra modEnchantIdentifier string, and every option carries a
 * fourth custom-enchantments list. Emitting the standard format made NetEase clients
 * fail deserialization and disconnect the moment a book was placed in the table.
 */
class PlayerEnchantOptionsNetEaseTest {

    private static final GameVersion NETEASE = GameVersion.V1_21_124_NETEASE;
    private static final GameVersion STANDARD = GameVersion.V1_21_124;

    private static EnchantOptionData sampleOption() {
        // Two enchantments in the first group so the per-entry NetEase overhead is
        // exercised more than once.
        List<EnchantData> group = List.of(
                new EnchantData(0, 1),
                new EnchantData(7, 2)
        );
        return new EnchantOptionData(
                3, 0,
                group,
                Collections.emptyList(),
                Collections.emptyList(),
                "fortune", 1
        );
    }

    /**
     * The exact repro: NetEase encode must be longer than standard encode by the
     * extra per-entry strings plus the fourth list header.
     */
    @Test
    void netEaseEncodingIsLongerThanStandard() {
        byte[] standard = encode(sampleOption(), STANDARD);
        byte[] netease = encode(sampleOption(), NETEASE);

        // NetEase payload = standard payload + 2 empty strings (one per enchant entry)
        // + 1 empty list header for the fourth group.
        // An empty string is a single VarUInt length byte (0x00), and an empty list
        // header is a single VarUInt count byte (0x00) → 3 extra bytes here.
        assertEquals(standard.length + 3, netease.length,
                "NetEase payload should carry 2 modEnchantIdentifier strings + 1 empty fourth list");
    }

    /**
     * NetEase uses the VarUInt minLevel encoding (same as standard v1_21_124),
     * not the byte encoding introduced at v1_26_20_26. With minLevel=200 the
     * VarUInt form is 2 bytes (0xC8 0x01); a byte form would be a single 0xC8.
     * <p>
     * The only NetEase-vs-standard length delta with no enchantments is the
     * extra fourth empty-list header byte.
     */
    @Test
    void netEaseMinLevelIsVarUIntLikeStandard1_21_124() {
        EnchantOptionData option = new EnchantOptionData(
                200, 0, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), "x", 1
        );
        byte[] standardBody = encode(option, STANDARD);
        byte[] neteaseBody = encode(option, NETEASE);

        assertEquals(standardBody.length + 1, neteaseBody.length,
                "NetEase adds exactly one empty fourth-list header byte");
    }

    /**
     * NetEase round-trips through encode/decode, preserving the custom list and
     * the modEnchantIdentifier strings (decoded as empty for vanilla enchants).
     */
    @Test
    void netEaseRoundTripPreservesStructure() {
        List<EnchantData> group = List.of(new EnchantData(9, 4, "minecraft:sharpness"));
        EnchantOptionData original = new EnchantOptionData(
                1, 2, group, Collections.emptyList(), Collections.emptyList(),
                List.of(new EnchantData(1, 1)), "thorns", 42
        );

        PlayerEnchantOptionsPacket encoded = new PlayerEnchantOptionsPacket();
        encoded.gameVersion = NETEASE;
        encoded.protocol = NETEASE.getProtocol();
        encoded.options.add(original);
        encoded.tryEncode();

        PlayerEnchantOptionsPacket decoded = new PlayerEnchantOptionsPacket();
        decoded.gameVersion = NETEASE;
        decoded.protocol = NETEASE.getProtocol();
        decoded.setBuffer(encoded.getBuffer());
        // Skip the header written by reset(): pid 0x92 (=146) VarUInt-encodes to
        // 2 bytes (0x92 0x01), so the body starts at offset 2.
        decoded.setOffset(2);
        decoded.decode();

        assertEquals(1, decoded.options.size());
        EnchantOptionData out = decoded.options.get(0);
        assertEquals(original.getMinLevel(), out.getMinLevel());
        assertEquals(original.getPrimarySlot(), out.getPrimarySlot());
        assertEquals(1, out.getEnchants0().size());
        EnchantData ed = out.getEnchants0().get(0);
        assertEquals(9, ed.getType());
        assertEquals(4, ed.getLevel());
        assertEquals("minecraft:sharpness", ed.getModEnchantIdentifier());
        assertEquals(1, out.getEnchantsCustom().size());
        assertEquals("thorns", out.getEnchantName());
        assertEquals(42, out.getEnchantNetId());
    }

    /**
     * Standard v1_21_124 round-trip must remain unchanged: no fourth list, no
     * modEnchantIdentifier on entries, and the field stays empty after decode.
     */
    @Test
    void standardRoundTripHasNoNetEaseExtras() {
        EnchantOptionData original = sampleOption();

        PlayerEnchantOptionsPacket encoded = new PlayerEnchantOptionsPacket();
        encoded.gameVersion = STANDARD;
        encoded.protocol = STANDARD.getProtocol();
        encoded.options.add(original);
        encoded.tryEncode();

        PlayerEnchantOptionsPacket decoded = new PlayerEnchantOptionsPacket();
        decoded.gameVersion = STANDARD;
        decoded.protocol = STANDARD.getProtocol();
        decoded.setBuffer(encoded.getBuffer());
        decoded.setOffset(1);
        decoded.decode();

        EnchantOptionData out = decoded.options.get(0);
        assertTrue(out.getEnchantsCustom().isEmpty(), "Standard decode must yield no custom list");
        for (EnchantData ed : out.getEnchants0()) {
            assertEquals("", ed.getModEnchantIdentifier(),
                    "Standard decode must not populate the NetEase-only identifier");
        }
    }

    /**
     * Backwards-compatible EnchantOptionData constructor keeps existing call sites
     * working and defaults the NetEase-only fields to empty.
     */
    @Test
    void legacyConstructorDefaultsNetEaseFields() {
        EnchantOptionData option = new EnchantOptionData(
                1, 0, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), "name", 5
        );
        assertTrue(option.getEnchantsCustom().isEmpty());
    }

    private static byte[] encode(EnchantOptionData option, GameVersion version) {
        PlayerEnchantOptionsPacket pk = new PlayerEnchantOptionsPacket();
        pk.gameVersion = version;
        pk.protocol = version.getProtocol();
        pk.options.add(option);
        pk.tryEncode();
        return pk.getBuffer();
    }
}
