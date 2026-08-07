package cn.nukkit.item;

public class ItemSpearDiamond extends ItemSpear {

    public ItemSpearDiamond() {
        super(DIAMOND_SPEAR, "Diamond Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_DIAMOND;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_DIAMOND;
    }

    @Override
    public int getAttackDamage() {
        return 5;
    }
}
