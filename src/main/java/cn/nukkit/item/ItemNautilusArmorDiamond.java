package cn.nukkit.item;

public class ItemNautilusArmorDiamond extends ItemNautilusArmor {

    public ItemNautilusArmorDiamond() {
        super(DIAMOND_NAUTILUS_ARMOR, "Diamond Nautilus Armor", 11);
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_DIAMOND;
    }
}
