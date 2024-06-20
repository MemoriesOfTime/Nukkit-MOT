package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;

/**
 * @author glorydark
 */
public abstract class BlockCopperBulbBase extends BlockSolidMeta {

    public static final int LIT_BIT = 0x01; // 0001

    public static final int POWERED_BIT = 0x02; // 0010

    public BlockCopperBulbBase() {
        super(0);
    }

    public BlockCopperBulbBase(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public int getId() {
        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (this.level.isBlockPowered(this.getLocation())) {
            this.setLit(true);
            this.setPowered(true);
        }
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL || type == Level.BLOCK_UPDATE_REDSTONE) {
            // Redstone event
            RedstoneUpdateEvent ev = new RedstoneUpdateEvent(this);
            getLevel().getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return 0;
            }
            if (this.level.isBlockPowered(this.getLocation())) {
                this.setLit(true);
                this.setPowered(true);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public double getHardness() {
        return 0.3D;
    }

    @Override
    public double getResistance() {
        return 1.5D;
    }

    public boolean isLit() {
        return this.getDamage(LIT_BIT) != 0;
    }

    public void setLit(boolean lit) {
        this.setDamage(LIT_BIT, lit ? 1 : 0);
    }

    public boolean isPowered() {
        return this.getDamage(POWERED_BIT) != 0;
    }

    public void setPowered(boolean lit) {
        this.setDamage(POWERED_BIT, lit ? 1 : 0);
    }
}
