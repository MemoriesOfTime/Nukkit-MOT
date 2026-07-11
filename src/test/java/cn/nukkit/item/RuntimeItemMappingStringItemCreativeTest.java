package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.BinaryStream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeItemMappingStringItemCreativeTest {

    private static final Set<String> STRING_CREATIVE_ITEMS_WITH_LEGACY_MAPPINGS = Set.of(
            "minecraft:bamboo_door",
            "minecraft:chiseled_bookshelf",
            "minecraft:copper_ingot",
            "minecraft:crafter",
            "minecraft:echo_shard",
            "minecraft:mangrove_door",
            "minecraft:raw_copper",
            "minecraft:recovery_compass"
    );

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void registeredStringItemIdentifierClassificationDistinguishesRegularItems() {
        assertTrue(Item.isRegisteredStringItemIdentifier("minecraft:mace"));
        assertTrue(Item.isRegisteredStringItemIdentifier("MINECRAFT:MACE"));

        assertFalse(Item.isRegisteredStringItemIdentifier("minecraft:stick"));
        assertFalse(Item.isRegisteredStringItemIdentifier("minecraft:stone"));
    }

    @Test
    void creativeParserKeepsRegisteredStringItemIdentity() {
        RuntimeItemMapping mapping = RuntimeItems.getMapping(GameVersion.getLastVersion());
        JsonObject creativeItems = loadCreativeItems();
        JsonArray items = creativeItems.getAsJsonArray("items");
        Set<String> found = new LinkedHashSet<>();

        for (JsonElement element : items) {
            JsonObject creativeItem = element.getAsJsonObject();
            String identifier = creativeItem.get("id").getAsString();
            if (!STRING_CREATIVE_ITEMS_WITH_LEGACY_MAPPINGS.contains(identifier)) {
                continue;
            }

            Item fromString = Item.fromString(identifier);
            assertEquals(Item.STRING_IDENTIFIED_ITEM, fromString.getId(), identifier);

            Item parsed = mapping.parseCreativeItem(creativeItem, false, GameVersion.getLastVersion());
            assertEquals(Item.STRING_IDENTIFIED_ITEM, parsed.getId(), identifier);
            assertEquals(identifier, parsed.getNamespaceId(), identifier);
            found.add(identifier);
        }

        assertEquals(STRING_CREATIVE_ITEMS_WITH_LEGACY_MAPPINGS, found);
    }

    @Test
    void slotCodecKeepsRegisteredStringItemIdentity() {
        Item input = Item.fromString("minecraft:bamboo_door");
        input.setCount(3);

        BinaryStream encoded = new BinaryStream();
        encoded.putSlot(GameVersion.getLastVersion(), input);

        Item decoded = new BinaryStream(encoded.getBuffer()).getSlot(GameVersion.getLastVersion());
        assertEquals(Item.STRING_IDENTIFIED_ITEM, decoded.getId());
        assertEquals("minecraft:bamboo_door", decoded.getNamespaceId());
        assertEquals(3, decoded.getCount());
    }

    @Test
    void slotCodecWritesBlockRuntimeForBlockStringItem() {
        GameVersion gameVersion = GameVersion.getLastVersion();
        Item input = Item.fromString("minecraft:bamboo_door");

        BinaryStream encoded = new BinaryStream();
        encoded.putSlot(gameVersion, input);

        BinaryStream decoded = new BinaryStream(encoded.getBuffer());
        RuntimeItemMapping mapping = RuntimeItems.getMapping(gameVersion);
        assertEquals(mapping.getNetworkId(input), decoded.getVarInt());
        assertEquals(1, decoded.getLShort());
        assertEquals(0, decoded.getUnsignedVarInt());
        assertFalse(decoded.getBoolean());

        int expectedBlockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(gameVersion, Block.BAMBOO_DOOR, 0);
        assertTrue(expectedBlockRuntimeId != 0);
        assertEquals(expectedBlockRuntimeId, decoded.getVarInt());
    }

    @Test
    void networkItemStackDescriptorConsumesStringItemDamageTag() {
        Item input = Item.fromString("minecraft:mace");
        input.setDamage(0);
        input.setNamedTag(new CompoundTag().putInt("Damage", 7));

        BinaryStream encoded = new BinaryStream();
        encoded.putNetworkItemStackDescriptor(GameVersion.V1_26_20, input);

        Item decoded = new BinaryStream(encoded.getBuffer()).getNetworkItemStackDescriptor(GameVersion.V1_26_20);
        assertEquals(Item.STRING_IDENTIFIED_ITEM, decoded.getId());
        assertEquals("minecraft:mace", decoded.getNamespaceId());
        assertEquals(7, decoded.getDamage());
        assertFalse(decoded.hasCompoundTag());
    }

    @Test
    void oldSlotKeepsAuxDamageForMvOriginStringItemWhenNbtHasDamage() throws Exception {
        int expectedDamage = 2;
        CompoundTag tag = new CompoundTag()
                .putInt("Damage", 7)
                .putInt("mv_origin_id", Item.STRING_IDENTIFIED_ITEM)
                .putInt("mv_origin_meta", expectedDamage)
                .putString("mv_origin_namespace", "minecraft:mace");

        BinaryStream encoded = new BinaryStream();
        encoded.putVarInt(Item.INFO_UPDATE);
        encoded.putVarInt((expectedDamage << 8) | 1);
        encoded.putLShort(0xffff);
        encoded.putByte((byte) 1);
        encoded.put(NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN, true));
        encoded.putVarInt(0);
        encoded.putVarInt(0);

        Item decoded = new BinaryStream(encoded.getBuffer()).getSlot(GameVersion.V1_16_20);
        assertEquals(Item.STRING_IDENTIFIED_ITEM, decoded.getId());
        assertEquals("minecraft:mace", decoded.getNamespaceId());
        assertEquals(expectedDamage, decoded.getDamage());
        assertFalse(decoded.hasCompoundTag());
    }

    private static JsonObject loadCreativeItems() {
        InputStream stream = Server.class.getClassLoader().getResourceAsStream("creative_items.json");
        assertNotNull(stream);
        return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
    }
}
