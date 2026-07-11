package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.Identifier;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class EntityNbtAdapter {

    private static volatile Map<String, Integer> identifierToRuntimeIdCache;

    private EntityNbtAdapter() {
    }

    static boolean isSavable(Entity entity) {
        return !(entity instanceof Player) && !entity.closed && entity.canBeSavedWithChunk();
    }

    static void prepareForSave(Entity entity, CompoundTag nbt) {
        Identifier identifier = entity.getIdentifier();
        if (identifier != null) {
            nbt.putString("identifier", identifier.toString());
        }
        getOrCreateUniqueEntityId(entity, nbt);
    }

    static long getOrCreateUniqueEntityId(Entity entity, CompoundTag nbt) {
        if (nbt.contains("UniqueID")) {
            return nbt.getLong("UniqueID");
        }

        UUID uuid = entity.getUniqueId();
        long uniqueEntityId = uuid == null ? entity.getId() : uniqueIdFromUuid(uuid);
        if (uniqueEntityId == 0) {
            uniqueEntityId = entity.getId();
        }
        if (uniqueEntityId == 0) {
            uniqueEntityId = 1;
        }
        nbt.putLong("UniqueID", uniqueEntityId);
        return uniqueEntityId;
    }

    static boolean prepareForLoad(CompoundTag nbt) {
        return normalizeForNukkitLoad(nbt) == EntityNbtLoadStatus.LOADABLE;
    }

    static EntityNbtLoadStatus normalizeForNukkitLoad(CompoundTag nbt) {
        EntityNbtLoadStatus idStatus = normalizeEntityId(nbt);
        if (idStatus != EntityNbtLoadStatus.LOADABLE) {
            return idStatus;
        }
        if (!normalizeDoubleVector(nbt, "Pos", 0, 0, 0, true, true)) {
            return EntityNbtLoadStatus.INVALID;
        }
        if (!normalizeDoubleVector(nbt, "Motion", 0, 0, 0, false, false)) {
            return EntityNbtLoadStatus.INVALID;
        }
        if (!normalizeRotation(nbt)) {
            return EntityNbtLoadStatus.INVALID;
        }
        putBaseDefaults(nbt);
        EntityNbtLoadStatus specificStatus = normalizeEntitySpecificFields(nbt);
        if (specificStatus != EntityNbtLoadStatus.LOADABLE) {
            return specificStatus;
        }
        return EntityNbtLoadStatus.LOADABLE;
    }

    private static EntityNbtLoadStatus normalizeEntityId(CompoundTag nbt) {
        if (nbt.contains("id")) {
            return Entity.canCreateEntity(nbt.getString("id"))
                    ? EntityNbtLoadStatus.LOADABLE
                    : EntityNbtLoadStatus.PRESERVE_ONLY;
        }
        if (!nbt.containsString("identifier")) {
            return EntityNbtLoadStatus.INVALID;
        }

        String entityId = toNukkitEntityId(nbt.getString("identifier"));
        if (entityId == null) {
            return EntityNbtLoadStatus.PRESERVE_ONLY;
        }
        nbt.putString("id", entityId);
        return EntityNbtLoadStatus.LOADABLE;
    }

    private static String toNukkitEntityId(String identifier) {
        Map<String, Integer> cache = identifierToRuntimeIdCache;
        if (cache == null) {
            cache = buildIdentifierCache();
            identifierToRuntimeIdCache = cache;
        }
        Integer runtimeId = cache.get(identifier);
        if (runtimeId != null) {
            return String.valueOf(runtimeId);
        }
        if (Entity.canCreateEntity(identifier)) {
            return identifier;
        }
        return null;
    }

    private static Map<String, Integer> buildIdentifierCache() {
        Map<String, Integer> cache = new HashMap<>();
        for (Map.Entry<Integer, String> entry : Entity.getEntityRuntimeMapping().entrySet()) {
            if (Entity.canCreateEntity(entry.getKey())) {
                cache.put(entry.getValue(), entry.getKey());
            }
        }
        return cache;
    }

    private static boolean normalizeDoubleVector(CompoundTag nbt, String name, double defaultX, double defaultY,
                                                 double defaultZ, boolean required, boolean coordinate) {
        Tag tag = nbt.get(name);
        double[] values = {defaultX, defaultY, defaultZ};

        if (tag == null) {
            if (required) {
                return false;
            }
        } else {
            if (!(tag instanceof ListTag<?> list) || list.size() < 3) {
                return false;
            }

            for (int i = 0; i < 3; i++) {
                Tag element = list.get(i);
                if (!(element instanceof NumberTag<?> numberTag)) {
                    return false;
                }
                double value = numberTag.getData().doubleValue();
                if (coordinate ? !isValidCoordinate(value) : !isFinite(value)) {
                    return false;
                }
                values[i] = value;
            }
        }

        nbt.putList(new ListTag<DoubleTag>(name)
                .add(new DoubleTag("", values[0]))
                .add(new DoubleTag("", values[1]))
                .add(new DoubleTag("", values[2])));
        return true;
    }

    private static boolean normalizeRotation(CompoundTag nbt) {
        Tag tag = nbt.get("Rotation");
        float[] values = {0f, 0f};

        if (tag != null) {
            if (!(tag instanceof ListTag<?> list)) {
                return false;
            }

            for (int i = 0; i < values.length && i < list.size(); i++) {
                Tag element = list.get(i);
                if (!(element instanceof NumberTag<?> numberTag)) {
                    values[i] = 0f;
                    continue;
                }
                float value = numberTag.getData().floatValue();
                values[i] = Float.isFinite(value) ? value : 0f;
            }
        }

        nbt.putList(new ListTag<FloatTag>("Rotation")
                .add(new FloatTag("", values[0]))
                .add(new FloatTag("", values[1])));
        return true;
    }

    private static void putBaseDefaults(CompoundTag nbt) {
        if (!nbt.containsNumber("FallDistance")) {
            nbt.putFloat("FallDistance", 0f);
        }
        if (!nbt.containsNumber("Fire")) {
            nbt.putShort("Fire", 0);
        }
        if (!nbt.containsNumber("Air")) {
            nbt.putShort("Air", 300);
        }
        if (!nbt.containsNumber("OnGround")) {
            nbt.putBoolean("OnGround", false);
        }
        if (!nbt.containsNumber("Invulnerable")) {
            nbt.putBoolean("Invulnerable", false);
        }
        if (!nbt.containsNumber("Scale")) {
            nbt.putFloat("Scale", 1f);
        }
    }

    private static EntityNbtLoadStatus normalizeEntitySpecificFields(CompoundTag nbt) {
        normalizePainting(nbt);
        if (isEntity(nbt, "64", "Item")) {
            return normalizeItemEntity(nbt) ? EntityNbtLoadStatus.LOADABLE : EntityNbtLoadStatus.INVALID;
        }
        if (isEntity(nbt, "69", "XpOrb")) {
            return normalizeXpOrb(nbt) ? EntityNbtLoadStatus.LOADABLE : EntityNbtLoadStatus.INVALID;
        }
        if (isEntity(nbt, "66", "FallingSand", "FallingBlock")) {
            return normalizeFallingBlock(nbt);
        }
        return EntityNbtLoadStatus.LOADABLE;
    }

    private static void normalizePainting(CompoundTag nbt) {
        if (isEntity(nbt, "83", "Painting") && !nbt.containsString("Motive") && nbt.containsString("Motif")) {
            nbt.putString("Motive", nbt.getString("Motif"));
        }
    }

    private static boolean normalizeItemEntity(CompoundTag nbt) {
        if (!nbt.containsCompound("Item")) {
            return false;
        }
        CompoundTag item = nbt.getCompound("Item");
        if (!item.containsNumber("Count")) {
            return false;
        }
        if (!nbt.containsNumber("Health")) {
            nbt.putShort("Health", 5);
        }
        if (!nbt.containsNumber("Age")) {
            nbt.putShort("Age", 0);
        }
        if (!nbt.containsNumber("PickupDelay")) {
            nbt.putShort("PickupDelay", 0);
        }
        return true;
    }

    private static boolean normalizeXpOrb(CompoundTag nbt) {
        if (!nbt.containsNumber("Value") || nbt.getInt("Value") <= 0) {
            return false;
        }
        if (!nbt.containsNumber("Health")) {
            nbt.putShort("Health", 5);
        }
        if (!nbt.containsNumber("Age")) {
            nbt.putShort("Age", 0);
        }
        if (!nbt.containsNumber("PickupDelay")) {
            nbt.putShort("PickupDelay", 0);
        }
        return true;
    }

    private static EntityNbtLoadStatus normalizeFallingBlock(CompoundTag nbt) {
        if (normalizeLegacyFallingBlockFields(nbt)
                || normalizeFallingBlockFromCompound(nbt, "Block")
                || normalizeFallingBlockFromCompound(nbt, "FallingBlock")) {
            return EntityNbtLoadStatus.LOADABLE;
        }
        return EntityNbtLoadStatus.PRESERVE_ONLY;
    }

    private static boolean normalizeLegacyFallingBlockFields(CompoundTag nbt) {
        int blockId;
        if (nbt.contains("TileID")) {
            if (!nbt.containsNumber("TileID")) {
                return false;
            }
            blockId = nbt.getInt("TileID");
        } else if (nbt.contains("Tile")) {
            if (!nbt.containsNumber("Tile")) {
                return false;
            }
            blockId = nbt.getInt("Tile");
        } else {
            return false;
        }

        if (nbt.contains("Data") && !nbt.containsNumber("Data")) {
            return false;
        }
        int data = nbt.containsNumber("Data") ? nbt.getByte("Data") : 0;
        return putFallingBlockLegacyFields(nbt, blockId, data);
    }

    private static boolean normalizeFallingBlockFromCompound(CompoundTag nbt, String name) {
        if (!nbt.containsCompound(name)) {
            return false;
        }

        CompoundTag blockTag = nbt.getCompound(name);
        if (normalizeFallingBlockFromNamedLegacyTag(nbt, blockTag)) {
            return true;
        }

        BlockStateSnapshot blockState = resolveBlockState(blockTag);
        return blockState != null
                && putFallingBlockLegacyFields(nbt, blockState.getLegacyId(), blockState.getLegacyData());
    }

    private static boolean normalizeFallingBlockFromNamedLegacyTag(CompoundTag nbt, CompoundTag blockTag) {
        if (blockTag.containsNumber("id")) {
            int data = 0;
            if (blockTag.containsNumber("val")) {
                data = blockTag.getShort("val");
            } else if (blockTag.contains("Data")) {
                if (!blockTag.containsNumber("Data")) {
                    return false;
                }
                data = blockTag.getByte("Data");
            }
            return putFallingBlockLegacyFields(nbt, blockTag.getInt("id"), data);
        }
        if (!blockTag.containsString("name") || !blockTag.containsNumber("val")) {
            return false;
        }

        Item blockItem = Item.fromString(blockTag.getString("name"));
        int blockId = blockItem.getBlockId();
        return putFallingBlockLegacyFields(nbt, blockId, blockTag.getShort("val"));
    }

    private static BlockStateSnapshot resolveBlockState(CompoundTag blockTag) {
        if (!blockTag.containsString("name") || !blockTag.containsCompound("states")) {
            return null;
        }

        NbtMap state = toNbtMap(blockTag);
        if (state == null) {
            return null;
        }

        BlockStateMapping mapping = BlockStateMapping.get();
        BlockStateSnapshot blockState = mapping.getStateUnsafe(state);
        if (blockState != null) {
            return blockState;
        }

        try {
            NbtMap updatedState = mapping.updateVanillaState(state);
            return mapping.getStateUnsafe(updatedState);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean putFallingBlockLegacyFields(CompoundTag nbt, int blockId, int data) {
        if (blockId <= 0) {
            return false;
        }
        nbt.putInt("TileID", blockId);
        nbt.putByte("Data", data);
        return true;
    }

    private static NbtMap toNbtMap(CompoundTag tag) {
        NbtMapBuilder builder = NbtMap.builder();
        for (Map.Entry<String, Tag> entry : tag.getTags().entrySet()) {
            Object value = toNbtValue(entry.getValue());
            if (value == null) {
                return null;
            }
            builder.put(entry.getKey(), value);
        }
        return builder.build();
    }

    private static Object toNbtValue(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            return toNbtMap(compoundTag);
        }
        if (tag instanceof StringTag stringTag) {
            return stringTag.data;
        }
        if (tag instanceof NumberTag<?> numberTag) {
            Number value = numberTag.getData();
            return switch (tag.getId()) {
                case Tag.TAG_Byte -> value.byteValue();
                case Tag.TAG_Short -> value.shortValue();
                case Tag.TAG_Int -> value.intValue();
                case Tag.TAG_Long -> value.longValue();
                case Tag.TAG_Float -> value.floatValue();
                case Tag.TAG_Double -> value.doubleValue();
                default -> null;
            };
        }
        if (tag instanceof ByteArrayTag byteArrayTag) {
            return byteArrayTag.data == null ? new byte[0] : Arrays.copyOf(byteArrayTag.data, byteArrayTag.data.length);
        }
        if (tag instanceof IntArrayTag intArrayTag) {
            return intArrayTag.data == null ? new int[0] : Arrays.copyOf(intArrayTag.data, intArrayTag.data.length);
        }
        return null;
    }

    private static boolean isEntity(CompoundTag nbt, String networkId, String... legacyNames) {
        String id = nbt.getString("id");
        if (networkId.equals(id)) {
            return true;
        }
        for (String legacyName : legacyNames) {
            if (legacyName.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidCoordinate(double value) {
        return isFinite(value) && Math.abs(value) <= Entity.ENTITY_COORDINATES_MAX_VALUE;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static long uniqueIdFromUuid(UUID uuid) {
        long value = uuid.getLeastSignificantBits();
        if (value == 0) {
            value = uuid.getMostSignificantBits();
        }
        return value == 0 ? 1 : value;
    }
}

enum EntityNbtLoadStatus {
    LOADABLE,
    PRESERVE_ONLY,
    INVALID
}
