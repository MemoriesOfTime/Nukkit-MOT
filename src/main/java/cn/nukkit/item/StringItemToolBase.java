package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author glorydark
 */
public class StringItemToolBase extends ItemTool implements ItemDurable, StringItem {

    private final String id;

    public StringItemToolBase(@NotNull String id, @Nullable String name) {
        super(STRING_IDENTIFIED_ITEM, 0, 1, StringItem.notEmpty(name));
        Preconditions.checkNotNull(id, "id can't be null");
        Preconditions.checkArgument(id.contains(":"), "The ID must be a namespaced ID, like minecraft:stone");
        this.id = id;
        clearNamedTag();
    }

    @Override
    public boolean isTool() {
        return true;
    }

    /**
     * No damage to item when it's used to attack entities
     *
     * @return whether the item should take damage when used to attack entities
     */
    @Override
    public boolean noDamageOnAttack() {
        return true;
    }

    /**
     * No damage to item when it's used to break blocks
     *
     * @return whether the item should take damage when used to break blocks
     */
    @Override
    public boolean noDamageOnBreak() {
        return true;
    }

    @Override
    public String getNamespaceId() {
        return this.id;
    }

    @Override
    public String getNamespaceId(int protocolId) {
        return this.getNamespaceId();
    }

    @Override
    public final int getId() {
        return StringItem.super.getId();
    }

    @Override
    public StringItemBase clone() {
        return (StringItemBase) super.clone();
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_20_0;
    }
}
