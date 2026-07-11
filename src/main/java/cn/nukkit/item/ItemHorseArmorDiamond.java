package cn.nukkit.item;

public class ItemHorseArmorDiamond extends Item {

    public ItemHorseArmorDiamond() {
        this(0, 1);
    }

    public ItemHorseArmorDiamond(Integer meta) {
        this(meta, 1);
    }

    public ItemHorseArmorDiamond(Integer meta, int count) {
        super(DIAMOND_HORSE_ARMOR, meta, count, "Diamond Horse Armor");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isHorseArmor() {
        return true;
    }

    @Override
    public int getArmorPoints() {
        return 11;
    }
}
