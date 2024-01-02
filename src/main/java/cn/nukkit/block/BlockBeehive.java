package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityBeehive;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Sound;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BlockBeehive extends BlockSolidMeta implements Faceable, BlockEntityHolder<BlockEntityBeehive> {

    public static final int DIRECTION_BIT = 0x3;
    public static final int HONEY_LEVEL_BIT = 0xc;

    public static final int HONEY_LEVEL_MIN = 0;
    public static final int HONEY_LEVEL_MAX = 5;

    public BlockBeehive() {
        this(0);
    }

    protected BlockBeehive(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BEEHIVE;
    }

    @Override
    public String getName() {
        return "Beehive";
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.BEEHIVE;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityBeehive> getBlockEntityClass() {
        return BlockEntityBeehive.class;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public int getBurnChance() {
        return 5;
    }

    @Override
    public int getBurnAbility() {
        return 20;
    }

    @Override
    public double getHardness() {
        return 0.6;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        if (player == null) {
            setBlockFace(BlockFace.SOUTH);
        } else {
            setBlockFace(player.getDirection().getOpposite());
        }

        int honeyLevel = item.hasCustomBlockData() ? item.getCustomBlockData().getByte("HoneyLevel") : 0;
        setHoneyLevel(honeyLevel);
        BlockEntityBeehive beehive = BlockEntityHolder.setBlockAndCreateEntity(this, true, true, item.getCustomBlockData());
        if (beehive == null) {
            return false;
        }
        
        if (beehive.namedTag.getByte("ShouldSpawnBees") > 0) {
            List<BlockFace> validSpawnFaces = beehive.scanValidSpawnFaces(true);
            for (BlockEntityBeehive.Occupant occupant : beehive.getOccupants()) {
                beehive.spawnOccupant(occupant, validSpawnFaces);
            }
    
            beehive.namedTag.putByte("ShouldSpawnBees", 0);
        }
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, Player player) {
        if (item.getId() == ItemID.SHEARS && isFull()) {
            honeyCollected(player);
            level.addSound(add(0.5, 0.5, 0.5), Sound.BLOCK_BEEHIVE_SHEAR);
            item.useOn(this);
            for (int i = 0; i < 3; ++i) {
                level.dropItem(this, Item.get(ItemID.HONEYCOMB));
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean canBeActivated() {
        return true;
    }

    public void honeyCollected(Player player) {
        honeyCollected(player, level.getServer().getDifficulty() > 0 && !player.isCreative());
    }

    public void honeyCollected(Player player, boolean angerBees) {
        setHoneyLevel(0);
        if (down().getId() != CAMPFIRE_BLOCK && angerBees) {
            angerBees(player);
        }
    }

    public void angerBees(Player player) {
        BlockEntityBeehive beehive = getBlockEntity();
        if (beehive != null) {
            beehive.angerBees(player);
        }
    }
    
    @Override
    public Item toItem() {
        Item item = Item.get(getItemId(), 0, 1);
        if (level != null) {
            BlockEntityBeehive beehive = getBlockEntity();
            if (beehive != null) {
                beehive.saveNBT();
                if (!beehive.isHoneyEmpty() || !beehive.isEmpty()) {
                    CompoundTag copy = beehive.namedTag.copy();
                    copy.putByte("HoneyLevel", getHoneyLevel());
                    item.setCustomBlockData(copy);
                }
            }
        }
        return item;
    }
    
    @Override
    public boolean canSilkTouch() {
        return true;
    }

    /*@Override
    public boolean mustSilkTouch(Vector3 vector, int layer, BlockFace face, Item item, Player player) {
        if (player != null) {
            BlockEntityBeehive beehive = getBlockEntity();
            if (beehive != null && !beehive.isEmpty()) {
                return true;
            }
        }
        return super.mustSilkTouch(vector, layer, face, item, player);
    }

    @Override
    public boolean mustDrop(Vector3 vector, int layer, BlockFace face, Item item, Player player) {
        return mustSilkTouch(vector, layer, face, item, player) || super.mustDrop(vector, layer, face, item, player);
    }*/

    @Override
    public boolean canHarvestWithHand() {
        return true;
    }
    
    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{ Item.get(BEEHIVE) };
    }
    
    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage(DIRECTION_BIT));
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setDamage(DIRECTION_BIT, face.getHorizontalIndex());
    }

    public void setHoneyLevel(int honeyLevel) {
        this.setDamage(HONEY_LEVEL_BIT, honeyLevel);
    }

    public int getHoneyLevel() {
        return getDamage(HONEY_LEVEL_BIT);
    }

    public boolean isEmpty() {
        return getHoneyLevel() == HONEY_LEVEL_MIN;
    }

    public boolean isFull() {
        return getHoneyLevel() == HONEY_LEVEL_MAX;
    }
    
    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }
    
    @Override
    public int getComparatorInputOverride() {
        return getHoneyLevel();
    }
}
