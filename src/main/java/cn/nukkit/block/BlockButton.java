package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.sound.ButtonClickSound;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.Faceable;

/**
 * Created by CreeperFace on 27. 11. 2016.
 */
public abstract class BlockButton extends BlockFlowable implements Faceable {

    public static final int BUTTON_PRESSED_BIT = 0x08;
    public static final int FACING_DIRECTION_BIT = 0x07;

    public BlockButton() {
        this(0);
    }

    public BlockButton(int meta) {
        super(meta);
    }

    @Override
    public double getResistance() {
        return 2.5;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public boolean canBeFlowedInto() {
        return false;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {

        this.setDamage(face.getIndex());
        if (!isSupportValid(this.getSide(this.getFacing().getOpposite()))) {
            return false;
        }
        this.level.setBlock(block, this, true, true);
        return true;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (this.isActivated()) {
            return false;
        }

        this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 0, 15));
        this.setDamage(this.getDamage() ^ BUTTON_PRESSED_BIT);
        this.level.setBlock(this, this, true, false);
        this.level.addSound(new ButtonClickSound(this.add(0.5, 0.5, 0.5)));
        this.level.scheduleUpdate(this, 30);

        level.updateAroundRedstone(getLocation(), null);
        level.updateAroundRedstone(getLocation().getSide(getFacing().getOpposite()), null);
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (!isSupportValid(this.getSide(this.getFacing().getOpposite()))) {
                this.level.useBreakOn(this, Item.get(Item.WOODEN_PICKAXE));
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            if (this.isActivated()) {
                this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 15, 0));

                this.setDamage(this.getDamage() ^ BUTTON_PRESSED_BIT);
                this.level.setBlock(this, this, true, false);
                this.level.addSound(new ButtonClickSound(this.add(0.5, 0.5, 0.5)));

                level.updateAroundRedstone(getLocation(), null);
                level.updateAroundRedstone(getLocation().getSide(getFacing().getOpposite()), null);
            }

            return Level.BLOCK_UPDATE_SCHEDULED;
        }

        return 0;
    }

    private boolean isSupportValid(Block block) {
        if (!block.isTransparent()) {
            return true;
        }
        if (this.getFacing() == BlockFace.UP) {
            return Block.canStayOnFullSolid(block);
        }
        return Block.canConnectToFullSolid(block);
    }

    public boolean isActivated() {
        return ((this.getDamage() & BUTTON_PRESSED_BIT) == BUTTON_PRESSED_BIT);
    }

    @Override
    public boolean isPowerSource() {
        return true;
    }

    @Override
    public int getWeakPower(BlockFace side) {
        return isActivated() ? 15 : 0;
    }

    @Override
    public int getStrongPower(BlockFace side) {
        return !isActivated() ? 0 : (getFacing() == side ? 15 : 0);
    }

    public BlockFace getFacing() {
        return BlockFace.fromIndex(this.getDamage() & FACING_DIRECTION_BIT);
    }

    @Override
    public boolean onBreak(Item item) {
        if (isActivated()) {
            this.level.getServer().getPluginManager().callEvent(new BlockRedstoneEvent(this, 15, 0));
        }

        return super.onBreak(item);
    }

    @Override
    public Item toItem() {
        return Item.get(this.getItemId(), 0);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }
}
