package cn.nukkit.utils.serverconfig.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cloudburstmc.netty.channel.raknet.RakConstants;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Accessors(fluent = true)
public class NetworkSettings extends OkaeriConfig {

    @Comment("ZLIB compression provider (2 recommended)")
    @CustomKey("zlib-provider")
    private int zlibProvider = 2;

    @Comment("Compression level (1-9, higher = more CPU, smaller packets)")
    @CustomKey("compression-level")
    private int compressionLevel = 5;

    @Comment("Chunk compression level (1-9, higher = more CPU, smaller chunks)")
    @CustomKey("chunk-compression-level")
    private int chunkCompressionLevel = 7;

    @Comment("Compression threshold in bytes")
    @CustomKey("compression-threshold")
    private int compressionThreshold = 256;

    @Comment("Use Snappy compression instead of ZLIB")
    @CustomKey("use-snappy-compression")
    private boolean useSnappyCompression = false;

    @Comment("RakNet packet limit per tick")
    @CustomKey("rak-packet-limit")
    private int rakPacketLimit = RakConstants.DEFAULT_PACKET_LIMIT;

    @Comment("RakNet cookie mode (active, offloaded, offloaded_psk, off, none, stateless)")
    @CustomKey("rak-cookie-mode")
    private String rakCookieMode = "active";

    @Comment("Client timeout in milliseconds (reserved, not yet applied)")
    @CustomKey("timeout-milliseconds")
    private int timeoutMilliseconds = 25000;

    @Comment("Show plugin list in query response")
    @CustomKey("query-plugins")
    private boolean queryPlugins = false;

    @Comment("Enable WaterDog proxy mode")
    @CustomKey("use-waterdog")
    private boolean useWaterdog = false;

    @Comment("ViaProxy Java Edition player username prefix")
    @CustomKey("viaproxy-username-prefix")
    private String viaProxyUsernamePrefix = "";

    @Comment("Enable Proxy Protocol v2 for UDP proxies (e.g. FRP). Whitelisted sources must send a valid PPv2 header; non-whitelisted sources are treated as direct clients")
    @CustomKey("enable-proxy-protocol")
    private boolean enableProxyProtocol = false;

    @Comment("Whitelisted proxy source IP/CIDR entries for Proxy Protocol. Use proxy addresses, not player addresses. Headerless or invalid packets from whitelisted sources are dropped")
    @CustomKey("proxy-protocol-whitelist")
    private List<String> proxyProtocolWhitelist = new ArrayList<>(List.of("127.0.0.1/32"));
}
