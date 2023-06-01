package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.Faceable;

public class BlockObserver extends BlockSolidMeta implements Faceable {

    public BlockObserver() {
        this(0);
    }

    public BlockObserver(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return OBSERVER;
    }

    @Override
    public String getName() {
        return "Observer";
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
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            return new Item[]{Item.get(OBSERVER, 0, 1)};
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
    
    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (player != null) {
            if (Math.abs(player.getFloorX() - this.x) <= 1 && Math.abs(player.getFloorZ() - this.z) <= 1) {
                double y = player.y + player.getEyeHeight();
                if (y - this.y > 2) {
                    this.setBlockFace(BlockFace.DOWN);
                } else if (this.y - y > 0) {
                    this.setBlockFace(BlockFace.UP);
                } else {
                    this.setBlockFace(player.getHorizontalFacing());
                }
            } else {
                this.setBlockFace(player.getHorizontalFacing());
            }
        } else {
            this.setBlockFace(BlockFace.DOWN);
        }
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }

    public void setBlockFace(BlockFace face) {
        this.setDamage(face.getIndex());
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(OBSERVER));
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }

    @Override
    public int getStrongPower(BlockFace blockFace) {
        return this.isPowered() && blockFace == this.getBlockFace() ? 15 : 0;
    }

    @Override
    public int getWeakPower(BlockFace blockFace) {
        return this.getStrongPower(blockFace);
    }

    @Override
    public int onUpdate(int type) {
        return super.onUpdate(type);
        //TODO 实现功能
        /*if (type == Level.BLOCK_UPDATE_NORMAL && !this.isPowered()) {
            RedstoneUpdateEvent redstoneUpdateEvent = new RedstoneUpdateEvent(this);
            this.level.getServer().getPluginManager().callEvent(redstoneUpdateEvent);
            if (redstoneUpdateEvent.isCancelled()) {
                return 0;
            }

            this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 0, 15));
            this.setPowered(true);
            if (this.level.setBlock(this, this, false, false)) {
                this.level.updateAroundRedstone(this, this.getBlockFace());
                level.scheduleUpdate(this, 5);
            }

            return 1;
        }
        if (type == Level.BLOCK_UPDATE_SCHEDULED && this.isPowered()) {
            RedstoneUpdateEvent redstoneUpdateEvent = new RedstoneUpdateEvent(this);
            this.level.getServer().getPluginManager().callEvent(redstoneUpdateEvent);
            if (redstoneUpdateEvent.isCancelled()) {
                return 0;
            }

            this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 0, 15));
            this.setPowered(false);
            this.level.setBlock(this, this, false, false);
            this.level.updateAroundRedstone(this, this.getBlockFace());
        }

        return type;*/
    }

    public boolean isPowered() {
        return (this.getDamage() & 8) == 8;
    }

    public void setPowered(boolean powered) {
        this.setDamage(this.getDamage() & 7 | (powered ? 8 : 0));
    }
}
