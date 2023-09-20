package cn.nukkit.network.protocol.types;

import lombok.Value;

@Value
public class ExperimentData {
    String name;
    boolean enabled;
}
