package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockObserver extends BlockSolidMeta implements Faceable {

    private static final int FACE_BIT = 0x7; //0111
    private static final int POWERED_BIT = 0x8; //1000

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
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
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
        return BlockFace.fromIndex(this.getDamage() & FACE_BIT);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setDamage(face.getIndex() & FACE_BIT | this.getDamage() & POWERED_BIT);
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
        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            RedstoneUpdateEvent ev = new RedstoneUpdateEvent(this);
            PluginManager pluginManager = level.getServer().getPluginManager();
            pluginManager.callEvent(ev);
            if (ev.isCancelled()) {
                return 0;
            }

            if (!isPowered()) {
                pluginManager.callEvent(new BlockRedstoneEvent(this, 0, 15));
                this.setPowered(true);

                if (this.level.setBlock(this, this)) {
                    getSide(getBlockFace().getOpposite()).onUpdate(Level.BLOCK_UPDATE_REDSTONE);
                    this.level.updateAroundRedstone(this, this.getBlockFace().getOpposite());
                    this.level.scheduleUpdate(this, 2);
                }
            } else {
                pluginManager.callEvent(new BlockRedstoneEvent(this, 15, 0));
                this.setPowered(false);

                this.level.setBlock(this, this);
                getSide(getBlockFace().getOpposite()).onUpdate(Level.BLOCK_UPDATE_REDSTONE);
                this.level.updateAroundRedstone(this, this.getBlockFace().getOpposite());
            }
            return type;
        }
        return 0;
    }

    @Override
    public void onNeighborChange(@NotNull BlockFace side) {
        if (side != this.getBlockFace() || this.level.isUpdateScheduled(this, this)) {
            return;
        }

        RedstoneUpdateEvent ev = new RedstoneUpdateEvent(this);
        this.level.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return;
        }

        this.level.scheduleUpdate(this, 1);
    }

    public boolean isPowered() {
        return (this.getDamage() & POWERED_BIT) == 8;
    }

    public void setPowered(boolean powered) {
        this.setDamage(this.getDamage() & FACE_BIT | (powered ? 0x8 : 0x0));
    }
}
