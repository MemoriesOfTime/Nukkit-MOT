package cn.nukkit.network.protocol.types;

import lombok.Value;

import java.util.UUID;

@Value
public class DimensionDefinition {
    String id;
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
}
