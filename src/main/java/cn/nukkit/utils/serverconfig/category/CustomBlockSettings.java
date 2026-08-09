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
public class CustomBlockSettings extends OkaeriConfig {

    @Comment({"",
        "Automatically download vanilla block palettes (vanilla_palette_*.nbt) from the",
        "official mirror when missing. Required for custom block support across multiple",
        "client protocol versions. Files are cached in the bin/ folder and verified with SHA256.",
        "If disabled, you must manually prepare the files in the bin/ folder."})
    @CustomKey("auto-download-vanilla-palette")
    private boolean autoDownloadVanillaPalette = true;
}
