package cn.nukkit.entity.data;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EntityMetadata {

    private Int2ObjectMap<EntityData> map;

    public EntityMetadata() {
        this.map = new Int2ObjectOpenHashMap<>();
    }

    private EntityMetadata(Int2ObjectMap<EntityData> map) {
        this.map = map;
    }

    public EntityData get(int id) {
        return this.getOrDefault(id, null);
    }

    public EntityData getOrDefault(int id, EntityData defaultValue) {
        try {
            return this.map.getOrDefault(id, defaultValue).setId(id);
        } catch (Exception e) {
            if (defaultValue != null) {
                return defaultValue.setId(id);
            }
            return null;
        }
    }

    public boolean exists(int id) {
        return this.map.containsKey(id);
    }

    public EntityMetadata put(EntityData data) {
        // 确保 LongEntityData 的 dataVersions 被正确初始化
        if (data instanceof LongEntityData longData) {
            int id = data.getId();
            if (id == Entity.DATA_FLAGS && (longData.dataVersions == null || longData.dataVersions.length != 3)) {
                longData.dataVersions = convertFlagsToDataVersions(longData.getData());
            } else if (id == Entity.DATA_FLAGS_EXTENDED && (longData.dataVersions == null || longData.dataVersions.length != 1)) {
                longData.dataVersions = convertExtendedFlagsToDataVersions(longData.getData());
            }
        }
        this.map.put(data.getId(), data);
        return this;
    }

    public EntityData remove(int id) {
        return this.map.remove(id);
    }

    public int getByte(int id) {
        return (int) this.getOrDefault(id, new ByteEntityData(id, 0)).getData() & 0xff;
    }

    public int getShort(int id) {
        return (int) this.getOrDefault(id, new ShortEntityData(id, 0)).getData();
    }

    public int getInt(int id) {
        return (int) this.getOrDefault(id, new IntEntityData(id, 0)).getData();
    }

    public long getLong(int id) {
        return (Long) this.getOrDefault(id, new LongEntityData(id, 0)).getData();
    }

    public float getFloat(int id) {
        return (float) this.getOrDefault(id, new FloatEntityData(id, 0)).getData();
    }

    public boolean getBoolean(int id) {
        return this.getByte(id) == 1;
    }

    public CompoundTag getNBT(int id) {
        return (CompoundTag) this.getOrDefault(id, new NBTEntityData(id, new CompoundTag())).getData();
    }

    public String getString(int id) {
        return (String) this.getOrDefault(id, new StringEntityData(id, "")).getData();
    }

    public Vector3 getPosition(int id) {
        return (Vector3) this.getOrDefault(id, new IntPositionEntityData(id, new Vector3())).getData();
    }

    public Vector3f getFloatPosition(int id) {
        return (Vector3f) this.getOrDefault(id, new Vector3fEntityData(id, new Vector3f())).getData();
    }

    public EntityMetadata putByte(int id, int value) {
        return this.put(new ByteEntityData(id, value));
    }

    public EntityMetadata putShort(int id, int value) {
        return this.put(new ShortEntityData(id, value));
    }

    public EntityMetadata putInt(int id, int value) {
        return this.put(new IntEntityData(id, value));
    }

    public EntityMetadata putLong(int id, long value) {
        LongEntityData data = new LongEntityData(id, value);
        if (id == Entity.DATA_FLAGS) {
            data.dataVersions = convertFlagsToDataVersions(value);
        } else if (id == Entity.DATA_FLAGS_EXTENDED) {
            data.dataVersions = convertExtendedFlagsToDataVersions(value);
        }
        return this.put(data);
    }

    /**
     * 将当前版本的 DATA_FLAGS 值转换为多版本兼容的 dataVersions 数组
     * @param currentFlags 当前版本的标志位值
     * @return long[3] = {protocol < 223, protocol 223~290, protocol 291+}
     */
    private static long[] convertFlagsToDataVersions(long currentFlags) {
        if (currentFlags == 0L) {
            return new long[]{0L, 0L, 0L};
        }
        long data137 = 0L; // < v1_2_13 (protocol < 223)
        long data223 = 0L; // v1_2_13 ~ v1_7_0 (protocol 223~290)
        long data291 = 0L; // v1_7_0+ (protocol 291+)

        for (int id = 0; id < 64; id++) {
            if ((currentFlags & (1L << id)) != 0) {
                int id291 = id > 46 ? id - 1 : id;
                int id223 = id291 > 30 ? id291 - 1 : id291;
                int id137 = (id223 >= 23 && id223 < 43) || id223 >= 46 ? id223 - 1 : id223;

                data291 |= 1L << id291;
                data223 |= 1L << id223;
                data137 |= 1L << id137;
            }
        }
        return new long[]{data137, data223, data291};
    }

    /**
     * 将当前版本的 DATA_FLAGS_EXTENDED 值转换为多版本兼容的 dataVersions 数组
     * @param currentFlags 当前版本的标志位值
     * @return long[1] = {protocol 291+}
     */
    private static long[] convertExtendedFlagsToDataVersions(long currentFlags) {
        if (currentFlags == 0L) {
            return new long[]{0L};
        }
        long data291 = 0L;
        for (int id = 0; id < 64; id++) {
            if ((currentFlags & (1L << id)) != 0) {
                int id291 = id > 46 ? id - 1 : id;
                data291 |= 1L << id291;
            }
        }
        return new long[]{data291};
    }

    public EntityMetadata putFloat(int id, float value) {
        return this.put(new FloatEntityData(id, value));
    }

    public EntityMetadata putBoolean(int id, boolean value) {
        return this.putByte(id, value ? 1 : 0);
    }

    public EntityMetadata putNBT(int id, CompoundTag tag) {
        return this.put(new NBTEntityData(id, tag));
    }

    public EntityMetadata putSlot(int id, Item value) {
        return this.put(new NBTEntityData(id, value.getNamedTag()));
    }

    public EntityMetadata putString(int id, String value) {
        return this.put(new StringEntityData(id, value));
    }

    public Map<Integer, EntityData> getMap() {
        return new TreeMap<>(this.map); // Ordered
    }

    @Override
    public EntityMetadata clone() {
        return new EntityMetadata(new Int2ObjectOpenHashMap<>(this.map));
    }
}
