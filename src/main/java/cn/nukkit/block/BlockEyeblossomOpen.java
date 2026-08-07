package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.utils.BlockColor;

public class BlockEyeblossomOpen extends BlockFlower {

    public BlockEyeblossomOpen() {
        this(0);
    }

    public BlockEyeblossomOpen(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return OPEN_EYEBLOSSOM;
    }

    @Override
    public String getName() {
        return "Open Eyeblossom";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ORANGE_BLOCK_COLOR;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_RANDOM) {
            if (!this.level.isDaytime()) {
                this.level.setBlock(this, Block.get(CLOSED_EYEBLOSSOM), true, true);
                scheduleNearbyEyeblossoms();
            }
        }
        return type;
    }

    protected void scheduleNearbyEyeblossoms() {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -2; y <= 2; y++) {
                    Block block = this.level.getBlock(this.add(x, y, z));
                    if (block instanceof BlockEyeblossomOpen || block instanceof BlockEyeblossomClosed) {
                        this.level.scheduleUpdate(block, 1);
                    }
                }
            }
        }
    }
}
