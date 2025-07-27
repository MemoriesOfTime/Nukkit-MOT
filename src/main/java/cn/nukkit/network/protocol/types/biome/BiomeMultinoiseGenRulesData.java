package cn.nukkit.network.protocol.types.biome;

import lombok.Value;

@Value
public class BiomeMultinoiseGenRulesData {

    float temperature;
    float humidity;
    float altitude;
    float weirdness;
    float weight;
}
