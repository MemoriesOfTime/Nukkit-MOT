package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Copper Chest base block.
 * <p>
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 */
public class BlockChestCopper extends BlockChest implements Oxidizable, Waxable {

    public BlockChestCopper() {
        this(0);
    }

    public BlockChestCopper(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Copper Chest";
    }

    @Override
    public int getId() {
        return COPPER_CHEST;
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
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public boolean isWaxed() {
        return false;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean onActivate(@NotNull Item item, Player player) {
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
    public boolean setWaxed(boolean waxed) {
        if (isWaxed() == waxed) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(waxed, getOxidizationLevel()), this.getDamage()));
    }

    @Override
    public boolean setOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        if (getOxidizationLevel().equals(oxidizationLevel)) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(isWaxed(), oxidizationLevel), this.getDamage()));
    }

    @Override
    public Block getStateWithOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        return Block.get(getCopperId(isWaxed(), oxidizationLevel), this.getDamage());
    }

    @Override
    public boolean tryPair() {
        BlockEntityChest chest = null;

        if (!(this.getLevel().getBlockEntity(this) instanceof BlockEntityChest blockEntity)) {
            return false;
        }

        for (BlockFace side : BlockFace.Plane.HORIZONTAL) {
            if ((this.getDamage() == 4 || this.getDamage() == 5) && (side == BlockFace.WEST || side == BlockFace.EAST)) {
                continue;
            } else if ((this.getDamage() == 3 || this.getDamage() == 2) && (side == BlockFace.NORTH || side == BlockFace.SOUTH)) {
                continue;
            }
            Block c = this.getSide(side);
            if (c instanceof BlockChestCopper && c.getDamage() == this.getDamage()) {
                BlockEntity entity = this.getLevel().getBlockEntity(c);
                if (entity instanceof BlockEntityChest && !((BlockEntityChest) entity).isPaired()) {
                    chest = (BlockEntityChest) entity;
                    break;
                }
            }
        }

        if (chest != null) {
            chest.pairWith(blockEntity);
            blockEntity.pairWith(chest);
            normalizePairedCopperChest(blockEntity, chest);
            return true;
        }

        return false;
    }

    private void normalizePairedCopperChest(BlockEntityChest first, BlockEntityChest second) {
        if (!(first.getBlock() instanceof BlockChestCopper firstCopper)
                || !(second.getBlock() instanceof BlockChestCopper secondCopper)) {
            return;
        }

        OxidizationLevel firstLevel = firstCopper.getOxidizationLevel();
        OxidizationLevel secondLevel = secondCopper.getOxidizationLevel();
        boolean firstWaxed = firstCopper.isWaxed();
        boolean secondWaxed = secondCopper.isWaxed();

        if (firstLevel == secondLevel && firstWaxed == secondWaxed) {
            return;
        }

        OxidizationLevel targetLevel = firstLevel.ordinal() <= secondLevel.ordinal() ? firstLevel : secondLevel;
        boolean targetWaxed = firstWaxed && secondWaxed;

        convertCopperChest(firstCopper, targetWaxed, targetLevel);
        convertCopperChest(secondCopper, targetWaxed, targetLevel);
    }

    private void convertCopperChest(BlockChestCopper copper, boolean targetWaxed, OxidizationLevel targetLevel) {
        if (copper.getOxidizationLevel() != targetLevel || copper.isWaxed() != targetWaxed) {
            copper.getValidLevel().setBlock(copper, Block.get(getCopperId(targetWaxed, targetLevel), copper.getDamage()));
        }
    }

    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return getId();
        }
        return switch (oxidizationLevel) {
            case UNAFFECTED -> waxed ? WAXED_COPPER_CHEST : COPPER_CHEST;
            case EXPOSED -> waxed ? WAXED_EXPOSED_COPPER_CHEST : EXPOSED_COPPER_CHEST;
            case WEATHERED -> waxed ? WAXED_WEATHERED_COPPER_CHEST : WEATHERED_COPPER_CHEST;
            case OXIDIZED -> waxed ? WAXED_OXIDIZED_COPPER_CHEST : OXIDIZED_COPPER_CHEST;
            default -> getId();
        };
    }
}
