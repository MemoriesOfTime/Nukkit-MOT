package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class NetworkSettings extends OkaeriConfig {

    @Comment("ZLIB compression provider (2 recommended)")
    private int zlibProvider = 2;

    @Comment("Compression level (1-9, higher = more CPU, smaller packets)")
    private int compressionLevel = 5;

    @Comment("Compression threshold in bytes")
    private int compressionThreshold = 256;

    @Comment("Use Snappy compression instead of ZLIB")
    private boolean useSnappyCompression = false;

    @Comment("RakNet packet limit per tick")
    private int rakPacketLimit = 1000000;

    @Comment("Enable RakNet cookie validation")
    private boolean enableRakSendCookie = true;

    @Comment("Client timeout in milliseconds")
    private int timeoutMilliseconds = 25000;

    @Comment("Show plugin list in query response")
    private boolean queryPlugins = false;

    @Comment("Enable WaterDog proxy mode")
    private boolean useWaterdog = false;
}
