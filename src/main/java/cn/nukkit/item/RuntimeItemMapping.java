package cn.nukkit.item;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.item.RuntimeItems.MappingEntry;
import cn.nukkit.item.customitem.CustomItem;
import cn.nukkit.item.customitem.CustomItemDefinition;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Log4j2
public class RuntimeItemMapping {

    private final int protocolId;

    private final Int2ObjectMap<LegacyEntry> runtime2Legacy = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<RuntimeEntry> legacy2Runtime = new Int2ObjectOpenHashMap<>();
    private final Map<String, LegacyEntry> identifier2Legacy = new HashMap<>();

    private final List<RuntimeEntry> itemPaletteEntries = new ArrayList<>();
    private final Int2ObjectMap<String> runtimeId2Name = new Int2ObjectOpenHashMap<>();
    private final Object2IntMap<String> name2RuntimeId = new Object2IntOpenHashMap<>();

    private final ArrayList<String> customItems = new ArrayList<>();

    private byte[] itemPalette;

    public RuntimeItemMapping(Map<String, MappingEntry> mappings, int protocolId) {
        this.protocolId = protocolId;
        String itemStatesFile = "runtime_item_states_" + protocolId + ".json";
        InputStream stream = Server.class.getClassLoader().getResourceAsStream(itemStatesFile);
        if (stream == null) {
            throw new AssertionError("Unable to load " + itemStatesFile);
        }
        JsonArray json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();

        for (JsonElement element : json) {
            if (!element.isJsonObject()) {
                throw new IllegalStateException("Invalid entry");
            }
            JsonObject entry = element.getAsJsonObject();
            String identifier = entry.get("name").getAsString();
            int runtimeId = entry.get("id").getAsInt();

            //高版本"minecraft:wool"的名称改为"minecraft:white_wool"
            //他们的legacyId均为35，这里避免冲突忽略"minecraft:wool"
            if (protocolId >= ProtocolInfo.v1_19_63 && "minecraft:wool".equalsIgnoreCase(identifier)) {
                continue;
            }

            if (this.protocolId < ProtocolInfo.v1_16_100) {
                this.registerOldItem(identifier, runtimeId);
                continue;
            }

            this.runtimeId2Name.put(runtimeId, identifier);
            this.name2RuntimeId.put(identifier, runtimeId);

            boolean hasDamage = false;
            int damage = 0;
            int legacyId;

            if (mappings.containsKey(identifier)) {
                MappingEntry mapping = mappings.get(identifier);
                legacyId = RuntimeItems.getLegacyIdFromLegacyString(mapping.getLegacyName());
                if(!(mapping.getProtocol() > protocolId)) {
                    if (legacyId == -1) {
                        throw new IllegalStateException("Unable to match  " + mapping + " with legacyId");
                    }
                    damage = mapping.getDamage();
                    hasDamage = true;
                } else {
                    legacyId = RuntimeItems.getLegacyIdFromLegacyString(identifier);
                    if (legacyId == -1) {
                        log.trace("Unable to find legacyId for " + identifier);
                        continue;
                    }
                }
            } else {
                legacyId = RuntimeItems.getLegacyIdFromLegacyString(identifier);
                if (legacyId == -1) {
                    log.trace("Unable to find legacyId for " + identifier);
                    continue;
                }
            }

            this.registerItem(identifier, runtimeId, legacyId, damage, hasDamage);
        }

        this.generatePalette();
    }

    Object2IntMap<String> getName2RuntimeId() {
        return name2RuntimeId;
    }

    public void registerItem(String identifier, int runtimeId, int legacyId, int damage) {
        this.registerItem(identifier, runtimeId, legacyId, damage, false);
    }

    public void registerItem(String identifier, int runtimeId, int legacyId, int damage, boolean hasDamage) {
        int fullId = this.getFullId(legacyId, damage);
        LegacyEntry legacyEntry = new LegacyEntry(legacyId, hasDamage, damage);

        if (Nukkit.DEBUG > 1) {
            if (this.runtime2Legacy.containsKey(runtimeId)) {
                log.warn("RuntimeItemMapping: Registering " + identifier + " but runtime id " + runtimeId + " is already used");
            }
        }

        this.runtimeId2Name.put(runtimeId, identifier);
        this.name2RuntimeId.put(identifier, runtimeId);

        this.runtime2Legacy.put(runtimeId, legacyEntry);
        this.identifier2Legacy.put(identifier, legacyEntry);
        if (!hasDamage && this.legacy2Runtime.containsKey(fullId)) {
            log.debug("RuntimeItemMapping contains duplicated legacy item state runtimeId=" + runtimeId + " identifier=" + identifier);
        } else {
            RuntimeEntry runtimeEntry = new RuntimeEntry(identifier, runtimeId, hasDamage);
            this.legacy2Runtime.put(fullId, runtimeEntry);
            this.itemPaletteEntries.add(runtimeEntry);
        }
    }

