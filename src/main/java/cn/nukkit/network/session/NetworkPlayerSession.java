package cn.nukkit.network.session;

import cn.nukkit.Player;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.session.login.NetworkSessionState;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public interface NetworkPlayerSession {

    enum ImmediatePacketMode {
        QUEUED_FLUSH,
        DIRECT_WRITE
    }

    void sendPacket(DataPacket packet);
    default void sendImmediatePacket(DataPacket packet, Runnable callback) {
        this.sendImmediatePacket(packet, callback, ImmediatePacketMode.QUEUED_FLUSH);
    }

    void sendImmediatePacket(DataPacket packet, Runnable callback, ImmediatePacketMode mode);

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
