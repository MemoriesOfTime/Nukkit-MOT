package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class DebugSettings extends OkaeriConfig {

    @Comment("Debug level (1=errors only, 2=warnings, 3=info)")
    private int debugLevel = 1;

    @Comment("Enable ANSI colors in terminal title")
    private boolean ansiTitle = false;

    @Comment("Show verbose deprecation warnings")
    private boolean deprecatedVerbose = true;

    @Comment("Call DataPacketSendEvent")
    private boolean callDataPkSendEvent = true;

    @Comment("Call BatchPacketSendEvent")
    private boolean callBatchPkSendEvent = true;

    @Comment("Call EntityMotionEvent")
    private boolean callEntityMotionEvent = true;

    @Comment("Enable block listener")
    private boolean blockListener = true;

    @Comment("Enable automatic bug reporting (Sentry)")
    private boolean automaticBugReport = true;

    @Comment("Show update notifications")
    private boolean updateNotifications = true;

    @Comment("Enable bStats metrics")
    private boolean bstatsMetrics = true;

    @Comment("Hastebin API token for paste uploads")
    private String hastebinToken = "";
}
