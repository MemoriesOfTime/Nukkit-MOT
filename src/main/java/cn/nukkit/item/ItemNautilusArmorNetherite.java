package cn.nukkit.item;

public class ItemNautilusArmorNetherite extends ItemNautilusArmor {

    public ItemNautilusArmorNetherite() {
        super(NETHERITE_NAUTILUS_ARMOR, "Netherite Nautilus Armor", 19);
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_NETHERITE;
    }
}
