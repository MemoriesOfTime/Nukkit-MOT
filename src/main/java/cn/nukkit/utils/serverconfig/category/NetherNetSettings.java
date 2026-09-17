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

    @Comment("Enabled by default alongside RakNet, set false to disable. Signaling shares the server-port over TCP; WebRTC media uses system-assigned UDP ports")
    @CustomKey("enabled")
    private boolean enabled = true;

    @Comment("Seconds to wait for the WebRTC handshake before dropping a connection")
    @CustomKey("handshake-timeout-seconds")
    private int handshakeTimeoutSeconds = 30;

    @Comment("Extra ICE STUN/TURN servers, e.g. stun:stun.l.google.com:19302. TURN credentials go in the URL: turn:user:pass@host:port?transport=udp")
    @CustomKey("ice-servers")
    private List<String> iceServers = new ArrayList<>();

    @Comment("Public addresses advertised to peers behind a NAT, as \"address:port\" entries")
    @CustomKey("advertise-addresses")
    private List<String> advertiseAddresses = new ArrayList<>();

    @Comment("Operator identity PEM file, generated on first start, keep it. Changing it makes all returning players re-confirm the trust prompt")
    @CustomKey("identity-file")
    private String identityFile = "keys/nethernet-identity.pem";

    @Comment("Operator name in the client trust prompt, empty uses the MOTD")
    @CustomKey("identity-domain")
    private String identityDomain = "";
}
