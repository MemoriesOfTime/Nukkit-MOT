package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.utils.BlockColor;

public class BlockEyeblossomClosed extends BlockEyeblossomOpen {

    public BlockEyeblossomClosed() {
        this(0);
    }

    public BlockEyeblossomClosed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CLOSED_EYEBLOSSOM;
    }

    @Override
    public String getName() {
        return "Closed Eyeblossom";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.GREEN_TERRACOTA_BLOCK_COLOR;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_RANDOM) {
            if (this.level.isDaytime()) {
                this.level.setBlock(this, Block.get(OPEN_EYEBLOSSOM), true, true);
                scheduleNearbyEyeblossoms();
            }
        }
        return type;
    }
}
