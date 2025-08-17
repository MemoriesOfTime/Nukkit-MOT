package cn.nukkit.network.protocol.types;

import cn.nukkit.network.protocol.PlayerAuthInputPacket;

public enum AuthoritativeMovementMode {
    /**
     * Movement is completely controlled by the client and does not send {@link PlayerAuthInputPacket}
     * @deprecated v800
     */
    @SuppressWarnings("dep-ann")
    CLIENT,
    /**
     * Movement is verified by the server using the {@link PlayerAuthInputPacket}
     */
    SERVER,
    SERVER_WITH_REWIND
}