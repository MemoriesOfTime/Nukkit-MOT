package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityPiglin;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Angelic47
 * Nukkit Project
 */
public class BlockChest extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityChest> {

    private static final int[] faces = {2, 5, 3, 4};

    public BlockChest() {
        this(0);
    }

    public BlockChest(int meta) {
        super(meta);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getId() {
        return CHEST;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityChest> getBlockEntityClass() {
        return BlockEntityChest.class;
    }
    
    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.CHEST;
    }

    @Override
    public String getName() {
        return "Chest";
    }

    @Override
    public double getHardness() {
        return 2.5;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public double getResistance() {
        return 12.5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public AxisAlignedBB recalculateBoundingBox() {
        return new SimpleAxisAlignedBB(this.x + 0.0625, this.y, this.z + 0.0625, this.x + 0.9375, this.y + 0.9475, this.z + 0.9375);
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        BlockEntityChest chest = null;
        this.setDamage(faces[player != null ? player.getDirection().getHorizontalIndex() : 0]);

        for (int side = 2; side <= 5; ++side) {
            if ((this.getDamage() == 4 || this.getDamage() == 5) && (side == 4 || side == 5)) {
                continue;
            } else if ((this.getDamage() == 3 || this.getDamage() == 2) && (side == 2 || side == 3)) {
                continue;
            }
            Block c = this.getSide(BlockFace.fromIndex(side));
            if (c instanceof BlockChest && c.getDamage() == this.getDamage()) {
                BlockEntity blockEntity = this.getLevel().getBlockEntity(c);
                if (blockEntity instanceof BlockEntityChest && !((BlockEntityChest) blockEntity).isPaired()) {
                    chest = (BlockEntityChest) blockEntity;
                    break;
                }
            }
        }

        this.getLevel().setBlock(block, this, true, true);
        CompoundTag nbt = new CompoundTag("")
                .putList(new ListTag<>("Items"))
                .putString("id", BlockEntity.CHEST)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z);

        if (item.hasCustomName()) {
            nbt.putString("CustomName", item.getCustomName());
        }

        if (item.hasCustomBlockData()) {
            Map<String, Tag> customData = item.getCustomBlockData().getTags();
            for (Map.Entry<String, Tag> tag : customData.entrySet()) {
                nbt.put(tag.getKey(), tag.getValue());
            }
        }

        BlockEntityChest blockEntity = (BlockEntityChest) BlockEntity.createBlockEntity(BlockEntity.CHEST, this.getChunk(), nbt);

        if (chest != null) {
            chest.pairWith(blockEntity);
            blockEntity.pairWith(chest);
        }

        return true;
    }

    @Override
    public boolean onBreak(Item item) {
        BlockEntity t = this.getLevel().getBlockEntity(this);
        if (t instanceof BlockEntityChest) {
            ((BlockEntityChest) t).unpair();
        }
        this.getLevel().setBlock(this, Block.get(BlockID.AIR), true, true);

        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (player != null) {
            Block top = this.up();
            if (!top.isTransparent() && !(top instanceof BlockSlab && (top.getDamage() & 0x07) <= 0)) { // avoid issues with the slab hack
                return true;
            }

            BlockEntity t = this.getLevel().getBlockEntity(this);
            BlockEntityChest chest;
            if (t instanceof BlockEntityChest) {
                chest = (BlockEntityChest) t;
            } else {
                CompoundTag nbt = new CompoundTag("")
                        .putList(new ListTag<>("Items"))
                        .putString("id", BlockEntity.CHEST)
                        .putInt("x", (int) this.x)
                        .putInt("y", (int) this.y)
                        .putInt("z", (int) this.z);
                chest = (BlockEntityChest) BlockEntity.createBlockEntity(BlockEntity.CHEST, this.getChunk(), nbt);
            }

            if (chest.namedTag.contains("Lock") && chest.namedTag.get("Lock") instanceof StringTag) {
                if (!chest.namedTag.getString("Lock").equals(item.getCustomName())) {
                    return true;
                }
            }

            player.addWindow(chest.getInventory());

            for (Entity e : this.getChunk().getEntities().values()) {
                if (e instanceof EntityPiglin) {
                    ((EntityPiglin) e).setAngry(600);
                }
            }
        }

        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WOOD_BLOCK_COLOR;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (blockEntity instanceof BlockEntityChest) {
            return ContainerInventory.calculateRedstone(((BlockEntityChest) blockEntity).getInventory());
        }

        return super.getComparatorInputOverride();
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }

    @Override
    public boolean onBreak(Item item, Player player) {
        boolean broken = this.onBreak(item);

        if (broken && player != null) {
            for (Entity e : this.getChunk().getEntities().values()) {
                if (e instanceof EntityPiglin) {
                    ((EntityPiglin) e).setAngry(600);
                }
            }
        }

        return broken;
    }

    @Override
    public boolean canBePushed() {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this);
        if (!(blockEntity instanceof BlockEntityChest)) {
            return super.canBePushed();
        }
        BlockEntityChest chest = (BlockEntityChest) blockEntity;
        return chest.getInventory().getViewers().size() < 1;
    }

    @Override
    public boolean canBePulled() {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this);
        if (!(blockEntity instanceof BlockEntityChest)) {
            return super.canBePulled();
        }
        BlockEntityChest chest = (BlockEntityChest) blockEntity;
        return chest.getInventory().getViewers().size() < 1;
    }
}
