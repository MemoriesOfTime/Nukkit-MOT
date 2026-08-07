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
public class NeteaseSettings extends OkaeriConfig {

    @Comment("Enable NetEase client support")
    @CustomKey("client-support")
    private boolean clientSupport = false;

    @Comment("Only allow NetEase clients")
    @CustomKey("only-allow-netease-client")
    private boolean onlyAllowNeteaseClient = false;
}
