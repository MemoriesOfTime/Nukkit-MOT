package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCopperGolemStatue;
import cn.nukkit.entity.passive.EntityCopperGolem;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for Copper Golem Statue blocks.
 * <p>
 * Adapted from EaseCation/Nukkit (<a href="https://github.com/EaseCation/Nukkit">EaseCation/Nukkit</a>)
 */
public class BlockCopperGolemStatue extends BlockTransparentMeta implements Oxidizable, Waxable, Faceable, BlockEntityHolder<BlockEntityCopperGolemStatue> {

    public static final int DIRECTION_MASK = 0b11;

    public BlockCopperGolemStatue() {
        this(0);
    }

    public BlockCopperGolemStatue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return COPPER_GOLEM_STATUE;
    }

    @Override
    public String getName() {
        return "Copper Golem Statue";
    }

    @Override
    public double getHardness() {
        return 3;
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
    public int getToolTier() {
        return ItemTool.TIER_STONE;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_STONE) {
            return new Item[]{toItem()};
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public Item toItem() {
        Item item = new ItemBlock(Block.get(this.getId()), 0);
        BlockEntityCopperGolemStatue blockEntity = getBlockEntity();
        if (blockEntity != null) {
            CompoundTag customBlockData = new CompoundTag();
            CompoundTag actorTag = new CompoundTag();
            actorTag.putString("ActorIdentifier", blockEntity.getActorIdentifier());
            actorTag.putCompound("SaveData", blockEntity.getActorSaveData());
            customBlockData.putCompound("Actor", actorTag);
            item.setCustomBlockData(customBlockData);
        }
        return item;
    }

    @Override
    public @NotNull BlockColor getColor() {
        return BlockColor.ORANGE_BLOCK_COLOR;
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
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntityCopperGolemStatue blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return 1;
        }
        return 1 + blockEntity.getPose();
    }

    @Override
    public @NotNull BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(getDamage() & DIRECTION_MASK);
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        setDamage(player != null ? player.getDirection().getOpposite().getHorizontalIndex() : 0);

        if (!super.place(item, block, target, face, fx, fy, fz, player)) {
            return false;
        }
        CompoundTag nbt = buildItemNbt(item);
        try {
            createBlockEntity(nbt);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
        if (item.isNull()) {
            BlockEntityCopperGolemStatue blockEntity = getOrCreateBlockEntity();
            blockEntity.changePose();
            return true;
        }

        if (item.isAxe() && !isWaxed() && getOxidizationLevel() == OxidizationLevel.UNAFFECTED) {
            if (player == null || !player.isCreative()) {
                item.useOn(this);
            }
            getLevel().setBlock(this, Block.get(AIR), true, true);
            EntityCopperGolem golem = new EntityCopperGolem(getChunk(), EntityCopperGolem.getDefaultNBT(add(0.5, 0, 0.5)));
            golem.spawnToAll();
            return true;
        }

        if (Waxable.super.onActivate(item, player) || Oxidizable.super.onActivate(item, player)) {
            return true;
        }

        return false;
    }

    @Override
    public int onUpdate(int type) {
        int result = Oxidizable.super.onUpdate(type);
        if (result != 0) {
            return result;
        }
        return 0;
    }

    @Override
    public boolean isWaxed() {
        return false;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean setWaxed(boolean waxed) {
        if (isWaxed() == waxed) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(waxed, getOxidizationLevel()), this.getDamage()));
    }

    @Override
    public boolean setOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        if (getOxidizationLevel().equals(oxidizationLevel)) {
            return true;
        }
        return getValidLevel().setBlock(this, Block.get(getCopperId(isWaxed(), oxidizationLevel), this.getDamage()));
    }

    @Override
    public Block getStateWithOxidizationLevel(@NotNull OxidizationLevel oxidizationLevel) {
        return Block.get(getCopperId(isWaxed(), oxidizationLevel), this.getDamage());
    }

    @Override
    @NotNull
    public Class<? extends BlockEntityCopperGolemStatue> getBlockEntityClass() {
        return BlockEntityCopperGolemStatue.class;
    }

    @Override
    @NotNull
    public String getBlockEntityType() {
        return BlockEntity.COPPER_GOLEM_STATUE;
    }

    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return getId();
        }
        return switch (oxidizationLevel) {
            case UNAFFECTED -> waxed ? WAXED_COPPER_GOLEM_STATUE : COPPER_GOLEM_STATUE;
            case EXPOSED -> waxed ? WAXED_EXPOSED_COPPER_GOLEM_STATUE : EXPOSED_COPPER_GOLEM_STATUE;
            case WEATHERED -> waxed ? WAXED_WEATHERED_COPPER_GOLEM_STATUE : WEATHERED_COPPER_GOLEM_STATUE;
            case OXIDIZED -> waxed ? WAXED_OXIDIZED_COPPER_GOLEM_STATUE : OXIDIZED_COPPER_GOLEM_STATUE;
            default -> getId();
        };
    }

    private CompoundTag buildItemNbt(@Nullable Item item) {
        CompoundTag nbt = new CompoundTag();
        if (item != null) {
            if (item.hasCustomName()) {
                nbt.putString("CustomName", item.getCustomName());
            }
            if (item.hasCustomBlockData()) {
                for (Tag tag : item.getCustomBlockData().getAllTags()) {
                    nbt.put(tag.getName(), tag);
                }
            }
        }
        return nbt;
    }
}
