package cn.nukkit.item;

public class ItemNautilusArmorCopper extends ItemNautilusArmor {

    public ItemNautilusArmorCopper() {
        super(COPPER_NAUTILUS_ARMOR, "Copper Nautilus Armor", 4);
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_COPPER;
    }
}
