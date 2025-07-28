package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.entity.EntityClimateVariant;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemBlueEgg extends ItemEgg implements StringItem {
    public ItemBlueEgg() {
        super(STRING_IDENTIFIED_ITEM, 0, 1);
    }

    @Override
    public String getNamespaceId() {
        return BLUE_EGG;
    }

    @Override
    public String getNamespaceId(GameVersion protocolId) {
        return BLUE_EGG;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_70;
    }

    @Override
    protected void correctNBT(CompoundTag nbt) {
        nbt.putString("variant", EntityClimateVariant.Variant.COLD.getName());
    }
}
