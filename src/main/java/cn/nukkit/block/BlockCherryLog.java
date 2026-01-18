package cn.nukkit.block;

public class BlockCherryLog extends BlockWood {

    public BlockCherryLog() {
        this(0);
    }

    public BlockCherryLog(int meta) {
        super(meta);
    }

    @Override
    public int getBurnAbility() {
        return 5;
    }

    @Override
    public String getName() {
        return "Cherry log";
    }

    @Override
    public int getId() {
        return CHERRY_LOG;
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_CHERRY_LOG;
    }
}