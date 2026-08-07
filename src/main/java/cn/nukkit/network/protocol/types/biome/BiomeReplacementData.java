package cn.nukkit.network.protocol.types.biome;

import lombok.Value;

import java.util.List;

/**
 * @since v859
 */
@Value
public class BiomeReplacementData {
    int biome;
    int dimension;
    List<Short> targetBiomes;
    float amount;
    float noiseFrequencyScale;
    int replacementIndex;
}
