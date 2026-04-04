package cn.nukkit.network.session.login;

import cn.nukkit.GameVersion;

/**
 * Session-scoped negotiated protocol state.
 */
public class SessionProtocolState {

    private volatile int raknetProtocol = -1;
    private volatile int bedrockProtocol = Integer.MAX_VALUE;
    private volatile GameVersion gameVersion;
    private volatile boolean protocolLocked;

    public int getRaknetProtocol() {
        return raknetProtocol;
    }

    public void setRaknetProtocol(int raknetProtocol) {
        this.raknetProtocol = raknetProtocol;
    }

    public int getBedrockProtocol() {
        return bedrockProtocol;
    }

    public void setBedrockProtocol(int bedrockProtocol) {
        this.bedrockProtocol = bedrockProtocol;
    }

    public GameVersion getGameVersion() {
        return gameVersion;
    }

    public void setGameVersion(GameVersion gameVersion) {
        this.gameVersion = gameVersion;
    }

    public boolean isProtocolLocked() {
        return protocolLocked;
    }

    public void setProtocolLocked(boolean protocolLocked) {
        this.protocolLocked = protocolLocked;
    }
}
