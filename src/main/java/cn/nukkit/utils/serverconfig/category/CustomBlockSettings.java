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

    @Comment({"",
        "Send hashed block network IDs (blockNetworkIdsHashed) to 1.19.80+ clients,",
        "including NetEase clients, instead of legacy runtime IDs. Enabled by default",
        "even without custom blocks; hashed IDs are persistent across versions.",
        "No effect on protocol < 1.19.80. Always forced on when custom blocks are registered."})
    @CustomKey("use-hashed-block-network-ids")
    private boolean useHashedBlockNetworkIds = true;
}
