package cn.nukkit.level.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.*;

/**
 * 一个已解析的结构模板：双层方块索引、方块实体与实体数据。
 * <p>
 * A parsed structure template: two block layers, block entities and entities.
 * Palette entries keep their original state NBT (name/version/states) so that
 * exports stay byte-compatible with Bedrock .mcstructure files.
 */
public final class Structure {

    /**
     * 固定写出 v1（每层 TAG_List&lt;TAG_Int&gt;、副层总写出），与各版本客户端一致；
     * v2（int 数组层、可省略空副层）仅 1.26.50+ 客户端能解析，所以只读取不写出。
     * <p>
     * Always written as v1 (two TAG_List&lt;TAG_Int&gt; layers, secondary layer always present):
     * every client from 1.13 to 1.26.x reads it. v2 int-array layers are only parsed by
     * clients 1.26.50+, so they are read but never written.
     */
    public static final int FORMAT_VERSION = 1;
    public static final int VOID = -1;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<CompoundTag> palette;
    private final int[] paletteLegacyIds;
    private final int[] layer0;
    private final int[] layer1;
    private final boolean hasLayer1;
    private final Map<Integer, CompoundTag> blockEntities;
    private final List<CompoundTag> entities;
    private final BlockVector3 origin;

    public Structure(int sizeX, int sizeY, int sizeZ, List<CompoundTag> palette, int[] paletteLegacyIds,
                     int[] layer0, int[] layer1, Map<Integer, CompoundTag> blockEntities,
                     List<CompoundTag> entities, BlockVector3 origin) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Invalid structure size " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        int volume = volume(sizeX, sizeY, sizeZ);
        if (layer0.length != volume || layer1.length != volume) {
            throw new IllegalArgumentException("Block index length does not match structure volume");
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette);
        this.paletteLegacyIds = paletteLegacyIds.clone();
        this.layer0 = layer0.clone();
        this.layer1 = layer1.clone();
        this.hasLayer1 = hasNonVoid(layer1);
        this.blockEntities = new HashMap<>(blockEntities);
        this.entities = new ArrayList<>(entities);
        this.origin = origin;
    }

    private static boolean hasNonVoid(int[] layer) {
        for (int value : layer) {
            if (value != VOID) {
                return true;
            }
        }
        return false;
    }

    public static int volume(int sizeX, int sizeY, int sizeZ) {
        return Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
    }

    public int index(int x, int y, int z) {
        return x * this.sizeY * this.sizeZ + y * this.sizeZ + z;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getVolume() {
        return layer0.length;
    }

    public List<CompoundTag> getPalette() {
        return palette;
    }

    /**
     * 调色板条目对应的旧版方块完整 ID（{@code id << DATA_BITS | meta}），未知方块为 {@link BlockID#INFO_UPDATE}。
     * <p>
     * Legacy full block id for a palette entry; unknown states fall back to {@link BlockID#INFO_UPDATE}.
     */
    public int getLegacyId(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex >= paletteLegacyIds.length) {
            return BlockID.INFO_UPDATE << Block.DATA_BITS;
        }
        int legacyId = paletteLegacyIds[paletteIndex];
        return legacyId < 0 ? BlockID.INFO_UPDATE << Block.DATA_BITS : legacyId;
    }

    public int getBlockIndex(int layer, int index) {
        return (layer == 0 ? layer0 : layer1)[index];
    }

    public boolean hasSecondaryLayer() {
        return hasLayer1;
    }

    public Map<Integer, CompoundTag> getBlockEntities() {
        return Collections.unmodifiableMap(blockEntities);
    }

    public List<CompoundTag> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    public BlockVector3 getOrigin() {
        return origin;
    }
}
