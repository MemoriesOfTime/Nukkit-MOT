package cn.nukkit.network.protocol.types;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 数据驱动维度的定义，随 DimensionDataPacket 发送。
 * <p>
 * Definition of a data-driven dimension, sent via DimensionDataPacket.
 */
@Value
public class DimensionDefinition {
    String id;
    /**
     * 独占上界（含端点上界 + 1）；v2192 起线上改发高度跨度，由编码端从本值推导。
     * <p>
     * Exclusive upper bound (inclusive max + 1); since v2192 the wire carries the height span instead, derived from this value at encode time.
     */
    int maximumHeight;
    int minimumHeight;
    int generatorType;
    /**
     * @since v975 1.26.20
     */
    int dimensionType;
    /**
     * @since v2168 1.26.40
     */
    UUID packId;
    /**
     * 自定义维度的默认生物群系；vanilla 维度与旧协议下为 null。
     * <p>
     * Default biome of a custom dimension; null for vanilla dimensions and older protocols.
     *
     * @since v2192 1.26.50
     */
    @Nullable
    String defaultBiome;

    public DimensionDefinition(String id, int maximumHeight, int minimumHeight, int generatorType, int dimensionType, UUID packId) {
        this(id, maximumHeight, minimumHeight, generatorType, dimensionType, packId, null);
    }

    public DimensionDefinition(String id, int maximumHeight, int minimumHeight, int generatorType, int dimensionType, UUID packId, @Nullable String defaultBiome) {
        this.id = id;
        this.maximumHeight = maximumHeight;
        this.minimumHeight = minimumHeight;
        this.generatorType = generatorType;
        this.dimensionType = dimensionType;
        this.packId = packId;
        this.defaultBiome = defaultBiome;
    }
}
