package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityMovingBlock;
import cn.nukkit.blockentity.BlockEntityPistonArm;
import cn.nukkit.event.block.BlockPistonEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Faceable;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author CreeperFace
 */
public abstract class BlockPistonBase extends BlockSolidMeta implements Faceable, BlockEntityHolder<BlockEntityPistonArm> {

    public boolean sticky = false;

    public BlockPistonBase() {
        this(0);
    }

    public BlockPistonBase(int meta) {
        super(meta);
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityPistonArm> getBlockEntityClass() {
        return BlockEntityPistonArm.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.PISTON_ARM;
    }

    @Override
    public double getResistance() {
        return 2.5;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (Math.abs(player.getFloorX() - this.x) < 2 && Math.abs(player.getFloorZ() - this.z) < 2) {
            double y = player.y + player.getEyeHeight();

            if (y - this.y > 2) {
                this.setDamage(BlockFace.UP.getIndex());
            } else if (this.y - y > 0) {
                this.setDamage(BlockFace.DOWN.getIndex());
            } else {
                this.setDamage(player.getHorizontalFacing().getIndex());
            }
        } else {
            this.setDamage(player.getHorizontalFacing().getIndex());
        }
        if(this.level.getBlockEntity(this) != null) {
            BlockEntity blockEntity = this.level.getBlockEntity(this);
            blockEntity.saveNBT();
            blockEntity.close();
        }
        this.level.setBlock(block, this, true, true);

        CompoundTag nbt = new CompoundTag("")
                .putString("id", BlockEntity.PISTON_ARM)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putInt("facing", this.getBlockFace().getIndex())
                .putBoolean("Sticky", this.sticky);

        BlockEntityPistonArm piston = (BlockEntityPistonArm) BlockEntity.createBlockEntity(BlockEntity.PISTON_ARM, this.level.getChunk(getChunkX(), getChunkZ()), nbt);
        piston.powered = this.isPowered();

        this.checkState(piston.powered);
        return true;
    }

    @Override
    public boolean onBreak(Item item) {
        this.level.setBlock(this, Block.get(BlockID.AIR), true, true);

        Block block = this.getSide(this.getBlockFace());
        if (block instanceof BlockPistonHead && ((BlockPistonHead) block).getBlockFace() == this.getBlockFace()) {
            block.onBreak(item);
        }
        return true;
    }

    public boolean isExtended() {
        BlockFace face = this.getBlockFace();
        Block block = this.getSide(face);
        return block instanceof BlockPistonHead && ((BlockPistonHead) block).getBlockFace() == face;
    }

    @Override
    public int onUpdate(int type) {
        if (type != Level.BLOCK_UPDATE_NORMAL && type != Level.BLOCK_UPDATE_REDSTONE && type != Level.BLOCK_UPDATE_SCHEDULED) {
            return 0;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(this);
        if (blockEntity instanceof BlockEntityPistonArm) {
            BlockEntityPistonArm arm = (BlockEntityPistonArm) blockEntity;
            boolean powered = this.isPowered();

            if (arm.state % 2 == 0 && arm.powered != powered && this.checkState(powered)) {
                arm.powered = powered;
                if (arm.chunk != null) {
                    arm.chunk.setChanged();
                }
            }
        }
        return type;
    }

    private boolean checkState(Boolean isPowered) {
        if (isPowered == null) {
            isPowered = this.isPowered();
        }

        if (isPowered && !this.isExtended()) {
            if (!this.doMove(true)) {
                return false;
            }

            this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_PISTON_OUT);
            return true;
        } else if (!isPowered && isExtended()) {
            if (!this.doMove(false)) {
                return false;
            }

            this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_PISTON_IN);
            return true;
        }

        return false;
    }

    private boolean isPowered() {
        BlockFace face = this.getBlockFace();

        for (BlockFace side : BlockFace.values()) {
            if (side == face) {
                continue;
            }

            Block b = this.getSide(side);
            if (b.getId() == Block.REDSTONE_WIRE && b.getDamage() > 0) {
                return true;
            }

            if (this.level.isSidePowered(b, side)) {
                return true;
            }
        }
        return false;
    }

