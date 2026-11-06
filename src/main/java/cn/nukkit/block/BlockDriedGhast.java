package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityHappyGhast;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.Faceable;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockDriedGhast extends BlockTransparentMeta implements Faceable {

    private static final int HYDRATION_STEP_TICKS = 6000; // Four five-minute steps.

    private static final int CARDINAL_DIRECTION_MASK = 0b0011;
    private static final int REHYDRATION_LEVEL_MASK = 0b1100;

    public BlockDriedGhast() {
        this(0);
    }

    public BlockDriedGhast(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Dried Ghast";
    }

    @Override
    public int getId() {
        return DRIED_GHAST;
    }

    @Override
    public double getHardness() {
        return 0;
    }

    @Override
    public double getResistance() {
        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setBlockFace(player == null ? BlockFace.SOUTH : player.getHorizontalFacing().getOpposite());
        this.setRehydrationLevel(0);
        if (!this.getLevel().setBlock(this, this, true, true)) return false;
        this.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        return true;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.FLOW_INTO_BLOCK;
    }

    @Override
    public int onUpdate(int type) {
        Level level = this.getLevel();
        if (level == null || this.layer != 0 || !level.isChunkLoaded(this.getFloorX() >> 4, this.getFloorZ() >> 4)) return 0;
        if (!(level.getBlock(this) instanceof BlockDriedGhast current)) return 0;
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            current.ensureHydrationUpdate();
            return type;
        }
        if (type != Level.BLOCK_UPDATE_SCHEDULED) return 0;

        boolean wet = current.hasWater();
        int stage = current.getRehydrationLevel();
        if (wet && stage == 3) {
            current.hatch();
        } else if (wet || stage > 0) {
            BlockDriedGhast next = (BlockDriedGhast) current.clone();
            next.setRehydrationLevel(stage + (wet ? 1 : -1));
            level.setBlock(current, next, true, true);
        }
        // Fetch again: synchronous world/spawn callbacks may replace the source.
        if (level.getBlock(this) instanceof BlockDriedGhast remaining) remaining.ensureHydrationUpdate();
        return type;
    }

    private boolean hasWater() {
        return this.level.getBlock(this, 1) instanceof BlockWater;
    }

    private void ensureHydrationUpdate() {
        if ((this.hasWater() || this.getRehydrationLevel() > 0) && !this.level.isUpdateScheduled(this, this)) {
            this.level.scheduleUpdate(this, HYDRATION_STEP_TICKS);
        }
    }

    private boolean isReadySource(int meta) {
        Block current = this.level.getBlock(this);
        return current instanceof BlockDriedGhast && current.getDamage() == meta && this.hasWater();
    }

    private void hatch() {
        int meta = this.getDamage();
        Vector3 position = new Vector3(this.x + 0.5, this.y + 0.1, this.z + 0.5);
        CompoundTag nbt = Entity.getDefaultNBT(position).putBoolean("Baby", true).putShort("Age", -24000);
        CreatureSpawnEvent event = new CreatureSpawnEvent(EntityHappyGhast.NETWORK_ID,
                Position.fromObject(position, this.level), nbt, CreatureSpawnEvent.SpawnReason.EGG, null);
        this.level.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || !this.isReadySource(meta)) return;

        var chunk = this.level.getChunkIfLoaded(this.getFloorX() >> 4, this.getFloorZ() >> 4);
        if (chunk == null) return;
        Entity baby = Entity.createEntity(EntityHappyGhast.NETWORK_ID, chunk, nbt);
        if (baby == null) return;
        if (baby.closed || !baby.isAlive() || !this.isReadySource(meta)) {
            if (!baby.closed) baby.close();
            return;
        }
        // Layer 1 is deliberately untouched: hydration consumes the ghast, never its water.
        if (!this.level.setBlock(this, Block.get(AIR), true, true)) {
            baby.close();
            return;
        }
        baby.spawnToAll();
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }

    public void setRehydrationLevel(int level) {
        this.setProperty(REHYDRATION_LEVEL_MASK, level);
    }

    public int getRehydrationLevel() {
        return this.getProperty(REHYDRATION_LEVEL_MASK);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setProperty(CARDINAL_DIRECTION_MASK, face.getHorizontalIndex());
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getProperty(CARDINAL_DIRECTION_MASK));
    }

    private void setProperty(int mask, int value) {
        int shift = Integer.numberOfTrailingZeros(mask);
        int maxValue = mask >>> shift;
        int clampedValue = value & maxValue;
        this.setDamage((this.getDamage() & ~mask) | (clampedValue << shift));
    }

    private int getProperty(int mask) {
        int shift = Integer.numberOfTrailingZeros(mask);
        return (this.getDamage() & mask) >>> shift;
    }
}
