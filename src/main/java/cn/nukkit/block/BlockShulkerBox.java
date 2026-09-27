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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Created by PetteriM1
 */
public class BlockShulkerBox extends BlockTransparentMeta implements BlockEntityHolder<BlockEntityShulkerBox> {

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

    @NotNull
    @Override
    public Class<? extends BlockEntityShulkerBox> getBlockEntityClass() {
        return BlockEntityShulkerBox.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.SHULKER_BOX;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        // Bedrock blast resistance of a shulker box is 2, the same as its hardness.
        // Nukkit stores five times that value, so 30 made the box three times tougher
        // than vanilla and let it survive TNT blasts that flattened the stone around it.
        return 10;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public boolean breaksWhenMoved() {
        return true;
    }

    @Override
    public boolean sticksToPiston() {
        return false;
    }

    /**
     * Block entity key that keeps the NBT of the item the box was placed from.
     *
     * <p>Only the contents and the custom name used to be carried through place and break, so
     * every other part of the item tag vanished the moment the box touched the ground: lore
     * and any custom keys written by plugins. A box handed out with its
     * own description came back from the first break as a plain shulker box. The tag is kept
     * server side only: {@link BlockEntityShulkerBox#getSpawnCompound()} does not send it, so
     * clients see no difference.
     */
    public static final String SOURCE_ITEM_TAG = "SourceItemTag";

    /**
     * The part of an item tag that the block entity does not already own.
     *
     * <p>{@code Items} lives in the box inventory and the display name in {@code CustomName},
     * both written back from the block entity on break, so a copy here would be a second,
     * stale source of truth.
     *
     * @return a detached copy, or {@code null} when nothing is left to keep
     */
    @Nullable
    public static CompoundTag sourceItemTag(@NotNull Item item) {
        CompoundTag tag = item.getNamedTag();
        if (tag == null) {
            return null;
        }
        CompoundTag copy = tag.copy();
        copy.remove("Items");
        if (copy.containsCompound("display")) {
            CompoundTag display = copy.getCompound("display");
            display.remove("Name");
            if (display.isEmpty()) {
                copy.remove("display");
            }
        }
        return copy.isEmpty() ? null : copy;
    }

    /** Stores {@link #sourceItemTag(Item)} into a block entity compound that is about to be created. */
    public static void putSourceItemTag(@NotNull CompoundTag blockEntityNbt, @NotNull Item item) {
        CompoundTag source = sourceItemTag(item);
        if (source != null) {
            blockEntityNbt.putCompound(SOURCE_ITEM_TAG, source);
        }
    }

    @Override
    public Item toItem() {
        return this.toItem(true);
    }

    /**
     * The box as an item for pick block.
     *
     * <p>Pick block in creative creates a brand new item from the placed box. It does not copy
     * the kept item tag: plugin data that identifies a single item (for example a serial number)
     * would otherwise be duplicated while the original box still sits in the world.
     */
    public Item toPickItem() {
        return this.toItem(false);
    }

    private Item toItem(boolean withSourceItemTag) {
        ItemBlock item = new ItemBlock(this, this.getDamage(), 1);

        if (this.level == null) {
            return item;
        }

        BlockEntityShulkerBox t = (BlockEntityShulkerBox) this.getLevel().getBlockEntity(this);

        if (t != null) {
            if (withSourceItemTag && t.namedTag.containsCompound(SOURCE_ITEM_TAG)) {
                CompoundTag source = t.namedTag.getCompound(SOURCE_ITEM_TAG);
                if (!source.isEmpty()) {
                    item.setNamedTag(source.copy());
                }
            }

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
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
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
        putSourceItemTag(nbt, item);

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

    @Override
    public boolean diffusesSkyLight() {
        return true;
    }
}