    private boolean doMove(boolean extending) {
        BlockFace direction = getBlockFace();
        BlocksCalculator calculator = new BlocksCalculator(extending);

        boolean canMove = calculator.canMove();
        if (!canMove && extending) {
            return false;
        }

        BlockPistonEvent event = new BlockPistonEvent(this, direction, calculator.getBlocksToMove(), calculator.getBlocksToDestroy(), extending);
        this.level.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        List<BlockVector3> attached = Collections.emptyList();
        if (canMove && (this.sticky || extending)) {
            List<Block> destroyBlocks = calculator.getBlocksToDestroy();
            for (int i = destroyBlocks.size() - 1; i >= 0; --i) {
                Block block = destroyBlocks.get(i);
                this.level.useBreakOn(block, null, null, false);

                if (Server.getInstance().dropSpawners && block instanceof BlockMobSpawner){
                    this.level.dropItem(block.add(0.5, 0.5, 0.5), Item.get(Item.MONSTER_SPAWNER, 0, 1));
                }
            }

            List<Block> newBlocks = calculator.getBlocksToMove();
            attached = newBlocks.stream().map(Vector3::asBlockVector3).collect(Collectors.toList());
            BlockFace side = extending ? direction : direction.getOpposite();

            List<CompoundTag> namedTags = new ArrayList<>();
            for (Block oldBlock : newBlocks){
                CompoundTag tag = null;
                BlockEntity blockEntity = this.level.getBlockEntity(oldBlock);
                if (blockEntity != null && !(blockEntity instanceof BlockEntityMovingBlock)) {
                    blockEntity.saveNBT();
                    tag = new CompoundTag(blockEntity.namedTag.getTags());
                    blockEntity.close();
                }
                namedTags.add(tag);
            }

            for (int i = 0; i < newBlocks.size(); i++){
                Block newBlock = newBlocks.get(i);
                Vector3 oldPos = newBlock.add(0);
                newBlock.position(newBlock.add(0).getSide(side));
                this.level.setBlock(newBlock, Block.get(BlockID.MOVING_BLOCK), true);

                CompoundTag nbt = BlockEntity.getDefaultCompound(newBlock, BlockEntity.MOVING_BLOCK)
                        .putInt("pistonPosX", this.getFloorX())
                        .putInt("pistonPosY", this.getFloorY())
                        .putInt("pistonPosZ", this.getFloorZ())
                        .putCompound("movingBlock", new CompoundTag()
                                .putInt("id", newBlock.getId())
                                .putInt("meta", newBlock.getDamage())
                        );

                if (namedTags.get(i) != null){
                    nbt.putCompound("movingEntity", namedTags.get(i));
                }

                BlockEntity.createBlockEntity(BlockEntity.MOVING_BLOCK, newBlock, nbt);
                if (this.level.getBlockIdAt(oldPos.getFloorX(), oldPos.getFloorY(), oldPos.getFloorZ()) != BlockID.MOVING_BLOCK) {
                    this.level.setBlock(oldPos, Block.get(BlockID.AIR));
                }
            }
        }

        if (extending) {
            this.level.setBlock(this.getSide(direction), this.createHead(this.getDamage()));
        }

        BlockEntityPistonArm blockEntity = (BlockEntityPistonArm) this.level.getBlockEntity(this);
        blockEntity.move(extending, attached);
        return true;
    }

    public abstract int getPistonHeadBlockId();

    protected BlockPistonHead createHead(int damage) {
        return (BlockPistonHead) Block.get(this.getPistonHeadBlockId(), damage);
    }

    public static boolean canPush(Block block, BlockFace face, boolean destroyBlocks, boolean extending) {
        Level level = block.getLevel();
        int minBlockY = level.getMinBlockY();
        int maxBlockY = level.getMaxBlockY();
        if (block.getY() >= minBlockY && (face != BlockFace.DOWN || block.getY() != minBlockY) && block.getY() <= maxBlockY && (face != BlockFace.UP || block.getY() != maxBlockY)) {
            if (extending && !block.canBePushed() || !extending && !block.canBePulled()) {
                return false;
            }

            if (block.breaksWhenMoved()) {
                return destroyBlocks || block.sticksToPiston();
            }

            BlockEntity be = block.level.getBlockEntity(block);
            return be == null || be.isMovable();
        }
        return false;
    }

    public class BlocksCalculator {

        private final Vector3 pistonPos;
        private Vector3 armPos;
        private final Block blockToMove;
        private final BlockFace moveDirection;
        private final boolean extending;

        private final List<Block> toMove = new ArrayList<>();
        private final List<Block> toDestroy = new ArrayList<>();