    synchronized boolean registerCustomItem(CustomItem customItem) {
        int runtimeId = CustomItemDefinition.getRuntimeId(customItem.getNamespaceId());
        String namespaceId = customItem.getNamespaceId();
        if (!Server.getInstance().enableExperimentMode) {
            return false;
        }
        if (!this.customItems.contains(namespaceId)) { //多个版本共用一个RuntimeItemMapping时，重复不返回false
            this.customItems.add(namespaceId);

            RuntimeEntry entry = new RuntimeEntry(
                    customItem.getNamespaceId(),
                    runtimeId,
                    false,
                    true
            );
            this.itemPaletteEntries.add(entry);
            this.runtimeId2Name.put(runtimeId, namespaceId);
            this.name2RuntimeId.put(namespaceId, runtimeId);

            this.generatePalette();
        }
        return true;
    }

    synchronized void deleteCustomItem(CustomItem customItem) {
        String namespaceId = customItem.getNamespaceId();
        if (!Server.getInstance().enableExperimentMode && !this.customItems.contains(namespaceId)) {
            return;
        }
        this.customItems.remove(namespaceId);

        this.runtimeId2Name.remove(customItem.getId());
        this.name2RuntimeId.removeInt(customItem.getNamespaceId());
        this.itemPaletteEntries.removeIf(next -> next.getIdentifier().equals(customItem.getNamespaceId()));

        this.generatePalette();
    }

    public ArrayList<String> getCustomItems() {
        return new ArrayList<>(customItems);
    }

    private void registerOldItem(String identifier, int legacyId) {
        int fullId = this.getFullId(legacyId, 0);
        LegacyEntry legacyEntry = new LegacyEntry(legacyId, false, 0);

        this.runtime2Legacy.put(legacyId, legacyEntry);
        this.identifier2Legacy.put(identifier, legacyEntry);
        this.legacy2Runtime.put(fullId, new RuntimeEntry(identifier, legacyId, false));
    }

    public void generatePalette() {
        BinaryStream paletteBuffer = new BinaryStream();
        int size = 0;
        for (RuntimeEntry entry : this.itemPaletteEntries) {
            if (entry.isCustomItem() && (!Server.getInstance().enableExperimentMode || protocolId < ProtocolInfo.v1_16_100)) {
                break;
            }
            size++;
        }
        paletteBuffer.putUnsignedVarInt(size);
        for (RuntimeEntry entry : this.itemPaletteEntries) {
            if (entry.isCustomItem()) {
                if (Server.getInstance().enableExperimentMode && protocolId >= ProtocolInfo.v1_16_100) {
                    paletteBuffer.putString(entry.getIdentifier());
                    paletteBuffer.putLShort(entry.getRuntimeId());
                    paletteBuffer.putBoolean(true); // Component item
                }
            } else {
                paletteBuffer.putString(entry.getIdentifier());
                paletteBuffer.putLShort(entry.getRuntimeId());
                if (this.protocolId >= ProtocolInfo.v1_16_100) {
                    paletteBuffer.putBoolean(false); // Component item
                }
            }
        }
        this.itemPalette = paletteBuffer.getBuffer();
    }

    public LegacyEntry fromRuntime(int runtimeId) {
        LegacyEntry legacyEntry = this.runtime2Legacy.get(runtimeId);
        if (legacyEntry == null) {
            throw new IllegalArgumentException("Unknown runtime2Legacy mapping: runtimeID=" + runtimeId + " protocol=" + this.protocolId);
        }
        return legacyEntry;
    }

    public RuntimeEntry toRuntime(int id, int meta) {
        RuntimeEntry runtimeEntry = this.legacy2Runtime.get(this.getFullId(id, meta));
        if (runtimeEntry == null) {
            runtimeEntry = this.legacy2Runtime.get(this.getFullId(id, 0));
        }

        if (runtimeEntry == null) {
            throw new IllegalArgumentException("Unknown legacy2Runtime mapping: id=" + id + " meta=" + meta + " protocol=" + this.protocolId);
        }
        return runtimeEntry;
    }

    public Item parseCreativeItem(JsonObject json, boolean ignoreUnknown) {
        return this.parseCreativeItem(json, ignoreUnknown, this.protocolId);
    }

