package cn.nukkit.network.session;

import cn.nukkit.Player;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.session.login.NetworkSessionState;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public interface NetworkPlayerSession {

    void sendPacket(DataPacket packet);
    void sendImmediatePacket(DataPacket packet, Runnable callback);

    @Deprecated
    default void flush() {

    }

    void disconnect(String reason);

    Player getPlayer();

    void setCompression(CompressionProvider compression);

    void setCompressionOut(CompressionProvider compression);

    CompressionProvider getCompression();

    default NetworkSessionState getState() {
        return null;
    }

    default void beginLegacyInboundCompressionGraceWindow() {

    }

    default void beginLegacyInboundCompressionGraceWindow(CompressionProvider compression) {
        this.beginLegacyInboundCompressionGraceWindow();
    }

    default void endLegacyInboundCompressionGraceWindow() {

    }

    default void beginLegacyInboundEncryptionGraceWindow() {

    }

    default void endLegacyInboundEncryptionGraceWindow() {

    }

    default void setEncryption(SecretKey encryptionKey, Cipher encryptionCipher, Cipher decryptionCipher) {

    }

    default long getPing() {
        return 0;
    }
}
