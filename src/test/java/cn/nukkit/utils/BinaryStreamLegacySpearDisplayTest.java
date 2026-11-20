package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.LittleEndianByteBufInputStream;
import cn.nukkit.network.protocol.ProtocolInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.nio.ByteOrder;
import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BinaryStreamLegacySpearDisplayTest {
    private static final String[][] PAIRS = {
            {"wooden_spear", "wooden_sword"},
            {"stone_spear", "stone_sword"},
            {"copper_spear", "copper_sword"},
            {"iron_spear", "iron_sword"},
            {"golden_spear", "golden_sword"},
            {"diamond_spear", "diamond_sword"},
            {"netherite_spear", "netherite_sword"}
    };

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    record Projection(GameVersion version, String spear, String sword, boolean descriptor, boolean nbt, int count) {
        String expected() {
            int protocol = version.getProtocol();
            if (protocol < ProtocolInfo.v1_20_0) return "minecraft:info_update";
            if (protocol >= ProtocolInfo.v1_21_130_28) return "minecraft:" + spear;
            return "minecraft:" + (spear.equals("copper_spear") && protocol < ProtocolInfo.v1_21_100
                    ? "iron_sword" : sword);
        }
    }

    static Stream<GameVersion> versions() {
        return Stream.of(GameVersion.values()).filter(v -> !v.isNetEase()
                && (v.getProtocol() >= ProtocolInfo.v1_20_0 || v.getProtocol() == 582)
                && v.getProtocol() <= ProtocolInfo.v1_26_50);
    }

    static Stream<Projection> projections() {
        List<Projection> cases = new ArrayList<>();
        versions().forEach(version -> {
            for (String[] pair : PAIRS) for (boolean descriptor : new boolean[]{false, true})
                for (boolean nbt : new boolean[]{false, true}) for (int count : new int[]{1, 3})
                    cases.add(new Projection(version, pair[0], pair[1], descriptor, nbt, count));
        });
        return cases.stream();
    }

    private static Item original(String key, int count, boolean nbt) {
        Item item = Item.fromString(key);
        item.setCount(count);
        item.setDamage(7);
        if (nbt) {
            item.setNamedTag(new CompoundTag().putString("opaque", "C6")
                    .putCompound("nested", new CompoundTag().putInt("value", 29)));
            item.setCustomName("C6 original");
        }
        return item;
    }

    private static byte[] write(GameVersion version, Item item, boolean descriptor) {
        BinaryStream output = new BinaryStream();
        if (descriptor) output.putNetworkItemStackDescriptor(version, item.clone());
        else output.putSlot(version, item.clone());
        return output.getBuffer();
    }

    private static Item read(GameVersion version, byte[] bytes, boolean descriptor) {
        BinaryStream input = new BinaryStream(bytes);
        return descriptor ? input.getNetworkItemStackDescriptor(version) : input.getSlot(version);
    }

    private static String wireNamespace(GameVersion version, byte[] bytes, boolean descriptor) {
        BinaryStream input = new BinaryStream(bytes);
        int threshold = descriptor ? ProtocolInfo.v1_26_20_26 : ProtocolInfo.v1_26_40;
        int runtimeId = version.getProtocol() >= threshold ? input.getLShort() : input.getVarInt();
        return RuntimeItems.getMapping(version).getNamespacedIdByNetworkId(runtimeId);
    }

    @ParameterizedTest
    @MethodSource("projections")
    void displaysSupportedItemAndRestoresOriginalSpear(Projection scenario) {
        Item item = original("minecraft:" + scenario.spear(), scenario.count(), scenario.nbt());
        byte[] bytes = write(scenario.version(), item, scenario.descriptor());
        assertEquals(scenario.expected(), wireNamespace(scenario.version(), bytes, scenario.descriptor()));
        Item restored = read(scenario.version(), bytes, scenario.descriptor());
        assertAll(
                () -> assertEquals(item.getNamespaceId(), restored.getNamespaceId()),
                () -> assertEquals(item.getCount(), restored.getCount()),
                () -> assertEquals(item.getDamage(), restored.getDamage()),
                () -> assertEquals(item.getNamedTag(), restored.getNamedTag()));
    }

    private static CompoundTag originTags() {
        return new CompoundTag().putInt("mv_origin_id", 255).putInt("mv_origin_meta", 7)
                .putString("mv_origin_namespace", "minecraft:diamond_spear")
                .putCompound("mv_origin_nbt", new CompoundTag().putString("opaque", "C6"));
    }

    record Rejected(String reason, int protocol, String shown, CompoundTag tags, boolean descriptor) {}

    static Stream<Rejected> rejectedAliases() {
        List<Rejected> cases = new ArrayList<>();
        for (boolean descriptor : new boolean[]{false, true}) {
            cases.add(new Rejected("foreign namespace", 818, "minecraft:diamond_sword", originTags().putString("mv_origin_namespace", "upstream:test_spear"), descriptor));
            cases.add(new Rejected("other vanilla namespace", 818, "minecraft:diamond_sword", originTags().putString("mv_origin_namespace", "minecraft:trident"), descriptor));
            cases.add(new Rejected("wrong sword pair", 818, "minecraft:wooden_sword", originTags(), descriptor));
            cases.add(new Rejected("missing id", 818, "minecraft:diamond_sword", originTags().remove("mv_origin_id"), descriptor));
            cases.add(new Rejected("missing meta", 818, "minecraft:diamond_sword", originTags().remove("mv_origin_meta"), descriptor));
            cases.add(new Rejected("missing namespace", 818, "minecraft:diamond_sword", originTags().remove("mv_origin_namespace"), descriptor));
            cases.add(new Rejected("id is string", 818, "minecraft:diamond_sword", originTags().putString("mv_origin_id", "255"), descriptor));
            cases.add(new Rejected("meta is string", 818, "minecraft:diamond_sword", originTags().putString("mv_origin_meta", "7"), descriptor));
            cases.add(new Rejected("namespace is int", 818, "minecraft:diamond_sword", originTags().putInt("mv_origin_namespace", 255), descriptor));
            cases.add(new Rejected("id is not string item", 818, "minecraft:diamond_sword", originTags().putInt("mv_origin_id", 276), descriptor));
            cases.add(new Rejected("broken nested NBT", 818, "minecraft:diamond_sword", originTags().putString("mv_origin_nbt", "bad"), descriptor));
            cases.add(new Rejected("spear already supported", 898, "minecraft:diamond_sword", originTags(), descriptor));
            cases.add(new Rejected("below client window", 582, "minecraft:diamond_sword", originTags(), descriptor));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("rejectedAliases")
    void doesNotRestoreMalformedOrUnrelatedAliases(Rejected scenario) {
        GameVersion version = GameVersion.byProtocol(scenario.protocol(), false);
        Item shown = Item.fromString(scenario.shown());
        shown.setNamedTag(scenario.tags().clone());
        Item restored = read(version, write(version, shown, scenario.descriptor()), scenario.descriptor());
        assertEquals(scenario.shown(), restored.getNamespaceId(), scenario.reason());
    }

    record VersionPath(GameVersion version, boolean descriptor) {}

    static Stream<VersionPath> versionPaths() {
        return versions().flatMap(version -> Stream.of(new VersionPath(version, false), new VersionPath(version, true)));
    }

    @ParameterizedTest
    @MethodSource("versionPaths")
    void keepsTridentAndExistingInfoUpdateRestoration(VersionPath path) {
        Item trident = original("minecraft:trident", 1, true);
        Item restored = read(path.version(), write(path.version(), trident, path.descriptor()), path.descriptor());
        assertAll(
                () -> assertEquals(trident.getNamespaceId(), restored.getNamespaceId()),
                () -> assertEquals(trident.getDamage(), restored.getDamage()),
                () -> assertEquals(trident.getNamedTag(), restored.getNamedTag()));
        Item info = Item.get(Item.INFO_UPDATE, 0, 3);
        info.setNamedTag(originTags());
        Item origin = read(path.version(), write(path.version(), info, path.descriptor()), path.descriptor());
        assertAll(
                () -> assertEquals("minecraft:diamond_spear", origin.getNamespaceId()),
                () -> assertEquals(3, origin.getCount()),
                () -> assertEquals(7, origin.getDamage()),
                () -> assertEquals("C6", origin.getNamedTag().getString("opaque")));
    }

    record Golden(int protocol, String key, String hex) {}

    // Frozen control bytes for unchanged pre-window and native-spear payloads.
    static Stream<Golden> unchangedWire() {
        return Stream.of(
                new Golden(582, "wooden_spear", "f003010000009c0a8501ffff010a0000030e006d765f6f726967696e5f6d65746100000000030c006d765f6f726967696e5f6964ff0000000813006d765f6f726967696e5f6e616d65737061636516006d696e6563726166743a776f6f64656e5f73706561720a0700646973706c61790804004e616d650c00576f6f64656e20537065617200000000000000000000"),
                new Golden(582, "stone_spear", "f003010000009c0a8301ffff010a0000030e006d765f6f726967696e5f6d65746100000000030c006d765f6f726967696e5f6964ff0000000813006d765f6f726967696e5f6e616d65737061636515006d696e6563726166743a73746f6e655f73706561720a0700646973706c61790804004e616d650b0053746f6e6520537065617200000000000000000000"),
                new Golden(582, "copper_spear", "f003010000009c0a8501ffff010a0000030e006d765f6f726967696e5f6d65746100000000030c006d765f6f726967696e5f6964ff0000000813006d765f6f726967696e5f6e616d65737061636516006d696e6563726166743a636f707065725f73706561720a0700646973706c61790804004e616d650c00436f7070657220537065617200000000000000000000"),
                new Golden(582, "iron_spear", "f003010000009c0a8101ffff010a0000030e006d765f6f726967696e5f6d65746100000000030c006d765f6f726967696e5f6964ff0000000813006d765f6f726967696e5f6e616d65737061636514006d696e6563726166743a69726f6e5f73706561720a0700646973706c61790804004e616d650a0049726f6e20537065617200000000000000000000"),
                new Golden(582, "golden_spear", "f003010000009c0a8501ffff010a0000030e006d765f6f726967696e5f6d65746100000000030c006d765f6f726967696e5f6964ff0000000813006d765f6f726967696e5f6e616d65737061636516006d696e6563726166743a676f6c64656e5f73706561720a0700646973706c61790804004e616d650c00476f6c64656e20537065617200000000000000000000"),
                new Golden(582, "diamond_spear", "f003010000009c0a8701ffff010a0000030e006d765f6f726967696e5f6d65746100000000030c006d765f6f726967696e5f6964ff0000000813006d765f6f726967696e5f6e616d65737061636517006d696e6563726166743a6469616d6f6e645f73706561720a0700646973706c61790804004e616d650d004469616d6f6e6420537065617200000000000000000000"),
                new Golden(582, "netherite_spear", "f003010000009c0a8b01ffff010a0000030e006d765f6f726967696e5f6d65746100000000030c006d765f6f726967696e5f6964ff0000000813006d765f6f726967696e5f6e616d65737061636519006d696e6563726166743a6e65746865726974655f73706561720a0700646973706c61790804004e616d650f004e657468657269746520537065617200000000000000000000"),
                new Golden(2193, "wooden_spear", "5c0301000000000a00000000000000000000"),
                new Golden(2193, "stone_spear", "5b0301000000000a00000000000000000000"),
                new Golden(2193, "copper_spear", "560301000000000a00000000000000000000"),
                new Golden(2193, "iron_spear", "590301000000000a00000000000000000000"),
                new Golden(2193, "golden_spear", "580301000000000a00000000000000000000"),
                new Golden(2193, "diamond_spear", "570301000000000a00000000000000000000"),
                new Golden(2193, "netherite_spear", "5a0301000000000a00000000000000000000"),
                new Golden(2193, "trident", "2c0201000000001cffff010a000003060044616d61676500000000000000000000000000")
        );
    }

    @ParameterizedTest
    @MethodSource("unchangedWire")
    void keepsNewClientAndPreWindowBytes(Golden golden) {
        GameVersion version = GameVersion.byProtocol(golden.protocol(), false);
        assertArrayEquals(HexFormat.of().parseHex(golden.hex()),
                write(version, Item.fromString("minecraft:" + golden.key()), false));
    }

    @Test
    void keepsRawDamageInDisplaySwordAndOriginMetadata() throws IOException {
        GameVersion version = GameVersion.byProtocol(818, false);
        Item item = original("minecraft:diamond_spear", 1, true);
        item.setDamage(93);
        byte[] bytes = write(version, item, false);
        assertEquals("minecraft:diamond_sword", wireNamespace(version, bytes, false));
        BinaryStream input = new BinaryStream(bytes);
        input.getVarInt();
        input.getLShort();
        input.getUnsignedVarInt();
        if (input.getBoolean()) input.getVarInt();
        input.getVarInt();
        ByteBuf userData = Unpooled.wrappedBuffer(input.getByteArray());
        try (LittleEndianByteBufInputStream nbt = new LittleEndianByteBufInputStream(userData)) {
            assertEquals(-1, nbt.readShort());
            assertEquals(1, nbt.readUnsignedByte());
            CompoundTag tags = NBTIO.readSafely(nbt, ByteOrder.LITTLE_ENDIAN);
            assertEquals(93, tags.getInt("Damage"));
            assertEquals(93, tags.getInt("mv_origin_meta"));
        } finally {
            userData.release();
        }
        assertEquals(93, read(version, bytes, false).getDamage());
    }

    @Test
    void clientAliasMetadataCannotChangeServerInventorySourceIdentity() {
        GameVersion version = GameVersion.byProtocol(818, false);
        Item sword = Item.fromString("minecraft:diamond_sword");
        sword.setNamedTag(originTags());
        Item claimedSpear = read(version, write(version, sword, false), false);
        assertEquals("minecraft:diamond_spear", claimedSpear.getNamespaceId());
        BaseInventory inventory = new BaseInventory(null, InventoryType.CHEST, Map.of(0, sword)) {};
        SlotChangeAction forged = new SlotChangeAction(inventory, 0, claimedSpear, Item.get(Item.AIR));
        assertFalse(forged.isValid(null));
        assertEquals("minecraft:diamond_sword", inventory.getItem(0).getNamespaceId());
        BaseInventory lawful = new BaseInventory(null, InventoryType.CHEST, Map.of(0, claimedSpear)) {};
        assertTrue(new SlotChangeAction(lawful, 0, claimedSpear, Item.get(Item.AIR)).isValid(null));
    }
}
