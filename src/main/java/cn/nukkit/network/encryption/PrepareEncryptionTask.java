package cn.nukkit.network.encryption;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.scheduler.AsyncTask;
import com.nimbusds.jose.jwk.Curve;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

public class PrepareEncryptionTask extends AsyncTask {

    private final Player player;

    private String handshakeJwt;
    private SecretKey encryptionKey;
    private Cipher encryptionCipher;
    private Cipher decryptionCipher;

    public PrepareEncryptionTask(Player player) {
        this.player = player;
    }

    @Override
    public void onRun() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(Curve.P_384.toECParameterSpec());
            KeyPair privateKeyPair = generator.generateKeyPair();

            byte[] token = EncryptionUtils.generateRandomToken();

            this.encryptionKey = EncryptionUtils.getSecretKey(privateKeyPair.getPrivate(), EncryptionUtils.generateKey(this.player.getLoginChainData().getIdentityPublicKey()), token);
            this.handshakeJwt = EncryptionUtils.createHandshakeJwt(privateKeyPair, token).serialize();

            boolean useGcm = this.player.protocol > ProtocolInfo.v1_16_210;
            this.encryptionCipher = EncryptionUtils.createCipher(useGcm, true, this.encryptionKey);
            this.decryptionCipher = EncryptionUtils.createCipher(useGcm, false, this.encryptionKey);
        } catch (Exception e) {
            this.player.getServer().getLogger().error("Failed to prepare encryption", e);
        }
    }

    @Override
    public void onCompletion(Server server) {

    }

    public String getHandshakeJwt() {
        return this.handshakeJwt;
    }

    public SecretKey getEncryptionKey() {
        return this.encryptionKey;
    }

    public Cipher getEncryptionCipher() {
        return this.encryptionCipher;
    }

    public Cipher getDecryptionCipher() {
        return this.decryptionCipher;
    }
}
