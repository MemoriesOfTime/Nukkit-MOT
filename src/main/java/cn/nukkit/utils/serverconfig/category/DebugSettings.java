package cn.nukkit.utils.serverconfig.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class DebugSettings extends OkaeriConfig {

    @Comment("Debug level (1=errors only, 2=warnings, 3=info)")
    @CustomKey("debug-level")
    private int debugLevel = 1;

    @Comment("Enable ANSI colors in terminal title")
    @CustomKey("ansi-title")
    private boolean ansiTitle = false;

    @Comment("Show verbose deprecation warnings")
    @CustomKey("deprecated-verbose")
    private boolean deprecatedVerbose = true;

    @Comment("Call DataPacketSendEvent")
    @CustomKey("call-data-pk-send-event")
    private boolean callDataPkSendEvent = true;

    @Comment("Call BatchPacketSendEvent")
    @CustomKey("call-batch-pk-send-event")
    private boolean callBatchPkSendEvent = true;

    @Comment("Call EntityMotionEvent")
    @CustomKey("call-entity-motion-event")
    private boolean callEntityMotionEvent = true;

    @Comment("Enable block listener")
    @CustomKey("block-listener")
    private boolean blockListener = true;

    @Comment("Enable automatic bug reporting (Sentry)")
    @CustomKey("automatic-bug-report")
    private boolean automaticBugReport = true;

    @Comment("Show update notifications")
    @CustomKey("update-notifications")
    private boolean updateNotifications = true;

    @Comment("Enable bStats metrics")
    @CustomKey("bstats-metrics")
    private boolean bstatsMetrics = true;

    @Comment("Hastebin API token for paste uploads")
    @CustomKey("hastebin-token")
    private String hastebinToken = "";
}
