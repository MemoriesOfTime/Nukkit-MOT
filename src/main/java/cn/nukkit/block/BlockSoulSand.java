package cn.nukkit.block;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.BlockFormEvent;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;

/**
 * @author Pub4Game
 * @since 27.12.2015
 */
public class BlockSoulSand extends BlockSolid {

    public BlockSoulSand() {
    }

    @Override
    public String getName() {
        return "Soul Sand";
    }

    @Override
    public int getId() {
        return SOUL_SAND;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public double getResistance() {
        return 2.5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_SHOVEL;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        return new SimpleAxisAlignedBB(
                this.x, this.y, this.z, this.x + 1, this.y + 1 - 1 / 8d, this.z + 1);
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public void onEntityCollide(Entity entity) {
        entity.motionX *= 0.4d;
        entity.motionZ *= 0.4d;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            Block up = up();
            if (up instanceof BlockWater && up.getDamage() == 0) {
                BlockFormEvent event = new BlockFormEvent(up, Block.get(BUBBLE_COLUMN, BlockBubbleColumn.DIRECTION_UP));
                if (!event.isCancelled()) {
                    if (event.getNewState().getWaterloggingType() != WaterloggingType.NO_WATERLOGGING) {
                        this.getLevel().setBlock(up, 1, Block.get(WATER), true, false);
                    }
                    this.getLevel().setBlock(up, 0, event.getNewState(), true, true);
                }
            }
        }
        return 0;
    }

}