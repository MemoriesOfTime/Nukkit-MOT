package cn.nukkit.network.protocol.types;

import lombok.Value;

@Value
public class NetworkPermissions {
    public static final NetworkPermissions DEFAULT = new NetworkPermissions(false);

    boolean serverAuthSounds;
}
