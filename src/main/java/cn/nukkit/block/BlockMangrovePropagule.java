package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.object.tree.ObjectMangroveTree;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.utils.BlockColor;

import java.util.concurrent.ThreadLocalRandom;

public class BlockMangrovePropagule extends BlockSapling {

    public static final int STAGE_BIT = 0x07;
    public static final int HANGING_BIT = 0x08;

    public static final int STAGE_FULLY_GROWN = 4;

    public BlockMangrovePropagule() {
        this(0);
    }

    public BlockMangrovePropagule(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MANGROVE_PROPAGULE;
    }

    @Override
    public String getName() {
        return "Mangrove Propagule";
    }

    @Override
    public Item[] getDrops(Item item) {
        if (isHanging() && getStage() < STAGE_FULLY_GROWN) {
            return Item.EMPTY_ARRAY;
        }

        return new Item[]{
                toItem(),
        };
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }

    @Override
    public boolean canBeFlowedInto() {
        return false;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.FLOW_INTO_BLOCK;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (block.up().getId() == Block.MANGROVE_LEAVES) {
            this.setHanging(true);
            this.getLevel().setBlock(block, this, true, true);
            return true;
        }

        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0F) { // Bone meal
            if (!isHanging()) {
                if (player != null && !player.isCreative()) {
                    item.count--;
                }

                this.level.addParticle(new BoneMealParticle(this));

                if (ThreadLocalRandom.current().nextFloat() >= 0.45f) {
                    return true;
                }

                this.grow();
                return true;
            }

            int stage = getStage();
            if (stage >= STAGE_FULLY_GROWN) {
                return false;
            }

            if (player != null && !player.isCreative()) {
                item.count--;
            }

            this.level.addParticle(new BoneMealParticle(this));

            this.setStage(stage + 1);
            this.level.setBlock(this, this, true);
            return true;
        }

        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (isSupportInvalid()) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) { //Growth
            if (!this.isHanging()) {
                if (getLevel().getFullLight(add(0, 1, 0)) >= BlockCrops.MINIMUM_LIGHT_LEVEL) {
                    if (isAged()) {
                        this.grow();
                    } else {
                        setAged(true);
                        this.getLevel().setBlock(this, this, true);
                        return Level.BLOCK_UPDATE_RANDOM;
                    }
                }
                return Level.BLOCK_UPDATE_RANDOM;
            }

            int stage = getStage();
            if (stage >= STAGE_FULLY_GROWN) {
                return 0;
            }

            this.setStage(stage + 1);
            this.level.setBlock(this, this, true);

            return Level.BLOCK_UPDATE_RANDOM;
        }
        return Level.BLOCK_UPDATE_NORMAL;
    }

    public boolean grow() {
        new ObjectMangroveTree().generate(this.getLevel(), new NukkitRandom(), this);
        this.level.setBlock(this, Block.get(BlockID.AIR));
        Block down = this.down();
        if (down.getId() == BlockID.DIRT_WITH_ROOTS) {
            this.level.setBlock(down, Block.get(BlockID.DIRT));
        }
        return true;
    }

    @Override
    protected boolean isSupportInvalid() {
        if (isHanging()) {
            return up().getId() != MANGROVE_LEAVES;
        }

        return super.isSupportInvalid();
    }

    public int getStage() {
        return this.getDamage(STAGE_BIT);
    }

    public void setStage(int stage) {
        this.setDamage(STAGE_BIT, stage);
    }

    public boolean isHanging() {
        return this.getDamage(HANGING_BIT) != 0;
    }

    public void setHanging(boolean hanging) {
        this.setDamage(HANGING_BIT, hanging ? 1 : 0);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.PLANT_BLOCK_COLOR;
    }
}