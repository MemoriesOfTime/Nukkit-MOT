package cn.nukkit.item;

public class ItemSpearGold extends ItemSpear {

    public ItemSpearGold() {
        super(GOLDEN_SPEAR, "Golden Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_GOLD;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_GOLD;
    }

    @Override
    public int getAttackDamage() {
        return 2;
    }
}
