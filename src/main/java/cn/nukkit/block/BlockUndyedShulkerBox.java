package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;
import org.jetbrains.annotations.NotNull;

public class BlockUndyedShulkerBox extends BlockShulkerBox implements BlockEntityHolder<BlockEntityShulkerBox> {

    public BlockUndyedShulkerBox() {
        super(0);
    }

    @Override
    public int getId() {
        return UNDYED_SHULKER_BOX;
    }

    @Override
    public String getName() {
        return "Shulker Box";
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityShulkerBox> getBlockEntityClass() {
        return BlockEntityShulkerBox.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.SHULKER_BOX;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.PURPLE_BLOCK_COLOR;
    }

    @Override
    public DyeColor getDyeColor() {
        return null;
    }

    @Override
    public void setDamage(int meta) {
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntity be = this.getLevel().getBlockEntity(this);

        if (!(be instanceof InventoryHolder)) {
            return 0;
        }

        return ContainerInventory.calculateRedstone(((InventoryHolder) be).getInventory());
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE) {
            this.getLevel().setBlock(this, Block.get(SHULKER_BOX, DyeColor.getByDyeData(item.getDamage()).getWoolData()), true, true);
            return true;
        }

        return super.onActivate(item, player);
    }
}