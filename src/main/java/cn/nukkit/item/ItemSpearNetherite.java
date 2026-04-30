package cn.nukkit.item;

public class ItemSpearNetherite extends ItemSpear {

    public ItemSpearNetherite() {
        super(NETHERITE_SPEAR, "Netherite Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_NETHERITE;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_NETHERITE;
    }

    @Override
    public int getAttackDamage() {
        return 6;
    }
}
