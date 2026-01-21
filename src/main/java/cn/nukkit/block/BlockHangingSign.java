package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityHangingSign;
import cn.nukkit.blockentity.BlockEntitySign;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.OpenSignPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;

public abstract class BlockHangingSign extends BlockSignPost {

    public static final int FACING_DIRECTION_MASK = 0b111;
    public static final int ATTACHED_DIRECTION_MASK = 0b1111_000;
    public static final int ATTACHED_DIRECTION_START = 3;
    public static final int HANGING_BIT = 0b1_0000_000;
    public static final int ATTACHED_BIT = 0b10_0000_000;

    public BlockHangingSign() {
        this(0);
    }

    public BlockHangingSign(int meta) {
        super(meta);
    }

    @Override
    public abstract int getId();

    @Override
    public abstract String getName();

    @NotNull
    @Override
    public Class<? extends BlockEntityHangingSign> getBlockEntityClass() {
        return BlockEntityHangingSign.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.HANGING_SIGN;
    }

    @Override
    public boolean isHangingSign() {
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return null;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        if (isHanging()) {
            return null;
        }
        if (getBlockFace().getAxis() == BlockFace.Axis.Z) {
            return shrink(0, 0, 6 / 16.0);
        }
        return shrink(6 / 16.0, 0, 0);
    }

    @Override
    public Item toItem() {
        return Item.get(this.getItemId());
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        setDamage(0);

        if (face == BlockFace.DOWN) {
            // 从下方放置，悬挂在上方方块下面
            setHanging(true);

            Block above = target;
            if (above.isSolid()) {
                // 上方是实心方块，使用朝向模式
                if (player != null) {
                    setFacingDirection(player.getHorizontalFacing().getOpposite().getIndex());
                } else {
                    setFacingDirection(2);
                }
            } else if (above instanceof BlockHangingSign) {
                // 上方是悬挂告示牌，使用附着模式
                if (player != null) {
                    int attachedDirection = (int) Math.floor(((player.yaw + 180) * 16 / 360) + 0.5) & 0xf;
                    setAttached(true);
                    setAttachedDirection(attachedDirection);
                } else {
                    setFacingDirection(2);
                }
            } else if (above instanceof BlockFence || above instanceof BlockChain) {
                // 上方是栅栏或锁链，使用附着模式
                if (player != null) {
                    int attachedDirection = (int) Math.floor(((player.yaw + 180) * 16 / 360) + 0.5) & 0xf;
                    setAttached(true);
                    setAttachedDirection(attachedDirection);
                } else {
                    setFacingDirection(2);
                }
            } else {
                return false;
            }
        } else if (face == BlockFace.UP) {
            // 不能从上方放置悬挂告示牌
            return false;
        } else {
            // 从侧面放置，附着在侧面方块上
            if (!target.isSolid() && !(target instanceof BlockHangingSign)) {
                return false;
            }
            if (player != null) {
                BlockFace cw = face.rotateY();
                setFacingDirection(cw.getIndex());
            } else {
                setFacingDirection(2);
            }
        }

        if (!this.getLevel().setBlock(block, this, true)) {
            return false;
        }

        CompoundTag nbt = new CompoundTag()
                .putString("id", BlockEntity.HANGING_SIGN)
                .putInt("x", (int) block.x)
                .putInt("y", (int) block.y)
                .putInt("z", (int) block.z);

        if (item.hasCustomBlockData()) {
            for (Tag aTag : item.getCustomBlockData().getAllTags()) {
                nbt.put(aTag.getName(), aTag);
            }
        }

        BlockEntity blockEntity = BlockEntity.createBlockEntity(BlockEntity.HANGING_SIGN, this.level.getChunk(block.getChunkX(), block.getChunkZ()), nbt);
        if (player != null && blockEntity instanceof BlockEntitySign blockEntitySign) {
            blockEntitySign.setEditorEntityRuntimeId(player.getId());
            if (player.protocol >= ProtocolInfo.v1_19_80) {
                OpenSignPacket openSignPacket = new OpenSignPacket();
                openSignPacket.setPosition(this.asBlockVector3());
                openSignPacket.setFrontSide(true);
                player.dataPacket(openSignPacket);
            }
        }

        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (!isHanging()) {
                return 0;
            }
            Block above = up();
            if (!above.isSolid() && !(above instanceof BlockHangingSign) && !(above instanceof BlockFence) && !(above instanceof BlockChain)) {
                getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        }
        return 0;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromIndex(getFacingDirection());
    }

    public int getFacingDirection() {
        return getDamage() & FACING_DIRECTION_MASK;
    }

    public void setFacingDirection(int direction) {
        setDamage((getDamage() & ~(ATTACHED_DIRECTION_MASK | FACING_DIRECTION_MASK)) | direction);
    }

    public int getAttachedDirection() {
        return (getDamage() & ATTACHED_DIRECTION_MASK) >> ATTACHED_DIRECTION_START;
    }

    public void setAttachedDirection(int attachedDirection) {
        setDamage((getDamage() & ~(ATTACHED_DIRECTION_MASK | FACING_DIRECTION_MASK)) | (attachedDirection << ATTACHED_DIRECTION_START));
    }

    public boolean isHanging() {
        return (getDamage() & HANGING_BIT) != 0;
    }

    public void setHanging(boolean hanging) {
        setDamage(hanging ? getDamage() | HANGING_BIT : getDamage() & ~HANGING_BIT);
    }

    public boolean isAttached() {
        return (getDamage() & ATTACHED_BIT) != 0;
    }

    public void setAttached(boolean attached) {
        setDamage(attached ? getDamage() | ATTACHED_BIT : getDamage() & ~ATTACHED_BIT);
    }
}
