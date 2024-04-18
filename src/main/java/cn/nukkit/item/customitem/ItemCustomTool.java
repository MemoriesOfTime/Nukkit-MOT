package cn.nukkit.item.customitem;

import cn.nukkit.item.*;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lt_name
 */
public abstract class ItemCustomTool extends StringItemToolBase implements ItemDurable, CustomItem {

    private final String textureName;

    public ItemCustomTool(@NotNull String id, @Nullable String name) {
        super(id, StringItem.notEmpty(name));
        this.textureName = name;
    }

    public ItemCustomTool(@NotNull String id, @Nullable String name, @NotNull String textureName) {
        super(id, StringItem.notEmpty(name));
        this.textureName = textureName;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_WOODEN;
    }

    @Override
    public String getTextureName() {
        return textureName;
    }

    @Nullable
    public final Integer getSpeed() {
        var nbt = Item.getCustomItemDefinition().get(this.getNamespaceId()).getNbt(ProtocolInfo.CURRENT_PROTOCOL);
        if (nbt == null || !nbt.getCompound("components").contains("minecraft:digger")) return null;
        return nbt.getCompound("components")
                .getCompound("minecraft:digger")
                .getList("destroy_speeds", CompoundTag.class).get(0).getInt("speed");
    }
}
