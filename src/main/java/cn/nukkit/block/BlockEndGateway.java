package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityEndGateway;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.BlockColor;

/**
 * @author PikyCZ
 */
public class BlockEndGateway extends BlockSolid {

    @Override
    public String getName() {
        return "End Gateway";
    }

    @Override
    public int getId() {
        return END_GATEWAY;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (super.place(item, block, target, face, fx, fy, fz, player)) {
            CompoundTag nbt = new CompoundTag()
                    .putString("id", BlockEntity.END_GATEWAY)
                    .putInt("x", (int) this.x)
                    .putInt("y", (int) this.y)
                    .putInt("z", (int) this.z);
            BlockEntity.createBlockEntity(BlockEntity.END_GATEWAY, this.getChunk(), nbt);
        }
        return false;
    }

    @Override
    public boolean canPassThrough() {
        if (this.getLevel() == null) {
            return false;
        }

        if (this.getLevel().getDimension() == Level.DIMENSION_THE_END) {
            if (this.getLevel().getBlockEntity(this) instanceof BlockEntityEndGateway) {
                BlockEntityEndGateway entityEndGateway = (BlockEntityEndGateway) this.getLevel().getBlockEntity(this);
                return !entityEndGateway.isTeleportCooldown();
            }
        }
        return false;
    }

    @Override
    public boolean isBreakable(Item item) {
        return false;
    }

    @Override
    public double getHardness() {
        return -1;
    }

    @Override
    public double getResistance() {
        return 18000000;
    }

    @Override
    public int getLightLevel() {
        return 15;
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BLACK_BLOCK_COLOR;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(BlockID.AIR));
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBePulled() {
        return false;
    }

    @Override
    public void onEntityCollide(Entity entity) {
        if (this.getLevel() == null) {
            return;
        }

        if (this.getLevel().getDimension() != Level.DIMENSION_THE_END) {
            return;
        }

        if (entity == null) {
            return;
        }

        if (!(this.getLevel().getBlockEntity(this) instanceof BlockEntityEndGateway)) {
            return;
        }
        BlockEntityEndGateway endGateway = (BlockEntityEndGateway) this.getLevel().getBlockEntity(this);

        if (!endGateway.isTeleportCooldown()) {
            endGateway.teleportEntity(entity);
        }
    }
}
