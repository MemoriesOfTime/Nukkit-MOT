package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockHangingMossPale extends BlockFlowable {

    private static final int TIP_BIT = 0b1;

    public BlockHangingMossPale() {
        this(0);
    }

    public BlockHangingMossPale(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_HANGING_MOSS;
    }

    @Override
    public String getName() {
        return "Pale Hanging Moss";
    }

    public boolean isTip() {
        return (this.getDamage() & TIP_BIT) != 0;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        Block up = this.up();
        if (up.getId() != this.getId() && !up.isSolid()) {
            return false;
        }

        this.setDamage(TIP_BIT);
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            this.getLevel().scheduleUpdate(this, 1);
            return Level.BLOCK_UPDATE_NORMAL;
        }

        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            Block up = this.up();
            if (up.getId() != this.getId() && !up.isSolid()) {
                this.getLevel().useBreakOn(this, (BlockFace) null, (Item) null, (Player) null, true);
                return Level.BLOCK_UPDATE_SCHEDULED;
            }

            if (up.getId() == this.getId() && (up.getDamage() & TIP_BIT) != 0) {
                up.setDamage(up.getDamage() & ~TIP_BIT);
                this.getLevel().setBlock(up, up, true, true);
            }

            Block down = this.down();
            if (down.getId() != this.getId() && !this.isTip()) {
                this.setDamage(this.getDamage() | TIP_BIT);
                this.getLevel().setBlock(this, this, true, true);
            }

            return Level.BLOCK_UPDATE_SCHEDULED;
        }

        return 0;
    }

    @Override
    public int getBurnChance() {
        return 15;
    }

    @Override
    public int getBurnAbility() {
        return 100;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isShears()) {
            return new Item[]{this.toItem()};
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0f) {
            Block bottom = this;
            while (bottom.down().getId() == this.getId()) {
                bottom = bottom.down();
            }

            Block belowBottom = bottom.down();
            if (belowBottom.getId() != AIR) {
                return false;
            }

            if (player != null && !player.isCreative()) {
                item.count--;
            }

            bottom.setDamage(bottom.getDamage() & ~TIP_BIT);
            this.getLevel().setBlock(bottom, bottom, true, true);

            Block grown = Block.get(this.getId(), TIP_BIT);
            this.getLevel().setBlock(belowBottom, grown, true, true);
            this.level.addParticle(new BoneMealParticle(this));
            return true;
        }

        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIGHT_GRAY_BLOCK_COLOR;
    }
}
