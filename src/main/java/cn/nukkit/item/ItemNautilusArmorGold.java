package cn.nukkit.item;

public class ItemNautilusArmorGold extends ItemNautilusArmor {

    public ItemNautilusArmorGold() {
        super(GOLDEN_NAUTILUS_ARMOR, "Golden Nautilus Armor", 7);
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_GOLD;
    }
}
