package cn.nukkit.network.session.login;

/**
 * Session-scoped transport establishment and cookie observability state.
 */
public class SessionConnectionState {

    private volatile long sessionCreatedNanos = System.nanoTime();
    private volatile long childChannelAcceptedNanos = sessionCreatedNanos;
    private volatile long queuedForPlayerCreationNanos;
    private volatile long playerCreatedNanos;
    private volatile long playerBoundNanos;
    private volatile String remoteAddress;
    private volatile String rakCookieMode;
    private volatile boolean queuedForPlayerCreation;
    private volatile boolean playerCreated;
    private volatile boolean playerBound;
    private volatile long stateChangedNanos = sessionCreatedNanos;

    public long getSessionCreatedNanos() {
        return sessionCreatedNanos;
    }

    public void setSessionCreatedNanos(long sessionCreatedNanos) {
        this.sessionCreatedNanos = sessionCreatedNanos;
        this.stateChangedNanos = System.nanoTime();
    }

    public long getChildChannelAcceptedNanos() {
        return childChannelAcceptedNanos;
    }

    public void setChildChannelAcceptedNanos(long childChannelAcceptedNanos) {
        this.childChannelAcceptedNanos = childChannelAcceptedNanos;
        this.stateChangedNanos = System.nanoTime();
    }

    public long getQueuedForPlayerCreationNanos() {
        return queuedForPlayerCreationNanos;
    }

    public void setQueuedForPlayerCreationNanos(long queuedForPlayerCreationNanos) {
        this.queuedForPlayerCreationNanos = queuedForPlayerCreationNanos;
        this.stateChangedNanos = System.nanoTime();
    }

    public long getPlayerCreatedNanos() {
        return playerCreatedNanos;
    }

    public void setPlayerCreatedNanos(long playerCreatedNanos) {
        this.playerCreatedNanos = playerCreatedNanos;
        this.stateChangedNanos = System.nanoTime();
    }

    public long getPlayerBoundNanos() {
        return playerBoundNanos;
    }

    public void setPlayerBoundNanos(long playerBoundNanos) {
        this.playerBoundNanos = playerBoundNanos;
        this.stateChangedNanos = System.nanoTime();
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
        this.stateChangedNanos = System.nanoTime();
    }

    public String getRakCookieMode() {
        return rakCookieMode;
    }

    public void setRakCookieMode(String rakCookieMode) {
        this.rakCookieMode = rakCookieMode;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isQueuedForPlayerCreation() {
        return queuedForPlayerCreation;
    }

    public void setQueuedForPlayerCreation(boolean queuedForPlayerCreation) {
        this.queuedForPlayerCreation = queuedForPlayerCreation;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isPlayerCreated() {
        return playerCreated;
    }

    public void setPlayerCreated(boolean playerCreated) {
        this.playerCreated = playerCreated;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isPlayerBound() {
        return playerBound;
    }

    public void setPlayerBound(boolean playerBound) {
        this.playerBound = playerBound;
        this.stateChangedNanos = System.nanoTime();
    }

    public long getStateChangedNanos() {
        return stateChangedNanos;
    }
}
