package cn.nukkit.utils.serverconfig.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * NetherNet（WebRTC）传输设置，对应 nukkit-mot.yml 的 network-settings.nethernet 段。
 * NetherNet (WebRTC) transport settings under network-settings.nethernet.
 * <p>
 * 与 RakNet 并行运行：旧客户端继续走 RakNet，受限网络下的 1.21.90+ 客户端经 HTTP 信令 + WebRTC 接入。
 * Runs alongside RakNet; restricted-network clients 1.21.90+ join over HTTP signaling + WebRTC.
 */
@Getter
@Setter
@Accessors(fluent = true)
public class NetherNetSettings extends OkaeriConfig {

    @Comment("Enabled by default alongside RakNet, set false to disable. Signaling shares the server-port over TCP; WebRTC media also shares server-port by default via server.properties server-udp-ports (default 19132, relayed in-process from RakNet's socket; 19134 pins a standalone media port, 0 reverts to system-assigned)")
    @CustomKey("enabled")
    private boolean enabled = true;

    @Comment("Extra ICE STUN servers, e.g. stun:stun.l.google.com:19302. TURN is no longer supported upstream")
    @CustomKey("ice-servers")
    private List<String> iceServers = new ArrayList<>();

    @Comment("Operator identity PEM file, generated on first start, keep it. Changing it makes all returning players re-confirm the trust prompt")
    @CustomKey("identity-file")
    private String identityFile = "keys/nethernet-identity.pem";

    @Comment("Operator name in the client trust prompt, empty uses the MOTD")
    @CustomKey("identity-domain")
    private String identityDomain = "";

    @Comment("TLS certificate for the signaling port: a PEM chain (leaf first) or a PKCS12 file, resolved against the server root. Players joining over HTTPS do not see the first-use trust prompt, and iOS clients cannot join without it. Leave empty to serve plaintext")
    @CustomKey("https-certificate")
    private String httpsCertificate = "";

    @Comment("PEM private key for https-certificate. Leave empty when https-certificate is a PKCS12 file")
    @CustomKey("https-private-key")
    private String httpsPrivateKey = "";

    @Comment("Password for the PKCS12 file or an encrypted PEM private key. Supports file:/path/to/password to read it from a file instead")
    @CustomKey("https-password")
    private String httpsPassword = "";

    @Comment("Once a certificate is configured, answer plaintext signaling with HTTP 426 instead of serving it. Set false to keep accepting plaintext alongside TLS on the same port")
    @CustomKey("https-reject-plaintext")
    private boolean httpsRejectPlaintext = true;
}
