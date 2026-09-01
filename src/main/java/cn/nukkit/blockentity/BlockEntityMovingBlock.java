package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockChest;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * Created by CreeperFace on 11.4.2017.
 */
public class BlockEntityMovingBlock extends BlockEntitySpawnable {

    protected Block block;
    protected BlockVector3 piston;

    public BlockEntityMovingBlock(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        if (namedTag.contains("movingBlock")) {
            CompoundTag blockData = namedTag.getCompound("movingBlock");
            this.block = Block.get(blockData.getInt("id"), blockData.getInt("meta"));
        } else {
            this.close();
            return;
        }

        if (namedTag.contains("pistonPosX") && namedTag.contains("pistonPosY") && namedTag.contains("pistonPosZ")) {
            this.piston = new BlockVector3(namedTag.getInt("pistonPosX"), namedTag.getInt("pistonPosY"), namedTag.getInt("pistonPosZ"));
        } else {
            this.piston = new BlockVector3(0, -1, 0);
        }

        super.initBlockEntity();

        // Must use the block entity update queue, not the block update queue — MOVING_BLOCK's
        // registered block class has no onUpdate, so block updates would never invoke this.
        this.scheduleUpdate();
    }

    @Override
    public boolean onUpdate() {
        // Verify the piston still exists and is properly moving this block.
        if (this.level != null) {
            if (!this.level.isChunkLoaded(this.piston.x >> 4, this.piston.z >> 4)) {
                this.restoreBlock();
                return false;
            }

            BlockEntity pistonEntity = this.level.getBlockEntity(this.piston);
            if (!(pistonEntity instanceof BlockEntityPistonArm)) {
                this.restoreBlock();
                return false;
            }

            BlockEntityPistonArm piston = (BlockEntityPistonArm) pistonEntity;
            boolean isAttached = false;
            BlockFace pushDir = piston.extending ? piston.facing : piston.facing.getOpposite();
            BlockVector3 thisPos = new BlockVector3(this.getFloorX(), this.getFloorY(), this.getFloorZ());
            for (BlockVector3 attachedPos : piston.attachedBlocks) {
                if (attachedPos.getSide(pushDir).equals(thisPos)) {
                    isAttached = true;
                    break;
                }
            }

            if (!isAttached) {
                this.restoreBlock();
                return false;
            }
        }

        return super.onUpdate();
    }

    Block restoreBlock() {
        Level level = this.level;
        Block movedBlock = this.block;
        CompoundTag blockEntityNbt = this.getBlockEntity();
        int blockX = this.getFloorX();
        int blockY = this.getFloorY();
        int blockZ = this.getFloorZ();

        this.close();
        if (level == null || movedBlock == null) {
            return movedBlock;
        }

        level.setBlock(blockX, blockY, blockZ, movedBlock, true, true);
        if (blockEntityNbt != null) {
            blockEntityNbt.putInt("x", blockX);
            blockEntityNbt.putInt("y", blockY);
            blockEntityNbt.putInt("z", blockZ);
            BlockEntity blockEntity = BlockEntity.createBlockEntity(
                    blockEntityNbt.getString("id"),
                    level.getChunk(blockX >> 4, blockZ >> 4),
                    blockEntityNbt);
            if (blockEntity != null && blockEntity.getBlock() instanceof BlockChest chest) {
                chest.tryPair();
            }
        }
        return movedBlock;
    }

    public CompoundTag getBlockEntity() {
        if (this.namedTag.contains("movingEntity")) {
            return this.namedTag.getCompound("movingEntity");
        }

        return null;
    }

    @Override
    public Block getBlock() {
        return this.block;
    }

    public void moveCollidedEntities(BlockEntityPistonArm piston, BlockFace moveDirection) {
        AxisAlignedBB bb = block.getBoundingBox();

        if (bb == null) {
            return;
        }

        bb = bb.getOffsetBoundingBox(
                this.x + (piston.progress * moveDirection.getXOffset()) - moveDirection.getXOffset(),
                this.y + (piston.progress * moveDirection.getYOffset()) - moveDirection.getYOffset(),
                this.z + (piston.progress * moveDirection.getZOffset()) - moveDirection.getZOffset()
        );

        Entity[] entities = this.level.getCollidingEntities(bb);

        for (Entity entity : entities) {
            piston.moveEntity(entity, moveDirection);
        }
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.level.getBlockIdAt(getFloorX(), getFloorY(), getFloorZ()) == BlockID.MOVING_BLOCK;
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return getDefaultCompound(this, MOVING_BLOCK)
                .putInt("pistonPosX", this.piston.x)
                .putInt("pistonPosY", this.piston.y)
                .putInt("pistonPosZ", this.piston.z)
                .putCompound("movingBlock", new CompoundTag()
                        .putInt("id", this.block.getId())
                        .putInt("meta", this.block.getDamage())
                );
    }
}
