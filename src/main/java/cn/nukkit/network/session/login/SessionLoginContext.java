package cn.nukkit.network.session.login;

/**
 * Session-scoped login state.
 */
public class SessionLoginContext {

    private volatile SessionLoginPhase phase = SessionLoginPhase.CONNECTED;
    private volatile boolean loginVerified;
    private volatile boolean loginPacketReceived;
    private volatile boolean awaitingEncryptionHandshake;
    private volatile boolean shouldLogin;
    private volatile long lastPhaseChangeNanos = System.nanoTime();
    private volatile String disconnectCauseHint;

    public SessionLoginPhase getPhase() {
        return phase;
    }

    public void setPhase(SessionLoginPhase phase) {
        this.phase = phase;
        this.lastPhaseChangeNanos = System.nanoTime();
    }

    public boolean isLoginVerified() {
        return loginVerified;
    }

    public void setLoginVerified(boolean loginVerified) {
        this.loginVerified = loginVerified;
    }

    public boolean isLoginPacketReceived() {
        return loginPacketReceived;
    }

    public void setLoginPacketReceived(boolean loginPacketReceived) {
        this.loginPacketReceived = loginPacketReceived;
    }

    public boolean isAwaitingEncryptionHandshake() {
        return awaitingEncryptionHandshake;
    }

    public void setAwaitingEncryptionHandshake(boolean awaitingEncryptionHandshake) {
        this.awaitingEncryptionHandshake = awaitingEncryptionHandshake;
    }

    public boolean isShouldLogin() {
        return shouldLogin;
    }

    public void setShouldLogin(boolean shouldLogin) {
        this.shouldLogin = shouldLogin;
    }

    public long getLastPhaseChangeNanos() {
        return lastPhaseChangeNanos;
    }

    public String getDisconnectCauseHint() {
        return disconnectCauseHint;
    }

    public void setDisconnectCauseHint(String disconnectCauseHint) {
        this.disconnectCauseHint = disconnectCauseHint;
    }
}
