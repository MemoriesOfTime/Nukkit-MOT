package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class NeteaseSettings extends OkaeriConfig {

    @Comment("Enable NetEase client support")
    private boolean clientSupport = false;

    @Comment("Only allow NetEase clients")
    private boolean onlyAllowNeteaseClient = false;
}
