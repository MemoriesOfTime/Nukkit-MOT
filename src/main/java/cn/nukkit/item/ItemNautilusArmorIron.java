package cn.nukkit.item;

public class ItemNautilusArmorIron extends ItemNautilusArmor {

    public ItemNautilusArmorIron() {
        super(IRON_NAUTILUS_ARMOR, "Iron Nautilus Armor", 5);
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_IRON;
    }
}
