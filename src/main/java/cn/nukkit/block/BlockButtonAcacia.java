package cn.nukkit.block;

public class BlockButtonAcacia extends BlockButtonWooden {

    public BlockButtonAcacia() {
        this(0);
    }

    public BlockButtonAcacia(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Acacia Button";
    }

    @Override
    public int getId() {
        return ACACIA_BUTTON;
    }
}