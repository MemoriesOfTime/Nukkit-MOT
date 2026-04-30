package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.IntBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChiseledBookshelf;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Sound;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Chiseled bookshelf with six book slots and block-entity backed storage.
 * <p>
 * Adapted from Lumi and PowerNukkitX.
 */
public class BlockChiseledBookshelf extends BlockSolidMeta implements BlockEntityHolder<BlockEntityChiseledBookshelf>, Faceable, BlockPropertiesHelper {

    private static final int BOOKS_MASK = 0b0011_1111;
    private static final int DIRECTION_MASK = 0b1100_0000;
    private static final IntBlockProperty BOOKS_STORED = new IntBlockProperty("books_stored", false, BOOKS_MASK);
    private static final BlockProperties PROPERTIES = new BlockProperties(BOOKS_STORED, VanillaProperties.DIRECTION);

    public BlockChiseledBookshelf() {
        this(0);
    }

    public BlockChiseledBookshelf(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Chiseled Bookshelf";
    }

    @Override
    public int getId() {
        return CHISELED_BOOKSHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:chiseled_bookshelf";
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityChiseledBookshelf> getBlockEntityClass() {
        return BlockEntityChiseledBookshelf.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.CHISELED_BOOKSHELF;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, @Nullable Player player) {
        this.setBlockFace(player != null ? player.getHorizontalFacing().getOpposite() : BlockFace.SOUTH);
        CompoundTag nbt = new CompoundTag();
        if (item.hasCustomName()) {
            nbt.putString("CustomName", item.getCustomName());
        }
        if (item.hasCustomBlockData()) {
            for (Map.Entry<String, Tag> tag : item.getCustomBlockData().getTags().entrySet()) {
                nbt.put(tag.getKey(), tag.getValue());
            }
        }
        return BlockEntityHolder.setBlockAndCreateEntity(this, true, true, nbt) != null;
    }

    @Override
    public int onTouch(@NotNull Vector3 vector, @NotNull Item item, @NotNull BlockFace face, float fx, float fy, float fz,
                       @Nullable Player player, PlayerInteractEvent.Action action) {
        if (action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || player == null) {
            return super.onTouch(vector, item, face, fx, fy, fz, player, action);
        }

        BlockFace blockFace = this.getBlockFace();
        if (face != blockFace) {
            return 0;
        }

        int slot = this.getRegion(switch (blockFace) {
            case NORTH -> new Vector2(1 - fx, fy);
            case SOUTH -> new Vector2(fx, fy);
            case WEST -> new Vector2(fz, fy);
            case EAST -> new Vector2(1 - fz, fy);
            default -> new Vector2(fx, fy);
        });

        BlockEntityChiseledBookshelf bookshelf = this.getOrCreateBlockEntity();
        if (bookshelf.hasBook(slot)) {
            Item removed = bookshelf.removeBook(slot);
            for (Item leftover : player.getInventory().addItem(removed)) {
                this.level.dropItem(player, leftover);
            }
            this.level.addSound(this.add(0.5, 0.5, 0.5), getPickupSound(removed));
        } else if (isBookshelfBook(item)) {
            Item stored = item.clone();
            stored.setCount(1);
            bookshelf.setBook(slot, stored);
            if (!player.isCreative()) {
                item.count--;
            }
            this.level.addSound(this.add(0.5, 0.5, 0.5),
                    item.getId() == Item.ENCHANTED_BOOK ? Sound.INSERT_ENCHANTED_CHISELED_BOOKSHELF : Sound.INSERT_CHISELED_BOOKSHELF);
        } else {
            return 0;
        }

        return 1;
    }

    @Override
    public Item[] getDrops(Item item) {
        return Item.EMPTY_ARRAY;
    }

    @Override
    public Item toItem() {
        return Item.fromString(ItemNamespaceId.CHISELED_BOOKSHELF_NAMESPACE_ID);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getProperty(DIRECTION_MASK));
    }

    @Override
    public void setBlockFace(BlockFace face) {
        int horizontalIndex = face.getHorizontalIndex();
        this.setProperty(DIRECTION_MASK, horizontalIndex >= 0 ? horizontalIndex : BlockFace.SOUTH.getHorizontalIndex());
    }

    public int getBooksStoredBit() {
        return this.getProperty(BOOKS_MASK);
    }

    public void setBooksStoredBit(int booksStoredBit) {
        this.setProperty(BOOKS_MASK, booksStoredBit);
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntityChiseledBookshelf bookshelf = this.getBlockEntity();
        return bookshelf == null ? 0 : bookshelf.getComparatorOutput();
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 7.5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public int getBurnChance() {
        return 30;
    }

    @Override
    public int getBurnAbility() {
        return 20;
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WOOD_BLOCK_COLOR;
    }

    private void setProperty(int mask, int value) {
        int shift = Integer.numberOfTrailingZeros(mask);
        int maxValue = mask >>> shift;
        this.setDamage((this.getDamage() & ~mask) | ((value & maxValue) << shift));
    }

    private int getProperty(int mask) {
        int shift = Integer.numberOfTrailingZeros(mask);
        return (this.getDamage() & mask) >>> shift;
    }

    private int getRegion(Vector2 clickPos) {
        if (clickPos.getX() < 1 / 3.0) {
            return clickPos.getY() < 0.5 ? 3 : 0;
        }
        if (clickPos.getX() < 2 / 3.0) {
            return clickPos.getY() < 0.5 ? 4 : 1;
        }
        return clickPos.getY() < 0.5 ? 5 : 2;
    }

    static Sound getPickupSound(Item item) {
        return item.getId() == Item.ENCHANTED_BOOK ? Sound.PICKUP_ENCHANTED_CHISELED_BOOKSHELF : Sound.PICKUP_CHISELED_BOOKSHELF;
    }

    private static boolean isBookshelfBook(Item item) {
        return switch (item.getId()) {
            case Item.BOOK, Item.BOOK_AND_QUILL, Item.WRITTEN_BOOK, Item.ENCHANTED_BOOK -> true;
            default -> false;
        };
    }
}
