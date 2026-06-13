package cn.nukkit.network.protocol.netease.pyrpc;

import cn.nukkit.api.OnlyNetEase;

/**
 * A typed NetEase PyRpc message payload.
 */
@OnlyNetEase
public interface PyRpcSubPacket {

    String getMethod();
}
