package cn.nukkit.network.protocol.types.biome;


import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.cloudburstmc.protocol.common.util.index.Indexable;
import org.cloudburstmc.protocol.common.util.index.Unindexed;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

@Value
@RequiredArgsConstructor(onConstructor_ = { @Deprecated })
public class BiomeDefinitionData {
    /**
     * Custom biome ID. (uint16)
     */
    @Nullable
    @Getter(AccessLevel.NONE)
    public transient Indexable<String> id;
    public float temperature;
    public float downfall;
    /**
     * 0-1 how frozen the leaves look.
     * @since 1.21.110
     */
    public float foliageSnow;
    /**
     * @deprecated 1.21.110
     */
    @SuppressWarnings("dep-ann")
    public float redSporeDensity;
    /**
     * @deprecated 1.21.110
     */
    @SuppressWarnings("dep-ann")
    public float blueSporeDensity;
    /**
     * @deprecated 1.21.110
     */
    @SuppressWarnings("dep-ann")
    public float ashDensity;
    /**
     * @deprecated 1.21.110
     */
    @SuppressWarnings("dep-ann")
    public float whiteAshDensity;
    public float depth;
    public float scale;
    public Color mapWaterColor;
    public boolean rain;
    @Nullable
    @Getter(AccessLevel.NONE)
    public transient Indexable<List<String>> tags;
    @Nullable
    public BiomeDefinitionChunkGenData chunkGenData;

    @JsonCreator
    public BiomeDefinitionData(@Nullable String id, float temperature, float downfall, float foliageSnow, float depth,
                               float scale, Color mapWaterColor, boolean rain, @Nullable List<String> tags,
                               @Nullable BiomeDefinitionChunkGenData chunkGenData) {
        this.id = id == null ? null : new Unindexed<>(id);
        this.temperature = temperature;
        this.downfall = downfall;
        this.foliageSnow = foliageSnow;
        this.redSporeDensity = 0;
        this.blueSporeDensity = 0;
        this.ashDensity = 0;
        this.whiteAshDensity = 0;
        this.depth = depth;
        this.scale = scale;
        this.mapWaterColor = mapWaterColor;
        this.rain = rain;
        this.tags = tags == null ? null : new Unindexed<>(tags);
        this.chunkGenData = chunkGenData;
    }

    @JsonCreator
    public BiomeDefinitionData(@Nullable String id, float temperature, float downfall, float redSporeDensity,
                               float blueSporeDensity, float ashDensity, float whiteAshDensity, float depth,
                               float scale, Color mapWaterColor, boolean rain, @Nullable List<String> tags,
                               @Nullable BiomeDefinitionChunkGenData chunkGenData) {
        this.id = id == null ? null : new Unindexed<>(id);
        this.temperature = temperature;
        this.downfall = downfall;
        this.foliageSnow = 0; //0 for old behavior
        this.redSporeDensity = redSporeDensity;
        this.blueSporeDensity = blueSporeDensity;
        this.ashDensity = ashDensity;
        this.whiteAshDensity = whiteAshDensity;
        this.depth = depth;
        this.scale = scale;
        this.mapWaterColor = mapWaterColor;
        this.rain = rain;
        this.tags = tags == null ? null : new Unindexed<>(tags);
        this.chunkGenData = chunkGenData;
    }

    public @Nullable String getId() {
        if (id == null) {
            return null;
        }
        return id.get();
    }

    public @Nullable List<String> getTags() {
        if (tags == null) {
            return null;
        }
        return tags.get();
    }
}