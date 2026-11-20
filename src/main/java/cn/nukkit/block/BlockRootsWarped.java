package cn.nukkit.block;

public class BlockRootsWarped extends BlockRoots {

    public BlockRootsWarped() {
        // Does nothing
    }

    @Override
    public int getId() {
        return WARPED_ROOTS;
    }

    @Override
    public boolean canBeReplaced() {
        return true;
    }

    @Override
    public String getName() {
        return "Warped Roots";
    }

}
