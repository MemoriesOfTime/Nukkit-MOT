package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多版本真实配方包严格解析回归测试.
 * <p>
 * 背景: BlastFurnaceRecipe.getType() 返回 BLAST_FURNACE/BLAST_FURNACE_DATA,
 * 曾导致 354~2167 版本编码时只写入裸类型 varint 而无配方体, 客户端解析错位崩溃.
 * 本测试逐字节解析各版本真实配方包, 保证每个条目都有完整配体且流被恰好消费完.
 * <p>
 * Strict-parse regression test for real crafting packets across protocol versions:
 * every entry must carry a complete body and the stream must be consumed exactly.
 */
public class CraftingDataPacketCompatibilityTest {

    private static CraftingManager manager;

    @BeforeAll
    static void init() {
        MockServer.init();
        manager = new CraftingManager();
    }

    static Stream<GameVersion> versions() {
        return Stream.of(
                GameVersion.V1_19_60,  // 567: 有 recipeId/priority/networkId, 无 assumeSymmetry/requirement/trim
                GameVersion.V1_20_50,  // 630: +trim
                GameVersion.V1_20_80,  // 671: +assumeSymmetry
                GameVersion.V1_21_0,   // 685: +requirement (仅 SHAPELESS/SHAPED, SHULKER_BOX 尚无)
                // 关键: 685~729 区间(1.21.0~1.21.30)SHULKER_BOX 不携带 requirement 字段;
                // 748(1.21.40)起才与 SHAPELESS 一致。补全此区间各临界点以防字节错位回归。
                GameVersion.V1_21_2,   // 686: requirement 仅 SHAPELESS (曾因 SHULKER_BOX 多写 1 字节致客户端解码崩溃)
                GameVersion.V1_21_20,  // 712
                GameVersion.V1_21_30,  // 729
                GameVersion.V1_21_40,  // 748: SHULKER_BOX 开始携带 requirement
                GameVersion.V1_21_50,  // 766
                GameVersion.V1_21_130, // 898
                GameVersion.V1_26_0,   // 924
                GameVersion.V1_26_10,  // 944
                GameVersion.V1_26_20,  // 975: 熔炉族改走 SHAPELESS+tag
                GameVersion.V1_26_30   // 1001
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versions")
    void craftingPacketParsesFully(GameVersion gv) throws Exception {
        Method m = CraftingManager.class.getDeclaredMethod("packetFor", GameVersion.class);
        m.setAccessible(true);
        BatchPacket bp = (BatchPacket) m.invoke(manager, gv);
        byte[] raw = Zlib.inflateRaw(bp.payload, 64 * 1024 * 1024);

        BinaryStream batch = new BinaryStream(raw);
        int packetLen = (int) batch.getUnsignedVarInt();
        byte[] packet = batch.get(packetLen);

        int protocol = gv.getProtocol();
        boolean hasAssumeSymmetry = protocol >= ProtocolInfo.v1_20_80;

        BinaryStream ps = new BinaryStream(packet);
        int header = (int) ps.getUnsignedVarInt();
        assertEquals(ProtocolInfo.CRAFTING_DATA_PACKET & 0xff, header & 0x3ff);

        Map<String, Integer> tagCount = new HashMap<>();
        Set<Integer> netIds = new HashSet<>();

        int total = (int) ps.getUnsignedVarInt();
        for (int i = 0; i < total; i++) {
            int type = ps.getVarInt();
            switch (type) {
                case 0: // SHAPELESS (975 起熔炉族配方以 SHAPELESS + tag 下发)
                case 5: { // SHULKER_BOX
                    ps.getString(); // recipeId
                    int ingCount = (int) ps.getUnsignedVarInt();
                    for (int j = 0; j < ingCount; j++) {
                        readIngredient(ps);
                    }
                    int resCount = (int) ps.getUnsignedVarInt();
                    for (int j = 0; j < resCount; j++) {
                        skipInstanceSlot(ps);
                    }
                    ps.getUUID();
                    String tag = ps.getString();
                    ps.getVarInt(); // priority
                    // 真实协议: requirement 字段 SHAPELESS 自 v1_21_0(685) 起携带;
                    // SHULKER_BOX 自 v1_21_40(748) 起才携带, 之前不携带。
                    // 若对 SHULKER_BOX 提前读取, 会与编码端的多写字节相互抵消,
                    // 掩盖客户端实际按协议跳过该字段所导致的流错位。
                    // <p>
                    // Real protocol: requirement is present for SHAPELESS since v1_21_0(685),
                    // but only since v1_21_40(748) for SHULKER_BOX. Reading it early for
                    // SHULKER_BOX would cancel the encoder's extra byte and hide the stream
                    // misalignment that real clients (which skip it per protocol) suffer.
                    if (hasRequirement(protocol, type)) {
                        int ctx = ps.getByte();
                        if (ctx == 0) { // UnlockingContext.NONE -> 附带材料数组
                            int n = (int) ps.getUnsignedVarInt();
                            for (int j = 0; j < n; j++) {
                                readIngredient(ps);
                            }
                        }
                    }
                    int netId = (int) ps.getUnsignedVarInt();
                    assertTrue(netIds.add(netId), "duplicate recipe networkId " + netId + " at entry " + i);
                    tagCount.merge(tag, 1, Integer::sum);
                    break;
                }
                case 1: { // SHAPED
                    ps.getString();
                    int w = ps.getVarInt();
                    int h = ps.getVarInt();
                    for (int j = 0; j < w * h; j++) {
                        readIngredient(ps);
                    }
                    int resCount = (int) ps.getUnsignedVarInt();
                    for (int j = 0; j < resCount; j++) {
                        skipInstanceSlot(ps);
                    }
                    ps.getUUID();
                    ps.getString(); // tag
                    ps.getVarInt(); // priority
                    if (hasAssumeSymmetry) {
                        ps.getBoolean();
                    }
                    if (hasRequirement(protocol, 1 /* SHAPED */)) {
                        int ctx = ps.getByte();
                        if (ctx == 0) {
                            int n = (int) ps.getUnsignedVarInt();
                            for (int j = 0; j < n; j++) {
                                readIngredient(ps);
                            }
                        }
                    }
                    ps.getUnsignedVarInt(); // networkId
                    break;
                }
                case 2: // FURNACE legacy (< v1_26_20_26): input + result + tag
                case 3: { // FURNACE_DATA legacy: 多一个 damage varint
                    ps.getVarInt(); // input runtimeId
                    if (type == 3) {
                        ps.getVarInt(); // input damage
                    }
                    skipInstanceSlot(ps);
                    String tag = ps.getString();
                    tagCount.merge(tag, 1, Integer::sum);
                    break;
                }
                case 4: // MULTI
                    ps.getUUID();
                    ps.getUnsignedVarInt();
                    break;
                case 8: { // SMITHING_TRANSFORM
                    ps.getString();
                    readIngredient(ps);
                    readIngredient(ps);
                    readIngredient(ps);
                    skipInstanceSlot(ps);
                    ps.getString();
                    ps.getUnsignedVarInt();
                    break;
                }
                case 9: { // SMITHING_TRIM
                    ps.getString();
                    for (int j = 0; j < 3; j++) {
                        assertEquals(3, ps.getByte(), "trim ingredient must be a tag descriptor");
                        ps.getString();
                        ps.getVarInt(); // count
                    }
                    ps.getString();
                    ps.getUnsignedVarInt();
                    break;
                }
                default:
                    fail("entry " + i + " has bare recipe network type " + type
                            + " (no body writer) — would desync the client");
            }
        }

        // potion mixes (407+: 6 个 varint)
        int potions = (int) ps.getUnsignedVarInt();
        for (int i = 0; i < potions; i++) {
            ps.getVarInt();
            ps.getVarInt();
            ps.getVarInt();
            ps.getVarInt();
            ps.getVarInt();
            ps.getVarInt();
        }
        // container mixes
        int containers = (int) ps.getUnsignedVarInt();
        for (int i = 0; i < containers; i++) {
            ps.getVarInt();
            ps.getVarInt();
            ps.getVarInt();
        }
        // material reducers (v1_17_30+)
        assertEquals(0, (int) ps.getUnsignedVarInt(), "material reducers must be empty");
        // cleanRecipes
        ps.getBoolean();

        assertEquals(packet.length, ps.getOffset(), "packet must be consumed exactly");

        assertTrue(tagCount.getOrDefault("furnace", 0) > 0, "furnace recipes must be sent");
        assertTrue(tagCount.getOrDefault("blast_furnace", 0) > 0, "blast furnace recipes must be sent");
        assertTrue(tagCount.getOrDefault("smoker", 0) > 0, "smoker recipes must be sent");
        if (protocol == ProtocolInfo.v1_26_20) {
            assertEquals(62, tagCount.get("blast_furnace"), "all blast furnace recipes on 26.20");
            assertEquals(9, tagCount.get("smoker"), "all smoker recipes on 26.20");
        }
    }

    /**
     * 指定协议版本下, 该配方网络类型是否在 wire 上携带 unlocking requirement 字段.
     * <p>
     * 反映真实客户端协议 (对照 CloudburstMC CraftingDataSerializer):
     * SHAPELESS/SHAPED 自 v1_21_0(685) 起, SHULKER_BOX 自 v1_21_40(748) 起;
     * 早于此版本对 SHULKER_BOX 写入该字段会导致客户端解码字节错位。
     * <p>
     * Whether a given recipe network type carries the unlocking requirement field on the wire
     * at this protocol version, mirroring real client protocol (CloudburstMC CraftingDataSerializer).
     */
    private static boolean hasRequirement(int protocol, int networkType) {
        if (protocol < ProtocolInfo.v1_21_0) {
            return false;
        }
        // SHULKER_BOX (5): v1_21_40(748) 起才携带 requirement
        if (networkType == 5) {
            return protocol >= ProtocolInfo.v1_21_40;
        }
        // SHAPELESS(0) / SHAPED(1): v1_21_0(685) 起携带
        return true;
    }

    // instanceItem=true 的 slot: varint runtimeId(0 即止) + lshort count + uvarint damage + varint blockRuntimeId + byteArray userData
    private static void skipInstanceSlot(BinaryStream ps) {
        int runtimeId = ps.getVarInt();
        if (runtimeId == 0) {
            return;
        }
        ps.getLShort();
        ps.getUnsignedVarInt();
        ps.getVarInt();
        ps.getByteArray();
    }

    private static void readIngredient(BinaryStream ps) {
        int type = ps.getByte() & 0xff;
        if (type == 1) {
            ps.getLShort();
            ps.getLShort();
        } else if (type == 3) {
            ps.getString();
        } else if (type != 0) {
            fail("unexpected ingredient descriptor type " + type);
        }
        ps.getVarInt(); // count
    }
}
