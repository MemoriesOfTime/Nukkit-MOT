package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class WorldEntry extends OkaeriConfig {

    @Comment("World generator type (normal, flat, nether, the_end, void)")
    private String generator = "normal";

    @Comment("World seed (0 = random)")
    private long seed = 0;

    @Comment("Generator settings (e.g. flat layer definition)")
    private String generatorSettings = "";
}
