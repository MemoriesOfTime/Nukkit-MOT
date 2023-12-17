package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemSeedsWheat;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Utils;

/**
 * Created on 2015/11/23 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockDoublePlant extends BlockFlowable {

    public static final int SUNFLOWER = 0;
    public static final int LILAC = 1;
    public static final int TALL_GRASS = 2;
    public static final int LARGE_FERN = 3;
    public static final int ROSE_BUSH = 4;
    public static final int PEONY = 5;
    public static final int TOP_HALF_BITMASK = 0x8;

    private static final String[] NAMES = new String[]{
            "Sunflower",
            "Lilac",
            "Double Tallgrass",
            "Large Fern",
            "Rose Bush",
            "Peony"
    };

    public BlockDoublePlant() {
        this(0);
    }

    public BlockDoublePlant(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return DOUBLE_PLANT;
    }

    @Override
    public boolean canBeReplaced() {
        return this.getDamage() == TALL_GRASS || this.getDamage() == LARGE_FERN;
    }

    @Override
    public String getName() {
        return NAMES[this.getDamage() > 5 ? 0 : this.getDamage()];
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if ((this.getDamage() & TOP_HALF_BITMASK) == TOP_HALF_BITMASK) {
                // Top
                if (!(this.down().getId() == DOUBLE_PLANT)) {
                    this.getLevel().setBlock(this, Block.get(BlockID.AIR), false, true);
                    return Level.BLOCK_UPDATE_NORMAL;
                }
            } else {
                // Bottom
                Block down = this.down();
                if ((down.isTransparent() && down.getId() != FARMLAND) || this.up().getId() != DOUBLE_PLANT) {
                    this.getLevel().useBreakOn(this);
                    return Level.BLOCK_UPDATE_NORMAL;
                }
            }
        }
        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        Block down = down();
        Block up = up();

        int id = down.getId();
        if (up.getId() == AIR && (id == GRASS || id == DIRT || id == PODZOL || id == FARMLAND || id == MYCELIUM)) {
            this.getLevel().setBlock(block, this, true, false); // If we update the bottom half, it will drop the item because there isn't a flower block above
            this.getLevel().setBlock(up, Block.get(DOUBLE_PLANT, getDamage() ^ TOP_HALF_BITMASK), true, true);
            return true;
        }

        return false;
    }

    @Override
    public boolean onBreak(Item item) {
        Block down = down();

        if ((this.getDamage() & TOP_HALF_BITMASK) == TOP_HALF_BITMASK) { // Top half
            this.getLevel().useBreakOn(down);
        } else {
            this.getLevel().setBlock(this, Block.get(BlockID.AIR), true, true);
        }

        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        if ((this.getDamage() & TOP_HALF_BITMASK) != TOP_HALF_BITMASK) {
            switch (this.getDamage() & 0x07) {
                case TALL_GRASS:
                case LARGE_FERN:
                    boolean dropSeeds = Utils.random.nextInt(10) == 0;
                    if (item.isShears()) {
                        //todo enchantment
                        return new Item[]{
                                toItem()
                        };
                    }

                    if (dropSeeds) {
                        return new Item[]{
                                new ItemSeedsWheat()
                        };
                    } else {
                        return Item.EMPTY_ARRAY;
                    }
            }

            return new Item[]{toItem()};
        }

        return Item.EMPTY_ARRAY;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.FOLIAGE_BLOCK_COLOR;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0f) { // Bone meal
            int type = this.getDamage() & 0x07;
            if (type == SUNFLOWER || type == LILAC || type == ROSE_BUSH || type == PEONY) { // Flower
                if (player != null && !player.isCreative()) {
                    item.count--;
                }

                this.level.addParticle(new BoneMealParticle(this));
                this.level.dropItem(this, this.toItem());
            }
            return true;
        }

        return false;
    }

    public Item toItem() {
        return new ItemBlock(this, this.getDamage() & 0x07, 1);
    }
}
