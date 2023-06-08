package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemItemFrame;
import cn.nukkit.level.Level;
import cn.nukkit.level.sound.ItemFrameItemAddedSound;
import cn.nukkit.level.sound.ItemFrameItemRotated;
import cn.nukkit.level.sound.ItemFramePlacedSound;
import cn.nukkit.level.sound.ItemFrameRemovedSound;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Pub4Game on 03.07.2016.
 */
public class BlockItemFrame extends BlockTransparentMeta implements Faceable {

    private final static int[] FACING = new int[]{4, 5, 3, 2, 1, 0}; // TODO when 1.13 support arrives, add UP/DOWN facings

    //TODO fix runtime_block_states
    private final static int FACING_BITMASK = 0x3; //11
    private final static int HAS_MAP_BIT = 0x4; //100
    //private final static int HAS_PHOTO_BIT = 0x10; //10000

    public BlockItemFrame() {
        this(0);
    }

    public BlockItemFrame(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return ITEM_FRAME_BLOCK;
    }

    @Override
    public String getName() {
        return "Item Frame";
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (!this.getSide(getFacing()).isSolid()) {
                this.level.useBreakOn(this);
                return type;
            }
        }

        return 0;
    }

    @Override
    public boolean canBeActivated() {
        return true;
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
    public boolean onActivate(Item item, Player player) {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this);
        BlockEntityItemFrame itemFrame = (BlockEntityItemFrame) blockEntity;
        if (itemFrame.getItem().getId() == Item.AIR) {
            Item itemToFrame = item.clone();
            if (player != null && player.isSurvival()) {
                item.setCount(item.getCount() - 1);
                player.getInventory().setItemInHand(item);
            }
            itemToFrame.setCount(1);
            itemFrame.setItem(itemToFrame);
            if (itemToFrame.getId() == ItemID.MAP) {
                setStoringMap(true);
                this.getLevel().setBlock(this, this, true);
            }
            this.getLevel().addSound(new ItemFrameItemAddedSound(this));
        } else {
            itemFrame.setItemRotation((itemFrame.getItemRotation() + 1) % 8);
            if (isStoringMap()) {
                setStoringMap(false);
                this.getLevel().setBlock(this, this, true);
            }
            this.getLevel().addSound(new ItemFrameItemRotated(this));
        }
        return true;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (face.getIndex() > 1 && target.isSolid() && (!block.isSolid() || block.canBeReplaced())) {
            this.setBlockFace(face);
            this.getLevel().setBlock(block, this, true, true);
            CompoundTag nbt = new CompoundTag()
                    .putString("id", BlockEntity.ITEM_FRAME)
                    .putInt("x", (int) block.x)
                    .putInt("y", (int) block.y)
                    .putInt("z", (int) block.z)
                    .putByte("ItemRotation", 0)
                    .putFloat("ItemDropChance", 1.0f);
            if (item.hasCustomBlockData()) {
                for (Tag aTag : item.getCustomBlockData().getAllTags()) {
                    nbt.put(aTag.getName(), aTag);
                }
            }
            BlockEntity.createBlockEntity(BlockEntity.ITEM_FRAME, this.getChunk(), nbt);
            this.getLevel().addSound(new ItemFramePlacedSound(this));
            return true;
        }
        return false;
    }

    @Override
    public boolean onBreak(Item item) {
        this.getLevel().setBlock(this, Block.get(BlockID.AIR), true, true);
        this.getLevel().addSound(new ItemFrameRemovedSound(this));
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this);
        BlockEntityItemFrame itemFrame = (BlockEntityItemFrame) blockEntity;
        if (itemFrame != null && ThreadLocalRandom.current().nextFloat() <= itemFrame.getItemDropChance()) {
            return new Item[]{
                    toItem(), itemFrame.getItem().clone()
            };
        } else {
            return new Item[]{
                    toItem()
            };
        }
    }

    @Override
    public Item toItem() {
        return new ItemItemFrame();
    }

    @Override
    public boolean canPassThrough() {
        return true;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (blockEntity instanceof BlockEntityItemFrame) {
            return ((BlockEntityItemFrame) blockEntity).getAnalogOutput();
        }

        return super.getComparatorInputOverride();
    }

    public BlockFace getFacing() {
        return switch (this.getDamage() & FACING_BITMASK) {
            case 0 -> BlockFace.WEST;
            case 1 -> BlockFace.EAST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.SOUTH;
            default -> null;
        };
    }

    @Override
    public void setBlockFace(@NotNull BlockFace face) {
        if (face.getIndex() > 1) {
            this.setDamage(FACING[face.getIndex()]);
        }
    }

    public boolean isStoringMap() {
        return (this.getDamage() & HAS_MAP_BIT) != 0;
    }

    public void setStoringMap(boolean map) {
        this.setDamage((this.getDamage() & FACING_BITMASK) | (map ? HAS_MAP_BIT : 0x0));
    }

    @Override
    public double getHardness() {
        return 0.25;
    }

    @Override
    public BlockFace getBlockFace() {
        return this.getFacing().getOpposite();
    }

    @Override
    public boolean isSolid() {
        return false;
    }
}
