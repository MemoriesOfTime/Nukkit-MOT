package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;

public class BlockChain extends BlockTransparentMeta {

    public BlockChain() {
        this(0);
    }

    public BlockChain(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Chain";
    }

    @Override
    public int getId() {
        return CHAIN_BLOCK;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setPillarAxis(face.getAxis());
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    public void setPillarAxis(BlockFace.Axis axis) {
        switch (axis) {
            case Y:
                this.setDamage(0);
                break;
            case X:
                this.setDamage(1);
                break;
            case Z:
                this.setDamage(2);
                break;
        }
    }

    public BlockFace.Axis getPillarAxis() {
        return switch (this.getDamage() % 0x3) {
            case 2 -> BlockFace.Axis.Z;
            case 1 -> BlockFace.Axis.X;
            default -> BlockFace.Axis.Y;
        };
    }

    @Override
    public double getHardness() {
        return 5;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getMinX() {
        return x + 7 / 16.0;
    }

    @Override
    public double getMaxX() {
        return x + 9 / 16.0;
    }

    @Override
    public double getMinZ() {
        return z + 7 / 16.0;
    }

    @Override
    public double getMaxZ() {
        return z + 9 / 16.0;
    }

    @Override
    public Item toItem() {
        return Item.get(Item.CHAIN);
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
}
