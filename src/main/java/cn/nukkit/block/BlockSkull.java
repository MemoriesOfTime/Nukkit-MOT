package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySkull;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSkull;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;

import org.jetbrains.annotations.NotNull;

import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;

/**
 * @author Justin
 */
public class BlockSkull extends BlockTransparentMeta implements Faceable, BlockPropertiesHelper, BlockEntityHolder<BlockEntitySkull> {

    protected static final BlockProperties PROPERTIES = new BlockProperties(VanillaProperties.FACING_DIRECTION);

    public BlockSkull() {
        this(0);
    }

    public BlockSkull(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SKULL_BLOCK;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntitySkull> getBlockEntityClass() {
        return BlockEntitySkull.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.SKULL;
    }

    @Override
    public double getHardness() {
        return 1;
    }

    @Override
    public double getResistance() {
        return 5;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public boolean canBeFlowedInto() {
        return true;
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
    public BlockFace getBlockFace() {
        return getPropertyValue(VanillaProperties.FACING_DIRECTION);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        setPropertyValue(VanillaProperties.FACING_DIRECTION, face);
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:skull";
    }

    @Override
    public String getName() {
        int itemMeta = 0;

        if (this.level != null) {
            BlockEntity blockEntity = getLevel().getBlockEntity(this);
            if (blockEntity != null) itemMeta = blockEntity.namedTag.getByte("SkullType");
        }

        return ItemSkull.getItemSkullName(itemMeta);
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (face == BlockFace.DOWN) {
            return false;
        }

        setBlockFace(face);

        this.getLevel().setBlock(block, this, true, true);

        CompoundTag nbt = new CompoundTag()
                .putString("id", BlockEntity.SKULL)
                .putByte("SkullType", item.getDamage())
                .putInt("x", block.getFloorX())
                .putInt("y", block.getFloorY())
                .putInt("z", block.getFloorZ())
                .putByte("Rot", (int) Math.floor((player.yaw * 16 / 360) + 0.5) & 0x0f);
        if (item.hasCustomBlockData()) {
            for (Tag aTag : item.getCustomBlockData().getAllTags()) {
                nbt.put(aTag.getName(), aTag);
            }
        }

        BlockEntitySkull blockEntity = (BlockEntitySkull) BlockEntity.createBlockEntity(BlockEntity.SKULL, this.getChunk(), nbt);
        blockEntity.spawnToAll();

        // TODO: 2016/2/3 SPAWN WITHER

        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        BlockEntity blockEntity = getLevel().getBlockEntity(this);
        int dropMeta = 0;
        if (blockEntity != null) dropMeta = blockEntity.namedTag.getByte("SkullType");
        return new Item[]{
                new ItemSkull(dropMeta)
        };
    }

    @Override
    public Item toItem() {
        BlockEntity blockEntity = getLevel().getBlockEntity(this);
        int itemMeta = 0;
        if (blockEntity != null) itemMeta = blockEntity.namedTag.getByte("SkullType");
        return Item.get(Item.SKULL, itemMeta);
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.AIR_BLOCK_COLOR;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        AxisAlignedBB bb = new SimpleAxisAlignedBB(this.x + 0.25, this.y, this.z + 0.25, this.x + 1 - 0.25, this.y + 0.5, this.z + 1 - 0.25);
        switch (this.getBlockFace()) {
            case NORTH:
                return bb.offset(0, 0.25, 0.25);
            case SOUTH:
                return bb.offset(0, 0.25, -0.25);
            case WEST:
                return bb.offset(0.25, 0.25, 0);
            case EAST:
                return bb.offset(-0.25, 0.25, 0);
        }
        return bb;
    }

    @Override
    public boolean alwaysDropsOnExplosion() {
        return true;
    }
}
