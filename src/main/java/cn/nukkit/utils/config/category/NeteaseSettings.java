package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class NeteaseSettings extends OkaeriConfig {

    @Comment("Enable NetEase client support")
    private boolean clientSupport = false;

    @Comment("Only allow NetEase clients")
    private boolean onlyAllowNeteaseClient = false;
}
