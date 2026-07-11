package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.utils.BlockColor;

import java.util.concurrent.ThreadLocalRandom;

public class BlockMossPale extends BlockMoss {

    @Override
    public int getId() {
        return PALE_MOSS_BLOCK;
    }

    @Override
    public String getName() {
        return "Pale Moss Block";
    }

    @Override
    public double getHardness() {
        return 0.1;
    }

    @Override
    public double getResistance() {
        return 2.5;
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
    public boolean onActivate(Item item, Player player) {
        if (item.getId() != Item.DYE || item.getDamage() != ItemDye.BONE_MEAL || this.up().getId() != AIR) {
            return false;
        }

        int random = ThreadLocalRandom.current().nextInt(60);
        Block block;
        Block blockAbove = null;
        if (random < 10) {
            if (this.up(2).getId() == AIR) {
                block = Block.get(TALL_GRASS);
                blockAbove = Block.get(DOUBLE_PLANT, BlockDoublePlant.TALL_GRASS);
            } else {
                block = Block.get(TALL_GRASS);
            }
        } else if (random < 35) {
            block = Block.get(PALE_MOSS_CARPET);
        } else {
            block = Block.get(TALL_GRASS);
        }

        this.getLevel().setBlock(this.up(), block, false, true);
        if (blockAbove != null) {
            this.getLevel().setBlock(this.up(2), blockAbove, false, true);
        }

        this.level.addParticle(new BoneMealParticle(this));

        if (player != null && !player.isCreative()) {
            item.count--;
        }
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIGHT_GRAY_BLOCK_COLOR;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_HOE;
    }
}
