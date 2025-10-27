package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

public class BlockStonecutterBlock extends BlockSolidMeta implements Faceable {

    public BlockStonecutterBlock() {
        this(0);
    }

    public BlockStonecutterBlock(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Stonecutter Block";
    }

    @Override
    public int getId() {
        return STONECUTTER_BLOCK;
    }

    @Override
    public double getHardness() {
        return 3.5;
    }

    @Override
    public double getResistance() {
        return 17.5;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{toItem()};
    }

    @Override
    public Item toItem() {
        return new ItemBlock(new BlockStonecutterBlock());
    }

    @Override
    public double getMaxY() {
        return y + 0.5625;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(Block.FACES2534[player != null ? player.getDirection().getHorizontalIndex() : 0]);
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    //TODO: Add Stonecutter functionality
    /*@Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (player != null) {
            player.addWindow(new StonecutterInventory(player.getUIInventory(), this), Player.STONECUTTER_WINDOW_ID);
        }
        return true;
    }*/

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }
}