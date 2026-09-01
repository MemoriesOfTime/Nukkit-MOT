package cn.nukkit.network.protocol.netease.pyrpc.subpacket;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket;

/**
 * NetEase store purchase success PyRpc payload.
 */
@OnlyNetEase
public final class StoreBuySuccessPyRpcSubPacket implements PyRpcSubPacket {

    public static final String METHOD = "StoreBuySuccServerEvent";

    @Override
    public String getMethod() {
        return METHOD;
    }
}
