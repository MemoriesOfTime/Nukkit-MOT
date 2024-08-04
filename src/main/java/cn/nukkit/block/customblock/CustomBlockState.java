package cn.nukkit.block.customblock;

import cn.nukkit.block.customblock.container.BlockContainerFactory;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.Data;
import org.cloudburstmc.nbt.NbtMap;

@Data
public class CustomBlockState {
    private final String identifier;
    private final int legacyId;
    private final NbtMap blockState;
    private final BlockContainerFactory factory;
    private CompoundTag nukkitBlockState;

    public CompoundTag getNukkitBlockState() {
        if (this.nukkitBlockState == null) {
            this.nukkitBlockState = CustomBlockManager.convertNbtMap(this.blockState);
        }
        return this.nukkitBlockState;
    }
}