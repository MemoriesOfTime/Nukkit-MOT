package cn.nukkit.item.customitem;

import cn.nukkit.item.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lt_name
 */
public abstract class ItemCustomProjectile extends StringItemProjectileBase implements CustomItem {

    private final String textureName;

    public ItemCustomProjectile(@NotNull String id, @Nullable String name) {
        super(id, StringItem.notEmpty(name));
        this.textureName = name;
    }

    public ItemCustomProjectile(@NotNull String id, @Nullable String name, @NotNull String textureName) {
        super(id, StringItem.notEmpty(name));
        this.textureName = textureName;
    }

    @Override
    public String getTextureName() {
        return textureName;
    }
}