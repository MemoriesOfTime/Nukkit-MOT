package cn.nukkit.item;

public class ItemSpearIron extends ItemSpear {

    public ItemSpearIron() {
        super(IRON_SPEAR, "Iron Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_IRON;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_IRON;
    }

    @Override
    public int getAttackDamage() {
        return 4;
    }
}
