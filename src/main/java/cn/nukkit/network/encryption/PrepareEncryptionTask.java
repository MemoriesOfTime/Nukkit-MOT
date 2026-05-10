package cn.nukkit.network.encryption;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.scheduler.AsyncTask;
import com.nimbusds.jose.jwk.Curve;
import lombok.Getter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

public class PrepareEncryptionTask extends AsyncTask {

    private final Player player;
    @Getter
    private String handshakeJwt;
    @Getter
    private SecretKey encryptionKey;
    @Getter
    private Cipher encryptionCipher;
    @Getter
    private Cipher decryptionCipher;

    public PrepareEncryptionTask(Player player) {
        this.player = player;
    }

    @Override
    public void onRun() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(Curve.P_384.toECParameterSpec());
            KeyPair serverKeyPair = generator.generateKeyPair();

            byte[] token = EncryptionUtils.generateRandomToken();
            this.encryptionKey = EncryptionUtils.getSecretKey(serverKeyPair.getPrivate(), EncryptionUtils.parseKey(this.player.getLoginChainData().getIdentityPublicKey()), token);
            this.handshakeJwt = EncryptionUtils.createHandshakeJwt(serverKeyPair, token);

            boolean useGcm = this.player.protocol > ProtocolInfo.v1_16_210;
            this.encryptionCipher = EncryptionUtils.createCipher(useGcm, true, this.encryptionKey);
            this.decryptionCipher = EncryptionUtils.createCipher(useGcm, false, this.encryptionKey);
        } catch (Exception ex) {
            this.player.getServer().getLogger().error("Failed to prepare encryption", ex);
        }
    }
}