        public BlocksCalculator(boolean extending) {
            this.pistonPos = getLocation();
            this.extending = extending;

            BlockFace face = getBlockFace();
            if (!extending) {
                this.armPos = pistonPos.getSide(face);
            }

            if (extending) {
                this.moveDirection = face;
                this.blockToMove = getSide(face);
            } else {
                this.moveDirection = face.getOpposite();
                if (sticky) {
                    this.blockToMove = getSide(face, 2);
                } else {
                    this.blockToMove = null;
                }
            }
        }

        public boolean canMove() {
            if (!sticky && !extending) {
                return true;
            }

            this.toMove.clear();
            this.toDestroy.clear();
            Block block = this.blockToMove;

            if (!canPush(block, this.moveDirection, true, extending)) {
                return false;
            }

            if (block.breaksWhenMoved()) {
                if (extending || block.sticksToPiston()) {
                    this.toDestroy.add(this.blockToMove);
                }
                return true;
            }

            if (!this.addBlockLine(this.blockToMove, this.moveDirection)) {
                return false;
            }

            for (Block b : new ArrayList<>(this.toMove)) {
                int blockId = b.getId();
                if ((blockId == SLIME_BLOCK) && !this.addBranchingBlocks(b)) {
                    return false;
                }
            }
            return true;
        }

        private boolean addBlockLine(Block origin, BlockFace from) {
            Block block = origin.clone();

            if (block.getId() == AIR) {
                return true;
            }

            if (!canPush(origin, this.moveDirection, false, extending)) {
                return true;
            }

            if (origin.equals(this.pistonPos)) {
                return true;
            }

            if (this.toMove.contains(origin)) {
                return true;
            }

            if (this.toMove.size() >= 12) {
                return false;
            }

            this.toMove.add(block);

            int count = 1;
            List<Block> sticked = new ArrayList<>();

            while (block.getId() == SLIME_BLOCK) {
                block = origin.getSide(this.moveDirection.getOpposite(), count);

                if (block.getId() == AIR || !canPush(block, this.moveDirection, false, extending) || block.equals(this.pistonPos)) {
                    break;
                }

                if (block.breaksWhenMoved() && block.sticksToPiston()) {
                    this.toDestroy.add(block);
                    break;
                }

                if (++count + this.toMove.size() > 12) {
                    return false;
                }

                sticked.add(block);
            }

            int stickedCount = sticked.size();

            if (stickedCount > 0) {
                this.toMove.addAll(Lists.reverse(sticked));
            }

            int step = 1;

            while (true) {
                Block nextBlock = origin.getSide(this.moveDirection, step);
                int index = this.toMove.indexOf(nextBlock);

                if (index > -1) {
                    this.reorderListAtCollision(stickedCount, index);

                    for (int i = 0; i <= index + stickedCount; ++i) {
                        Block b = this.toMove.get(i);

                        if (b.getId() == SLIME_BLOCK && !this.addBranchingBlocks(b)) {
                            return false;
                        }
                    }

                    return true;
                }

                if (nextBlock.getId() == AIR || nextBlock.equals(armPos)) {
                    return true;
                }

                if (!canPush(nextBlock, this.moveDirection, true, extending) || nextBlock.equals(this.pistonPos)) {
                    return false;
                }

                if (nextBlock.breaksWhenMoved()) {
                    this.toDestroy.add(nextBlock);
                    return true;
                }

                if (this.toMove.size() >= 12) {
                    return false;
                }

                this.toMove.add(nextBlock);
                ++stickedCount;
                ++step;
            }
        }

        private void reorderListAtCollision(int count, int index) {
            List<Block> list = new ArrayList<>(this.toMove.subList(0, index));
            List<Block> list1 = new ArrayList<>(this.toMove.subList(this.toMove.size() - count, this.toMove.size()));
            List<Block> list2 = new ArrayList<>(this.toMove.subList(index, this.toMove.size() - count));
            this.toMove.clear();
            this.toMove.addAll(list);
            this.toMove.addAll(list1);
            this.toMove.addAll(list2);
        }

        private boolean addBranchingBlocks(Block block) {
            for (BlockFace face : BlockFace.values()) {
                if (face.getAxis() != this.moveDirection.getAxis() && !this.addBlockLine(block.getSide(face), face)) {
                    return false;
                }
            }

            return true;
        }

        public List<Block> getBlocksToMove() {
            return this.toMove;
        }

        public List<Block> getBlocksToDestroy() {
            return this.toDestroy;
        }
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public BlockFace getBlockFace() {
        BlockFace face = BlockFace.fromIndex(this.getDamage());
        return face.getHorizontalIndex() >= 0 ? face.getOpposite() : face;
    }
}