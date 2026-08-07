package cn.nukkit.network.protocol.types.biome;

import lombok.Value;

import java.util.List;

@Value
public class BiomeSurfaceMaterialAdjustmentData {
    List<BiomeElementData> biomeElements;
}
