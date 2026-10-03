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
public class WorldEntry extends OkaeriConfig {

    @Comment("Maximum changed chunks per autosave DB write (1..64); snapshots remain in the same tick")
    @CustomKey("save-batch-chunks")
    private int saveBatchChunks = 16;

    @Comment("Target bytes per autosave DB write (65536..16777216); one oversized chunk is written alone")
    @CustomKey("save-batch-bytes")
    private int saveBatchBytes = 4 * 1024 * 1024;

    @Comment("World generator type (normal, flat, nether, the_end, void)")
    private String generator = "normal";

    @Comment("World seed (0 = random)")
    private long seed = 0;

    @Comment("Generator settings (e.g. flat layer definition)")
    @CustomKey("generator-settings")
    private String generatorSettings = "";
}
