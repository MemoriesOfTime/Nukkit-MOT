package cn.nukkit.network.protocol.types.biome;

import lombok.Value;

@Value
public class BiomeClimateData {
    float temperature;
    float downfall;
    float redSporeDensity;
    float blueSporeDensity;
    float ashDensity;
    float whiteAshDensity;
    float snowAccumulationMin;
    float snowAccumulationMax;
}
