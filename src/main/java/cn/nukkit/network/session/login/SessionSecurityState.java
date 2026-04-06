package cn.nukkit.network.session.login;

import cn.nukkit.network.CompressionProvider;

/**
 * Session-scoped transport security and compression state.
 */
public class SessionSecurityState {

    private volatile CompressionProvider compressionIn;
    private volatile CompressionProvider compressionOut;
    private volatile CompressionProvider legacyInboundCompression;
    private volatile boolean compressionInitialized;
    private volatile boolean prefixedCompression;
    private volatile boolean encryptionEnabled;
    private volatile boolean legacyInboundEncryptionGraceWindow;
    private volatile boolean legacyInboundGraceWindow;
    private volatile long stateChangedNanos = System.nanoTime();

    public CompressionProvider getCompressionIn() {
        return compressionIn;
    }

    public void setCompressionIn(CompressionProvider compressionIn) {
        this.compressionIn = compressionIn;
        this.stateChangedNanos = System.nanoTime();
    }

    public CompressionProvider getCompressionOut() {
        return compressionOut;
    }

    public void setCompressionOut(CompressionProvider compressionOut) {
        this.compressionOut = compressionOut;
        this.stateChangedNanos = System.nanoTime();
    }

    public CompressionProvider getLegacyInboundCompression() {
        return legacyInboundCompression;
    }

    public void setLegacyInboundCompression(CompressionProvider legacyInboundCompression) {
        this.legacyInboundCompression = legacyInboundCompression;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isCompressionInitialized() {
        return compressionInitialized;
    }

    public void setCompressionInitialized(boolean compressionInitialized) {
        this.compressionInitialized = compressionInitialized;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isPrefixedCompression() {
        return prefixedCompression;
    }

    public void setPrefixedCompression(boolean prefixedCompression) {
        this.prefixedCompression = prefixedCompression;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public void setEncryptionEnabled(boolean encryptionEnabled) {
        this.encryptionEnabled = encryptionEnabled;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isLegacyInboundEncryptionGraceWindow() {
        return legacyInboundEncryptionGraceWindow;
    }

    public void setLegacyInboundEncryptionGraceWindow(boolean legacyInboundEncryptionGraceWindow) {
        this.legacyInboundEncryptionGraceWindow = legacyInboundEncryptionGraceWindow;
        this.stateChangedNanos = System.nanoTime();
    }

    public boolean isLegacyInboundGraceWindow() {
        return legacyInboundGraceWindow;
    }

    public void setLegacyInboundGraceWindow(boolean legacyInboundGraceWindow) {
        this.legacyInboundGraceWindow = legacyInboundGraceWindow;
        this.stateChangedNanos = System.nanoTime();
    }

    public long getStateChangedNanos() {
        return stateChangedNanos;
    }
}
