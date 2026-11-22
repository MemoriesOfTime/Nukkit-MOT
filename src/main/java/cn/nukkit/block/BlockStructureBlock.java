package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityStructureBlock;
import cn.nukkit.inventory.StructureBlockInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 结构方块。方块 meta 与 {@code structure_block_type} 状态一一对应。
 * <p>
 * Structure block. The block meta maps directly onto the {@code structure_block_type} state.
 */
public class BlockStructureBlock extends BlockSolidMeta implements BlockEntityHolder<BlockEntityStructureBlock> {

    public static final int TYPE_DATA = 0;
    public static final int TYPE_SAVE = 1;
    public static final int TYPE_LOAD = 2;
    public static final int TYPE_CORNER = 3;
    public static final int TYPE_INVALID = 4;
    public static final int TYPE_EXPORT = 5;

    public BlockStructureBlock() {
        this(0);
    }

    public BlockStructureBlock(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRUCTURE_BLOCK;
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
    public String getName() {
        return "Structure Block";
    }

    @Override
    public boolean isBreakable(Item item) {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public @NotNull Class<? extends BlockEntityStructureBlock> getBlockEntityClass() {
        return BlockEntityStructureBlock.class;
    }

    @Override
    public @NotNull String getBlockEntityType() {
        return BlockEntity.STRUCTURE_BLOCK;
    }

    public int getStructureBlockType() {
        return this.getDamage() & 0x07;
    }

    public void setStructureBlockType(int type) {
        this.setDamage(type & 0x07);
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, @Nullable Player player) {
        if (player == null || !player.isCreative() || !player.isOp()) {
            return false;
        }
        CompoundTag nbt = new CompoundTag()
                .putString("id", BlockEntity.STRUCTURE_BLOCK)
                .putInt("x", this.getFloorX())
                .putInt("y", this.getFloorY())
                .putInt("z", this.getFloorZ())
                .putInt(BlockEntityStructureBlock.TAG_DATA, this.getStructureBlockType());
        return BlockEntityHolder.setBlockAndCreateEntity(this, true, true, nbt) != null;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
        if (player == null || !player.isCreative() || !player.isOp()) {
            return false;
        }
        BlockEntityStructureBlock tile = this.getOrCreateBlockEntity();
        tile.spawnTo(player);
        StructureBlockInventory inventory = (StructureBlockInventory) tile.getInventory();
        if (player.getWindowId(inventory) != -1) {
            player.removeWindow(inventory);
        }
        player.addWindow(inventory);
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL || type == Level.BLOCK_UPDATE_REDSTONE) {
            BlockEntityStructureBlock tile = this.getBlockEntity();
            if (tile == null) {
                return 0;
            }
            boolean powered = this.level.isBlockPowered(this);
            if (powered && !tile.isPowered()) {
                tile.setPowered(true);
                tile.onRedstoneRise();
            } else if (!powered) {
                tile.setPowered(false);
            }
        }
        return 0;
    }
}
