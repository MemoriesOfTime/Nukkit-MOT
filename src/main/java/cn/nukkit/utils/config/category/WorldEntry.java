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
public class WorldEntry extends OkaeriConfig {

    @Comment("World generator type (normal, flat, nether, the_end, void)")
    private String generator = "normal";

    @Comment("World seed (0 = random)")
    private long seed = 0;

    @Comment("Generator settings (e.g. flat layer definition)")
    private String generatorSettings = "";
}
