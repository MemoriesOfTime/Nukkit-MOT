package cn.nukkit.item;

public class ItemSpearWood extends ItemSpear {

    public ItemSpearWood() {
        super(WOODEN_SPEAR, "Wooden Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_WOODEN;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public int getAttackDamage() {
        return 2;
    }
}
