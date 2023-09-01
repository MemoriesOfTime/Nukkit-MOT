package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public abstract class StringItemBase extends Item implements StringItem {
    private final String id;

    public StringItemBase(@NotNull String id, @Nullable String name) {
        super(STRING_IDENTIFIED_ITEM, 0, 1, StringItem.notEmpty(name));
        Preconditions.checkNotNull(id, "id can't be null");
        Preconditions.checkArgument(id.contains(":"), "The ID must be a namespaced ID, like minecraft:stone");
        this.id = id;
        clearNamedTag();
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
        return protocolId >= ProtocolInfo.v1_16_100;
    }
}
