package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * Block entity for Copper Golem Statue blocks.
 * Manages the pose state and entity data for the copper golem statue.
 * <p>
 * Adapted from EaseCation/Nukkit (<a href="https://github.com/EaseCation/Nukkit">EaseCation/Nukkit</a>)
 */
public class BlockEntityCopperGolemStatue extends BlockEntitySpawnable {

    public static final int POSE_STANDING = 0;
    public static final int POSE_SITTING = 1;
    public static final int POSE_RUNNING = 2;
    public static final int POSE_STAR = 3;

    private String actorIdentifier;
    private CompoundTag actorSaveData;
    private int pose;

    public BlockEntityCopperGolemStatue(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        if (namedTag.contains("Actor")) {
            CompoundTag actorTag = namedTag.getCompound("Actor");
            this.actorIdentifier = actorTag.contains("ActorIdentifier")
                    ? actorTag.getString("ActorIdentifier")
                    : "minecraft:copper_golem<>";
            this.actorSaveData = actorTag.contains("SaveData")
                    ? actorTag.getCompound("SaveData")
                    : new CompoundTag();
        } else {
            this.actorIdentifier = "minecraft:copper_golem<>";
            this.actorSaveData = new CompoundTag();
        }

        this.pose = (namedTag.contains("Pose") ? namedTag.getInt("Pose") : POSE_STANDING) & 3;

        super.initBlockEntity();
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        if (actorIdentifier != null) {
            CompoundTag actorTag = new CompoundTag();
            actorTag.putString("ActorIdentifier", this.actorIdentifier);
            actorTag.putCompound("SaveData", this.actorSaveData);
            namedTag.putCompound("Actor", actorTag);
        }

        namedTag.putInt("Pose", pose);
    }

    @Override
    public boolean isBlockEntityValid() {
        int blockId = getBlock().getId();
        return blockId == Block.COPPER_GOLEM_STATUE
                || blockId == Block.EXPOSED_COPPER_GOLEM_STATUE
                || blockId == Block.WEATHERED_COPPER_GOLEM_STATUE
                || blockId == Block.OXIDIZED_COPPER_GOLEM_STATUE
                || blockId == Block.WAXED_COPPER_GOLEM_STATUE
                || blockId == Block.WAXED_EXPOSED_COPPER_GOLEM_STATUE
                || blockId == Block.WAXED_WEATHERED_COPPER_GOLEM_STATUE
                || blockId == Block.WAXED_OXIDIZED_COPPER_GOLEM_STATUE;
    }

    @Override
    public @NotNull CompoundTag getSpawnCompound() {
        return getDefaultCompound(this, COPPER_GOLEM_STATUE)
                .putInt("Pose", pose);
    }

    public int getPose() {
        return pose;
    }

    public void setPose(int pose) {
        this.pose = pose & 3;
    }

    public void changePose() {
        setPose(getPose() + 1);
        this.spawnToAll();
    }

    public String getActorIdentifier() {
        return actorIdentifier;
    }

    public void setActorIdentifier(String actorIdentifier) {
        this.actorIdentifier = actorIdentifier;
    }

    public CompoundTag getActorSaveData() {
        return actorSaveData;
    }

    public void setActorSaveData(CompoundTag actorSaveData) {
        this.actorSaveData = actorSaveData;
    }

    public void initActorDataFromItem(@NotNull cn.nukkit.item.Item item) {
        if (item.hasCustomBlockData()) {
            CompoundTag customBlockData = item.getCustomBlockData();
            if (customBlockData.contains("Actor") && customBlockData.get("Actor") instanceof CompoundTag actorTag) {
                if (actorTag.contains("ActorIdentifier")) {
                    this.actorIdentifier = actorTag.getString("ActorIdentifier");
                }
                if (actorTag.contains("SaveData") && actorTag.get("SaveData") instanceof CompoundTag saveData) {
                    this.actorSaveData = saveData;
                }
            }
        }
    }
}
