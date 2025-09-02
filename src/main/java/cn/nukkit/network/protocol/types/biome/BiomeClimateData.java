package cn.nukkit.network.protocol.types.biome;

import lombok.Value;

@Value
public class BiomeClimateData {
    float temperature;
    float downfall;
    /**
     * @deprecated 1.21.110
     */
    float redSporeDensity;
    /**
     * @deprecated 1.21.110
     */
    float blueSporeDensity;
    /**
     * @deprecated 1.21.110
     */
    float ashDensity;
    /**
     * @deprecated 1.21.110
     */
    float whiteAshDensity;
    float snowAccumulationMin;
    float snowAccumulationMax;
}
