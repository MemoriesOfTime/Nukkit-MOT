package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;

import cn.nukkit.block.properties.VanillaProperties;

/**
 * Created on 2015/12/8 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockPumpkin extends BlockSolidMeta implements Faceable {

    public BlockPumpkin() {
        this(0);
    }

    public BlockPumpkin(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Pumpkin";
    }

    @Override
    public int getId() {
        return PUMPKIN;
    }

    @Override
    public double getHardness() {
        return 1;
    }

    @Override
    public double getResistance() {
        return 5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (!item.isShears()) {
            return false;
        }

        BlockCarvedPumpkin carvedPumpkin = new BlockCarvedPumpkin();
        carvedPumpkin.setBlockFace(this.getBlockFace());
        item.useOn(this);
        this.level.setBlock(this, carvedPumpkin, true, true);
        this.getLevel().dropItem(add(0.5, 0.5, 0.5), Item.get(ItemID.PUMPKIN_SEEDS));
        return true;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setBlockFace(player != null ? player.getDirection().getOpposite() : BlockFace.SOUTH);
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public boolean onBreak(Item item) {
        for (BlockFace face : BlockFace.Plane.HORIZONTAL) {
            Block block = this.getSide(face);
            if (block instanceof BlockStemPumpkin stemPumpkin) {
                if (stemPumpkin.getBlockFace() == face.getOpposite()) {
                    stemPumpkin.setPropertyValue(VanillaProperties.FACING_DIRECTION, BlockFace.DOWN);
                    this.getLevel().setBlock(stemPumpkin, stemPumpkin, true, true);
                }
            }
        }

        return super.onBreak(item);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ORANGE_BLOCK_COLOR;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }

    @Override
    public void setBlockFace(BlockFace blockFace) {
        this.setDamage(blockFace.getHorizontalIndex());
    }

    @Override
    public boolean breaksWhenMoved() {
        return true;
    }

    @Override
    public boolean sticksToPiston() {
        return false;
    }
}
