package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.LevelEventPacket;

public class BlockStairsCopperCutWaxed extends BlockStairsCopperCut {

    public BlockStairsCopperCutWaxed() {
        this(0);
    }

    public BlockStairsCopperCutWaxed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WAXED_CUT_COPPER_STAIRS;
    }

    @Override
    public String getName() {
        return "Waxed Cut Copper Stairs";
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.isAxe()) {
            item.useOn(this);

            level.addLevelEvent(this, LevelEventPacket.EVENT_PARTICLE_WAX_OFF);

            level.setBlock(this, get(getDewaxedBlockId(), getDamage()), true);
            return true;
        }

        return false;
    }

    @Override
    public int onUpdate(int type) {
        // 打蜡不氧化，但非 RANDOM 更新仍需走父类维护 corner 位
        // Waxed never oxidizes, but non-RANDOM updates must still reach the parent to maintain corner bits
        return type == Level.BLOCK_UPDATE_RANDOM ? 0 : super.onUpdate(type);
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public final int getWaxedBlockId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIncrementAgeBlockId() {
        return WAXED_EXPOSED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDewaxedBlockId() {
        return CUT_COPPER_STAIRS;
    }
}