    public Item parseCreativeItem(JsonObject json, boolean ignoreUnknown, int protocolId) {
        String identifier = json.get("id").getAsString();
        LegacyEntry legacyEntry = this.fromIdentifier(identifier);
        if (legacyEntry == null || !Utils.hasItemOrBlock(legacyEntry.getLegacyId())) {
            OptionalInt networkId = this.getNetworkIdByNamespaceId(identifier);
            if ("minecraft:raw_iron".equalsIgnoreCase(identifier)) {
                int test = 1;
            }
            if (networkId.isEmpty() || !Item.NAMESPACED_ID_ITEM.containsKey(identifier)) {
                if (!ignoreUnknown) {
                    throw new IllegalStateException("Can not find legacyEntry for " + identifier);
                }
                log.trace("Can not find legacyEntry for " + identifier);
                return null;
            } else {
                legacyEntry = null;
            }
        }

        byte[] nbtBytes;
        if (json.has("nbt_b64")) {
            nbtBytes = Base64.getDecoder().decode(json.get("nbt_b64").getAsString());
        } else if (json.has("nbt_hex")) {
            nbtBytes = Utils.parseHexBinary(json.get("nbt_hex").getAsString());
        } else {
            nbtBytes = new byte[0];
        }

        int legacyId = ItemID.STRING_IDENTIFIED_ITEM;
        if (legacyEntry != null) {
            legacyId = legacyEntry.getLegacyId();
        }
        int damage = 0;
        if (json.has("damage")) {
            damage = json.get("damage").getAsInt();
        } else if (legacyEntry != null && legacyEntry.isHasDamage()) {
            damage = legacyEntry.getDamage();
        } else if (json.has("blockRuntimeId")) {
            int runtimeId = json.get("blockRuntimeId").getAsInt();
            int fullId = GlobalBlockPalette.getLegacyFullId(protocolId, runtimeId);
            if (fullId == -1) {
                if (ignoreUnknown) {
                    return null;
                } else {
                    throw new IllegalStateException("Can not find blockRuntimeId for " + runtimeId);
                }
            }

            damage = fullId & 0xf;
        }

        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        if (legacyEntry != null) {
            return Item.get(legacyId, damage, count, nbtBytes);
        } else {
            Item item = Item.fromString(identifier);
            item.setDamage(damage);
            item.setCount(count);
            item.setCompoundTag(nbtBytes);
            return item;
        }
    }


    public LegacyEntry fromIdentifier(String identifier) {
        return this.identifier2Legacy.get(identifier);
    }

    public int getFullId(int id, int data) {
        return (((short) id) << 16) | ((data & 0x7fff) << 1);
    }

    /**
     * Returns the <b>namespaced id</b> of a given <b>network id</b>.
     *
     * @param networkId The given <b>network id</b>
     * @return The <b>namespace id</b> or {@code null} if it is unknown
     */
    @Nullable
    public String getNamespacedIdByNetworkId(int networkId) {
        return this.runtimeId2Name.get(networkId);
    }

    /**
     * Returns the <b>network id</b> of a given <b>namespaced id</b>.
     *
     * @param namespaceId The given <b>namespaced id</b>
     * @return A <b>network id</b> wrapped in {@link OptionalInt} or an empty {@link OptionalInt} if it is unknown
     */
    @NotNull
    public OptionalInt getNetworkIdByNamespaceId(@NotNull String namespaceId) {
        int id = this.name2RuntimeId.getOrDefault(namespaceId, -1);
        if (id == -1) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(id);
    }

    public int getNetworkId(Item item) {
        if (item instanceof StringItem) {
            OptionalInt networkIdByNamespaceId = getNetworkIdByNamespaceId(item.getNamespaceId());
            if (networkIdByNamespaceId.isEmpty()) {
                throw new IllegalArgumentException("Unknown item mapping " + item + " protocol=" + this.protocolId);
            }
            return networkIdByNamespaceId.getAsInt();
        }
        RuntimeEntry runtimeEntry = toRuntime(item.getId(), item.getDamage());
        if (runtimeEntry == null) {
            throw new IllegalArgumentException("Unknown item mapping " + item + " protocol=" + this.protocolId);
        }
        return runtimeEntry.runtimeId;
    }

    public byte[] getItemPalette() {
        return this.itemPalette;
    }

    public int getProtocolId() {
        return this.protocolId;
    }

    @Data
    public static class LegacyEntry {
        private final int legacyId;
        private final boolean hasDamage;
        private final int damage;

        public int getDamage() {
            return this.hasDamage ? this.damage : 0;
        }
    }

    @Data
    public static class RuntimeEntry {
        private final String identifier;
        private final int runtimeId;
        private final boolean hasDamage;
        private final boolean isCustomItem;

        public RuntimeEntry(String identifier, int runtimeId, boolean hasDamage) {
            this(identifier, runtimeId, hasDamage, false);
        }

        public RuntimeEntry(String identifier, int runtimeId, boolean hasDamage, boolean isCustomItem) {
            this.identifier = identifier;
            this.runtimeId = runtimeId;
            this.hasDamage = hasDamage;
            this.isCustomItem = isCustomItem;
        }
    }
}
