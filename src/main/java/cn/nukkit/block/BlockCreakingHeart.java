package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityCreakingHeart;
import cn.nukkit.entity.mob.EntityCreaking;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class BlockCreakingHeart extends BlockSolidMeta implements BlockEntityHolder<BlockEntityCreakingHeart> {

    private static final int AXIS_MASK = 0b11;
    public static final int AXIS_Y = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Z = 2;

    public static final int STATE_MASK = 0b1100;
    public static final int STATE_UPROOTED = 0;
    public static final int STATE_DORMANT = 1;
    public static final int STATE_AWAKE = 2;

    public static final int NATURAL_BIT = 0b10000;

    public BlockCreakingHeart() {
        this(0);
    }

    public BlockCreakingHeart(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CREAKING_HEART;
    }

    @Override
    public String getName() {
        return "Creaking Heart";
    }

    @Override
    public double getHardness() {
        return 10;
    }

    @Override
    public double getResistance() {
        return 10;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ORANGE_BLOCK_COLOR;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, Player player) {
        int axis = AXIS_Y;
        if (face == BlockFace.WEST || face == BlockFace.EAST) {
            axis = AXIS_X;
        } else if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
            axis = AXIS_Z;
        }

        int damage = (this.getDamage() & ~AXIS_MASK) | axis;
        if (hasPaleOakLogs(axis)) {
            damage = (damage & ~STATE_MASK) | (STATE_DORMANT << 2);
        } else {
            damage = damage & ~STATE_MASK;
        }
        this.setDamage(damage);
        if (!super.place(item, block, target, face, fx, fy, fz, player)) {
            return false;
        }

        CompoundTag nbt = BlockEntity.getDefaultCompound(this, getBlockEntityType());
        nbt.putInt("Axis", axis);
        BlockEntityCreakingHeart blockEntity = createBlockEntity(nbt);
        return blockEntity != null;
    }

    @Override
    public void onNeighborChange(@NotNull BlockFace side) {
        BlockEntityCreakingHeart blockEntity = this.getBlockEntity();
        if (blockEntity != null && blockEntity.getLinkedCreaking() != null) {
            return;
        }

        int axis = this.getDamage() & AXIS_MASK;
        int currentState = (this.getDamage() & STATE_MASK) >> 2;
        if (currentState == STATE_AWAKE) {
            return;
        }

        boolean hasLogs = hasPaleOakLogs(axis);
        int newState = hasLogs ? STATE_DORMANT : STATE_UPROOTED;
        int newDamage = (this.getDamage() & ~STATE_MASK) | (newState << 2);
        if (newDamage != this.getDamage()) {
            this.setDamage(newDamage);
            this.getLevel().setBlock(this, this, true, true);
        }
    }

    @Override
    public boolean onBreak(Item item, Player player) {
        BlockEntityCreakingHeart blockEntity = this.getBlockEntity();
        if (blockEntity != null) {
            blockEntity.removeProtector();
        }
        return super.onBreak(item, player);
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_RANDOM) {
            int currentState = getState();
            if (currentState == STATE_UPROOTED) {
                return 0;
            }

            if (this.level.isDaytime() && !this.level.isRaining() && !this.level.isThundering()) {
                if (currentState == STATE_AWAKE) {
                    this.setDamage((this.getDamage() & ~STATE_MASK) | (STATE_DORMANT << 2));
                    this.getLevel().setBlock(this, this, true, true);
                    BlockEntityCreakingHeart be = this.getBlockEntity();
                    if (be != null) {
                        be.onHeartDormant();
                    }
                }
            } else {
                if (currentState == STATE_DORMANT) {
                    int axis = this.getDamage() & AXIS_MASK;
                    if (hasPaleOakLogs(axis)) {
                        this.setDamage((this.getDamage() & ~STATE_MASK) | (STATE_AWAKE << 2));
                    } else {
                        this.setDamage(this.getDamage() & ~STATE_MASK);
                    }
                    this.getLevel().setBlock(this, this, true, true);
                }
            }
            return type;
        }
        return 0;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntityCreakingHeart blockEntity = this.getBlockEntity();
        if (blockEntity == null) {
            return 0;
        }

        EntityCreaking creaking = blockEntity.getLinkedCreaking();
        if (creaking == null) {
            return 0;
        }

        int signal = 15 - (int) Math.floor(creaking.distance(this) / 32d * 15d);
        return Math.max(0, Math.min(15, signal));
    }

    public int getState() {
        return (this.getDamage() & STATE_MASK) >> 2;
    }

    public boolean isActive() {
        return getState() == STATE_AWAKE;
    }

    public boolean isNatural() {
        return (this.getDamage() & NATURAL_BIT) != 0;
    }

    public void setActive(boolean active) {
        int state = active ? STATE_AWAKE : (hasPaleOakLogs(this.getDamage() & AXIS_MASK) ? STATE_DORMANT : STATE_UPROOTED);
        this.setDamage((this.getDamage() & ~STATE_MASK) | (state << 2));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                new ItemBlock(Block.get(RESIN_CLUMP), 0, ThreadLocalRandom.current().nextInt(1, 4)),
        };
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBePulled() {
        return false;
    }

    private boolean hasPaleOakLogs(int axis) {
        BlockFace[] faces = getAxisFaces(axis);
        return isPaleOakLog(this.getSide(faces[0])) && isPaleOakLog(this.getSide(faces[1]));
    }

    private static BlockFace[] getAxisFaces(int axis) {
        return switch (axis) {
            case AXIS_X -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case AXIS_Z -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            default -> new BlockFace[]{BlockFace.UP, BlockFace.DOWN};
        };
    }

    private static boolean isPaleOakLog(Block block) {
        int id = block.getId();
        return id == Block.PALE_OAK_LOG || id == Block.STRIPPED_PALE_OAK_LOG
                || id == Block.PALE_OAK_WOOD || id == Block.STRIPPED_PALE_OAK_WOOD;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityCreakingHeart> getBlockEntityClass() {
        return BlockEntityCreakingHeart.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.CREAKING_HEART;
    }
}
