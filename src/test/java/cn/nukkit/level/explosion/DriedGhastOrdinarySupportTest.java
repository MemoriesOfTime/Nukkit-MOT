package cn.nukkit.level.explosion;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BinaryStream;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary support follows the release gate, rather than experimental item-table presence. */
@Isolated("native Server.instance, item registries and hashed block palette mode")
public final class DriedGhastOrdinarySupportTest {
    @BeforeAll static void bootstrap() throws Exception { cn.nukkit.MockServer.init(); }

    private static byte[] resource(String name) throws Exception {
        try (var input = DriedGhastOrdinarySupportTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name);
            return input.readAllBytes();
        }
    }

    private static int declaration(int protocol, String name, String expectedSha) throws Exception {
        byte[] bytes = resource("runtime_item_states_" + protocol + ".json");
        assertEquals(expectedSha, java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Integer result = null;
        for (var value : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray()) {
            var row = value.getAsJsonObject();
            if (!name.equals(row.get("name").getAsString())) continue;
            assertNull(result, "Duplicate independent item declaration " + name);
            result = row.get("id").getAsInt();
        }
        assertNotNull(result, name);
        return result;
    }

    private static CompoundTag nativeState(int protocol, int id, int meta, String expectedSha) throws Exception {
        byte[] bytes = resource("runtime_block_states_" + protocol + ".dat");
        assertEquals(expectedSha, java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var root = NBTIO.readTag(new GZIPInputStream(new ByteArrayInputStream(bytes)), ByteOrder.BIG_ENDIAN, false);
        assertInstanceOf(ListTag.class, root);
        CompoundTag selected = null;
        for (var value : ((ListTag<?>) root).getAll()) {
            CompoundTag row = assertInstanceOf(CompoundTag.class, value);
            assertTrue(row.containsInt("id") && (row.containsShort("data") || row.containsInt("data")));
            assertTrue(!row.exist("stateOverload") || row.containsByte("stateOverload"));
            if (row.getInt("id") == id && row.getShort("data") == meta && !row.getBoolean("stateOverload")) {
                assertNull(selected, "Ambiguous exact native state " + id + ":" + meta);
                selected = row.clone();
            }
        }
        assertNotNull(selected, "Exact native state " + id + ":" + meta);
        assertTrue(selected.containsInt("runtimeId") && selected.containsString("name") && selected.containsCompound("states"));
        return selected;
    }

    private static int independentHash(String name, CompoundTag states) throws Exception {
        CompoundTag canonical = new CompoundTag(new LinkedHashMap<>()).putString("name", name)
                .putCompound("states", new CompoundTag(new TreeMap<>(states.getTags())));
        int hash = 0x811c9dc5;
        for (byte value : NBTIO.write(canonical, ByteOrder.LITTLE_ENDIAN)) hash = (hash ^ (value & 255)) * 0x01000193;
        return hash;
    }

    private record Slot(int networkId, int count, int damage, int blockRuntime, CompoundTag tag) { }

    private static Slot actualSlot(GameVersion version, Item item) throws Exception {
        BinaryStream output = new BinaryStream();
        output.putSlot(version, item, false);
        BinaryStream wire = new BinaryStream(output.getBuffer());
        int network = wire.getVarInt();
        int count = wire.getLShort();
        int damage = (int) wire.getUnsignedVarInt();
        assertFalse(wire.getBoolean(), "No stack network ID in these ordinary source items");
        int blockRuntime = wire.getVarInt();
        byte[] userBytes = wire.getByteArray();
        assertEquals(wire.getCount(), wire.getOffset(), "All physical item bytes consumed");
        var user = new ByteArrayInputStream(userBytes);
        assertEquals(255, user.read()); assertEquals(255, user.read()); assertEquals(1, user.read());
        CompoundTag tag = NBTIO.read(user, ByteOrder.LITTLE_ENDIAN);
        assertArrayEquals(new byte[8], user.readNBytes(8), "Empty CanPlaceOn and CanDestroy lists");
        assertEquals(0, user.available(), "No unexplained item user-data bytes");
        return new Slot(network, count, damage, blockRuntime, tag);
    }

    @Test void ordinaryProtocol800RejectsDeclaredDriedGhastAndWritesWholeItemFallback() throws Exception {
        assertEquals(800, GameVersion.V1_21_80.getProtocol()); assertEquals(1282, Block.DRIED_GHAST);
        String itemsSha = "8c80e4f3ea2cef86437147656a94c22b9afdffa607548ff2b941e6ffa076bcec";
        assertEquals(-1027, declaration(800, "minecraft:dried_ghast", itemsSha), "The experimental raw declaration deliberately exists");
        assertEquals(248, declaration(800, "minecraft:info_update", itemsSha));
        CompoundTag fallback = nativeState(800, 248, 0, "23021b7aa74cbcf69ea6113629eb6badb06dbb0b10b00ad70d1538c34500587c");
        assertEquals("minecraft:info_update", fallback.getString("name")); assertTrue(fallback.getCompound("states").isEmpty());
        assertEquals(1222, fallback.getInt("runtimeId"));
        assertEquals(-1887601408, independentHash("minecraft:info_update", new CompoundTag()));
        Item[] drops = Block.get(Block.DRIED_GHAST, 0).getDrops(Item.get(Item.AIR));
        assertEquals(1, drops.length);
        Item[] routes = { Block.get(Block.DRIED_GHAST, 0).toItem(), drops[0],
                Item.fromString("minecraft:dried_ghast"), Item.get(-1027, 0, 1) };
        boolean previous = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (Item item : routes) {
                assertInstanceOf(ItemBlock.class, item); item.setCount(7);
                item.setNamedTag(new CompoundTag().putString("opaque", "dried-ghast800").putInt("receipt", 41));
                assertEquals(-1027, item.getId()); assertEquals(0, item.getDamage());
                assertEquals(1282, item.getBlockUnsafe().getId()); assertEquals(0, item.getBlockUnsafe().getDamage());
                assertEquals(-1027, RuntimeItems.getMapping(GameVersion.V1_21_80).toRuntime(-1027, 0).getRuntimeId());
                assertFalse(item.isSupportedOn(GameVersion.V1_21_80), "Ordinary support does not advertise Drop2");
                byte[] before = item.getCompoundTag().clone(); Block attached = item.getBlockUnsafe();
                for (boolean hashed : new boolean[]{false, true}) {
                    GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                    Slot actual = actualSlot(GameVersion.V1_21_80, item);
                    assertEquals(248, actual.networkId()); assertEquals(7, actual.count()); assertEquals(0, actual.damage());
                    assertEquals(hashed ? -1887601408 : 1222, actual.blockRuntime(), "Entire item prefix and block body degrade together");
                    assertTrue(actual.tag().containsInt("mv_origin_id") && actual.tag().containsInt("mv_origin_meta")
                            && actual.tag().containsCompound("mv_origin_nbt"));
                    assertEquals(-1027, actual.tag().getInt("mv_origin_id")); assertEquals(0, actual.tag().getInt("mv_origin_meta"));
                    CompoundTag expectedOrigin = item.getNamedTag().clone(); expectedOrigin.setName("mv_origin_nbt");
                    assertEquals(expectedOrigin, actual.tag().getCompound("mv_origin_nbt"));
                    assertArrayEquals(before, item.getCompoundTag()); assertSame(attached, item.getBlockUnsafe());
                    assertEquals(7, item.getCount()); assertEquals(-1027, item.getId()); assertEquals(0, item.getDamage());
                    assertEquals(1282, attached.getId()); assertEquals(0, attached.getDamage());
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(previous); }
    }

    @Test void releasedProtocol818PreservesExistingPublicFallbackWireIdentity() throws Exception {
        assertEquals(818, GameVersion.V1_21_90.getProtocol());
        assertEquals(-1027, declaration(818, "minecraft:dried_ghast", "76b849ee98a7776056f43d222d01fddeff8e587212639c6caab30ed35a5101d5"));
        // Public9cb has no Dried Ghast block row here. This support-only change preserves that mapping.
        CompoundTag fallback = nativeState(818, 248, 0, "0984c9baf5a8dcef9fa3f135bb9d67fcbf7945241a50c1045b972ab66d02e91d");
        assertEquals("minecraft:info_update", fallback.getString("name"));
        assertTrue(fallback.getCompound("states").isEmpty());
        Item item = new ItemBlock(Block.get(Block.DRIED_GHAST, 0), 0, 7);
        item.setNamedTag(new CompoundTag().putString("opaque", "dried-ghast818").putInt("receipt", 42));
        assertTrue(item.isSupportedOn(GameVersion.V1_21_90));
        byte[] before = item.getCompoundTag().clone();
        boolean previous = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (boolean hashed : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed);
                Slot actual = actualSlot(GameVersion.V1_21_90, item);
                assertEquals(-1027, actual.networkId()); assertEquals(7, actual.count()); assertEquals(0, actual.damage());
                assertEquals(hashed ? independentHash("minecraft:info_update", fallback.getCompound("states")) : fallback.getInt("runtimeId"), actual.blockRuntime());
                assertEquals(item.getNamedTag(), actual.tag());
                assertArrayEquals(before, item.getCompoundTag()); assertEquals(1282, item.getBlockUnsafe().getId());
                assertEquals(0, item.getBlockUnsafe().getDamage()); assertEquals(-1027, item.getId()); assertEquals(7, item.getCount());
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(previous); }
    }

    @Test void ordinaryNeighborsAndSupportedWindowRetainTheirMappingContract() throws Exception {
        Item stone = new ItemBlock(Block.get(Block.STONE, 0), 0, 3);
        Item dried = new ItemBlock(Block.get(Block.DRIED_GHAST, 0), 0, 1);
        Item mappingOnly = new Item(-1027, 0);
        GameVersion mapped816 = GameVersion.byProtocol(816, false);
        assertEquals(GameVersion.V1_21_80, mapped816, "Protocol816 resolves to the existing pre-release family");
        assertFalse(dried.isSupportedOn(mapped816), "The ordinary gate still rejects816 before818");
        int versions = 0;
        for (GameVersion version : GameVersion.values()) {
            if (version.isNetEase() || version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
            assertTrue(stone.isSupportedOn(version), "Ordinary stone remains supported " + version);
            assertEquals(version.getProtocol() >= 818 && mappingOnly.isSupportedOn(version),
                    dried.isSupportedOn(version), "Release gate still requires inherited registration " + version);
            versions++;
        }
        assertEquals(40, versions, "Complete standard 1.20 through 1.26.50 support window");
        stone.setNamedTag(new CompoundTag().putString("opaque", "ordinary-stone")); byte[] before = stone.getCompoundTag().clone();
        boolean previous = GlobalBlockPalette.useHashedBlockNetworkIds();
        try {
            for (GameVersion version : new GameVersion[]{GameVersion.V1_20_0, GameVersion.V1_21_80}) {
                CompoundTag row = nativeState(version.getProtocol(), 1, 0, version == GameVersion.V1_20_0
                        ? "b5097e4d8172dc249a6a10528539e7722ff1b1cc943768a4f9061b5e83a644e4"
                        : "23021b7aa74cbcf69ea6113629eb6badb06dbb0b10b00ad70d1538c34500587c");
                assertEquals("minecraft:stone", row.getString("name"));
                for (boolean hashed : new boolean[]{false, true}) {
                    GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed); Slot actual = actualSlot(version, stone);
                    assertEquals(1, actual.networkId()); assertEquals(3, actual.count()); assertEquals(0, actual.damage());
                    assertEquals(hashed ? independentHash("minecraft:stone", row.getCompound("states")) : row.getInt("runtimeId"), actual.blockRuntime());
                    assertEquals(stone.getNamedTag(), actual.tag()); assertArrayEquals(before, stone.getCompoundTag());
                    assertEquals(1, stone.getBlockUnsafe().getId()); assertEquals(0, stone.getBlockUnsafe().getDamage());
                }
            }
        } finally { GlobalBlockPalette.setUseHashedBlockNetworkIds(previous); }
    }
}
