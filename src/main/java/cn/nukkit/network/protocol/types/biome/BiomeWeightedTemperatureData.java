package cn.nukkit.network.protocol.types.biome;

import lombok.Value;

@Value
public class BiomeWeightedTemperatureData {

    BiomeTemperatureCategory temperature;
    long weight;
}
