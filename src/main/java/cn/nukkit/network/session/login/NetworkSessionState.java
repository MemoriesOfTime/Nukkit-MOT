package cn.nukkit.network.session.login;

/**
 * Aggregated mutable session state used during connection and login.
 */
public class NetworkSessionState {

    private final SessionConnectionState connection = new SessionConnectionState();
    private final SessionLoginContext login = new SessionLoginContext();
    private final SessionProtocolState protocol = new SessionProtocolState();
    private final SessionSecurityState security = new SessionSecurityState();

    public SessionConnectionState getConnection() {
        return connection;
    }

    public SessionLoginContext getLogin() {
        return login;
    }

    public SessionProtocolState getProtocol() {
        return protocol;
    }

    public SessionSecurityState getSecurity() {
        return security;
    }
}
