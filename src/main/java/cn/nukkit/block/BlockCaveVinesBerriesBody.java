package cn.nukkit.block;

import cn.nukkit.level.Position;

public class BlockCaveVinesBerriesBody extends BlockCaveVines {

    public BlockCaveVinesBerriesBody() {
        this(0);
    }

    public BlockCaveVinesBerriesBody(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CAVE_VINES_BODY_WITH_BERRIES;
    }

    @Override
    public BlockCaveVines getStateWithBerries(Position position) {
        return (BlockCaveVines) Block.get(CAVE_VINES_BODY_WITH_BERRIES, this.getDamage(), position);
    }

    @Override
    public boolean hasBerries() {
        return true;
    }

    @Override
    public int getLightLevel() {
        return 14;
    }
}
