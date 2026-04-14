package cn.nukkit.network.session.login;

/**
 * Session-level login phases.
 */
public enum SessionLoginPhase {
    CONNECTED,
    NETWORK_SETTINGS_NEGOTIATED,
    LOGIN_RECEIVED,
    ENCRYPTION_REQUEST_SENT,
    AWAITING_ENCRYPTION_RESPONSE,
    ENCRYPTION_RESPONSE_RECEIVED,
    PRE_LOGIN,
    RESOURCE_PACK,
    READY_TO_LOGIN,
    LOGGED_IN,
    DISCONNECTED
}
