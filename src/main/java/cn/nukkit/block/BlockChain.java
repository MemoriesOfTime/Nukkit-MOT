package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

public class BlockChain extends BlockTransparentMeta {

    public BlockChain() {
        this(0);
    }

    public BlockChain(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Iron Chain";
    }

    @Override
    public int getId() {
        return CHAIN_BLOCK;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
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
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
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
    public boolean isSolid() {
        return false;
    }

    @Override
    public double getMinX() {
        return this.getPillarAxis() == BlockFace.Axis.X ? x : x + 6.5 / 16.0;
    }

    @Override
    public double getMaxX() {
        return this.getPillarAxis() == BlockFace.Axis.X ? x + 1 : x + 9.5 / 16.0;
    }

    @Override
    public double getMinY() {
        return this.getPillarAxis() == BlockFace.Axis.Y ? y : y + 6.5 / 16.0;
    }

    @Override
    public double getMaxY() {
        return this.getPillarAxis() == BlockFace.Axis.Y ? y + 1 : y + 9.5 / 16.0;
    }

    @Override
    public double getMinZ() {
        return this.getPillarAxis() == BlockFace.Axis.Z ? z : z + 6.5 / 16.0;
    }

    @Override
    public double getMaxZ() {
        return this.getPillarAxis() == BlockFace.Axis.Z ? z + 1 : z + 9.5 / 16.0;
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
