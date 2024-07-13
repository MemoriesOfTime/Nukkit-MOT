package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.inventory.ShulkerBoxInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

import javax.annotation.Nullable;

/**
 * Created by PetteriM1
 */
public class BlockShulkerBox extends BlockTransparentMeta {

    public BlockShulkerBox() {
        this(0);
    }

    public BlockShulkerBox(int meta) {
        super(meta);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getId() {
        return SHULKER_BOX;
    }

    @Override
    public String getName() {
        return this.getDyeColor().getName() + " Shulker Box";
    }

    @Override
    public double getHardness() {
        return 2.5;
    }

    @Override
    public double getResistance() {
        return 30;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public boolean breaksWhenMoved() {
        return true;
    }

    @Override
    public boolean sticksToPiston() {
        return false;
    }

    @Override
    public Item toItem() {
        ItemBlock item = new ItemBlock(this, this.getDamage(), 1);

        BlockEntityShulkerBox t = (BlockEntityShulkerBox) this.getLevel().getBlockEntity(this);

        if (t != null) {
            ShulkerBoxInventory i = t.getRealInventory();

            if (!i.isEmpty()) {

                CompoundTag nbt = item.getNamedTag();
                if (nbt == null)
                    nbt = new CompoundTag("");

                ListTag<CompoundTag> items = new ListTag<>();

                for (int it = 0; it < i.getSize(); it++) {
                    if (i.getItem(it).getId() != Item.AIR) {
                        CompoundTag d = NBTIO.putItemHelper(i.getItem(it), it);
                        items.add(d);
                    }
                }

                nbt.put("Items", items);

                item.setCompoundTag(nbt);
            }

            if (t.hasName()) {
                item.setCustomName(t.getName());
            }
        }

        return item;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.getLevel().setBlock(block, this, true, true);
        CompoundTag nbt = BlockEntity.getDefaultCompound(this, BlockEntity.SHULKER_BOX)
                .putByte("facing", face.getIndex());

        if (item.hasCustomName()) {
            nbt.putString("CustomName", item.getCustomName());
        }

        CompoundTag t = item.getNamedTag();

        if (t != null) {
            if (t.contains("Items")) {
                nbt.putList(t.getList("Items"));
            }
        }

        BlockEntity.createBlockEntity(BlockEntity.SHULKER_BOX, this.getChunk(), nbt);
        return true;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (player != null) {
            BlockEntity t = this.getLevel().getBlockEntity(this);
            BlockEntityShulkerBox box;
            if (t instanceof BlockEntityShulkerBox) {
                box = (BlockEntityShulkerBox) t;
            } else {
                CompoundTag nbt = BlockEntity.getDefaultCompound(this, BlockEntity.SHULKER_BOX);
                box = (BlockEntityShulkerBox) BlockEntity.createBlockEntity(BlockEntity.SHULKER_BOX, this.getChunk(), nbt);
            }

            Block block = this.getSide(BlockFace.fromIndex(box.namedTag.getByte("facing")));
            if (!(block instanceof BlockAir) && !(block instanceof BlockLiquid) && !(block instanceof BlockFlowable)) {
                return true;
            }

            player.addWindow(box.getInventory());
        }

        return true;
    }

    @Override
    public BlockColor getColor() {
        return this.getDyeColor().getColor();
    }

    public DyeColor getDyeColor() {
        return DyeColor.getByWoolData(this.getDamage());
    }

    @Override
    public int getFullId() {
        return (this.getId() << DATA_BITS) + this.getDamage();
    }

    @Override
    public boolean alwaysDropsOnExplosion() {
        return true;
    }

    @Override
    public Item[] getDrops(@Nullable Player player, Item item) {
        if (player != null
                && player.isCreative()
                && this.getLevel().getBlockEntity(this) instanceof BlockEntityShulkerBox t
                && !t.getRealInventory().isEmpty()) {
            return new Item[]{this.toItem()};
        }
        return super.getDrops(player, item);
    }
}