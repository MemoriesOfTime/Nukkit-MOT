package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Sound;
import cn.nukkit.utils.BlockColor;

/**
 * @author MagicDroidX
 * AMAZING COARSE DIRT added by kvetinac97
 * Nukkit Project
 */
public class BlockDirt extends BlockSolidMeta {

    public BlockDirt() {
        this(0);
    }

    public BlockDirt(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return DIRT;
    }

    @Override
    public boolean canBeActivated() {
        return true;
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
    public int getToolType() {
        return ItemTool.TYPE_SHOVEL;
    }

    @Override
    public String getName() {
        return this.getDamage() == 0 ? "Dirt" : "Coarse Dirt";
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.isHoe()) {
            Block up = this.up();
            if (up instanceof BlockAir || up instanceof BlockFlowable) {
                item.useOn(this);
                this.getLevel().setBlock(this, this.getDamage() == 0 ? get(FARMLAND) : get(DIRT), true);
                if (player != null) {
                    player.getLevel().addSoundToViewers(player, Sound.STEP_GRASS);
                }
                return true;
            }
        } else if (item.isShovel()) {
            Block up = this.up();
            if (up instanceof BlockAir || up instanceof BlockFlowable) {
                item.useOn(this);
                this.getLevel().setBlock(this, Block.get(GRASS_PATH));
                if (player != null) {
                    player.getLevel().addSoundToViewers(player, Sound.STEP_GRASS);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public Item[] getDrops(Item item) {
        int damage = this.getDamage() & 0x01;
        return new Item[]{new ItemBlock(Block.get(BlockID.DIRT, damage), damage)};
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.DIRT_BLOCK_COLOR;
    }
}
