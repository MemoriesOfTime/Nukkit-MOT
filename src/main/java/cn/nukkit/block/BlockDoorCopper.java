package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Sound;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockDoorCopper extends BlockDoor implements Oxidizable, Waxable {

    public BlockDoorCopper() {
        this(0);
    }

    public BlockDoorCopper(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Copper Door";
    }

    @Override
    public int getId() {
        return COPPER_DOOR;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_STONE;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_STONE) {
            return new Item[]{toItem()};
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ORANGE_BLOCK_COLOR;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (Waxable.super.onActivate(item, player) || Oxidizable.super.onActivate(item, player)) {
            return true;
        }
        return super.onActivate(item, player);
    }

    @Override
    public int onUpdate(int type) {
        int result = Oxidizable.super.onUpdate(type);
        if (result != 0) {
            return result;
        }
        return super.onUpdate(type);
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public void playOpenSound() {
        level.addSound(this, Sound.OPEN_IRON_DOOR);
    }

    @Override
    public void playCloseSound() {
        level.addSound(this, Sound.CLOSE_IRON_DOOR);
    }

    @Override
    public boolean isWaxed() {
        return false;
    }

    @Override
    public boolean setWaxed(boolean waxed) {
        if (isWaxed() == waxed) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(waxed, getOxidizationLevel()), getDamage()));
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean setOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        if (getOxidizationLevel().equals(oxidizationLevel)) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(isWaxed(), oxidizationLevel), getDamage()));
    }

    @Override
    public Block getStateWithOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        return Block.get(getCopperId(isWaxed(), oxidizationLevel), getDamage());
    }

    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return getId();
        }
        switch (oxidizationLevel) {
            case UNAFFECTED:
                return waxed ? WAXED_COPPER_DOOR : COPPER_DOOR;
            case EXPOSED:
                return waxed ? WAXED_EXPOSED_COPPER_DOOR : EXPOSED_COPPER_DOOR;
            case WEATHERED:
                return waxed ? WAXED_WEATHERED_COPPER_DOOR : WEATHERED_COPPER_DOOR;
            case OXIDIZED:
                return waxed ? WAXED_OXIDIZED_COPPER_DOOR : OXIDIZED_COPPER_DOOR;
            default:
                return getId();
        }
    }
}
