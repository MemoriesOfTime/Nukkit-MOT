package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockBeehive;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockLiquid;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityBee;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class BlockEntityBeehive extends BlockEntity {

    private List<Occupant> occupants;

    public BlockEntityBeehive(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        super.initBlockEntity();

        this.occupants = new ArrayList<>(4);
        if (!this.namedTag.contains("ShouldSpawnBees")) {
            this.namedTag.putByte("ShouldSpawnBees", 0);
        }

        if(!this.namedTag.contains("Occupants")) {
            this.namedTag.putList(new ListTag<>("Occupants"));
        } else {
            ListTag<CompoundTag> occupantsTag = namedTag.getList("Occupants", CompoundTag.class);
            for (int i = 0; i < occupantsTag.size(); i++) {
                this.occupants.add(new Occupant(occupantsTag.get(i)));
            }
        }

        // Backward compatibility
        if (this.namedTag.contains("HoneyLevel")) {
            int faceHorizontalIndex = 0;
            Block block = getBlock();
            if (block instanceof BlockBeehive) {
                faceHorizontalIndex = block.getDamage() & 0b11;
                int honeyLevel = this.namedTag.getByte("HoneyLevel");
                BlockBeehive beehive = (BlockBeehive) block;
                beehive.setBlockFace(BlockFace.fromHorizontalIndex(faceHorizontalIndex));
                beehive.setHoneyLevel(honeyLevel);
                beehive.getLevel().setBlock(beehive, beehive, true, true);
            }
            this.namedTag.remove("HoneyLevel");
        }

        if (!isEmpty()) {
            scheduleUpdate();
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        ListTag<CompoundTag> occupantsTag = new ListTag<>("Occupants");
        for (Occupant occupant : occupants) {
            occupantsTag.add(occupant.saveNBT());
        }
        this.namedTag.putList(occupantsTag);

        // Backward compatibility
        if (this.namedTag.contains("HoneyLevel")) {
            int faceHorizontalIndex = 0;
            Block block = getBlock();
            if (block instanceof BlockBeehive) {
                faceHorizontalIndex = block.getDamage() & 0b11;
                int honeyLevel = this.namedTag.getByte("HoneyLevel");
                BlockBeehive beehive = (BlockBeehive) block;
                beehive.setBlockFace(BlockFace.fromHorizontalIndex(faceHorizontalIndex));
                beehive.setHoneyLevel(honeyLevel);
                beehive.getLevel().setBlock(beehive, beehive, true, true);
            }
            this.namedTag.remove("HoneyLevel");
        }
    }

    public int getHoneyLevel() {
        Block block = getBlock();
        if (block instanceof BlockBeehive) {
            return ((BlockBeehive) block).getHoneyLevel();
        } else {
            return 0;
        }
    }

    public void setHoneyLevel(int honeyLevel) {
        Block block = getBlock();
        if (block instanceof BlockBeehive) {
            ((BlockBeehive) block).setHoneyLevel(honeyLevel);
            block.getLevel().setBlock(block, block, true, true);
        }
    }

    public boolean addOccupant(Occupant occupant) {
        occupants.add(occupant);
        ListTag<CompoundTag> occupants = this.namedTag.getList("Occupants", CompoundTag.class);
        occupants.add(occupant.saveNBT());
        this.namedTag.putList(occupants);
        scheduleUpdate();
        return true;
    }

    public Occupant addOccupant(Entity entity) {
        if (entity instanceof EntityBee bee) {
            boolean hasNectar = bee.getHasNectar();
            return addOccupant(bee, hasNectar ? 2400 : 600, hasNectar, true);
        } else {
            return addOccupant(entity, 600, false, true);
        }
    }

    public Occupant addOccupant(Entity entity, int ticksLeftToStay) {
        return addOccupant(entity, ticksLeftToStay, false, true);
    }

    public Occupant addOccupant(Entity entity, int ticksLeftToStay, boolean hasNectar) {
        return addOccupant(entity, ticksLeftToStay, hasNectar, true);
    }

    public Occupant addOccupant(Entity entity, int ticksLeftToStay, boolean hasNectar, boolean playSound) {
        entity.saveNBT();
        Occupant occupant = new Occupant(ticksLeftToStay, entity.getSaveId(), entity.namedTag.clone());
        if(!addOccupant(occupant)) {
            return null;
        }

        entity.close();
        if (playSound) {
            entity.level.addSound(this, Sound.BLOCK_BEEHIVE_ENTER);
            if (entity.level != null && (entity.level != level || distanceSquared(this) >= 4)) {
                entity.level.addSound(entity, Sound.BLOCK_BEEHIVE_ENTER);
            }
        }
        return occupant;
    }

    public Occupant[] getOccupants() {
        return occupants.toArray(Occupant.EMPTY_ARRAY);
    }

    public boolean removeOccupant(Occupant occupant) {
        return occupants.remove(occupant);
    }

    public boolean isHoneyEmpty() {
        return getHoneyLevel() == BlockBeehive.HONEY_LEVEL_MIN;
    }

    public boolean isHoneyFull() {
        return getHoneyLevel() == BlockBeehive.HONEY_LEVEL_MAX;
    }

    public boolean isEmpty() {
        return occupants.isEmpty();
    }

    public int getOccupantsCount() {
        return occupants.size();
    }
    
    public boolean isSpawnFaceValid(BlockFace face) {
        Block side = getSide(face).getLevelBlock();
        return side.canPassThrough() && !(side instanceof BlockLiquid);
    }
    
    public List<BlockFace> scanValidSpawnFaces() {
        return scanValidSpawnFaces(false);
    }

    public List<BlockFace> scanValidSpawnFaces(boolean preferFront) {
        if (preferFront) {
            Block block = getBlock();
            if (block instanceof BlockBeehive) {
                BlockFace beehiveFace = ((BlockBeehive) block).getBlockFace();
                if (isSpawnFaceValid(beehiveFace)) {
                    return Collections.singletonList(beehiveFace);
                }
            }
        }
        
        List<BlockFace> validFaces = new ArrayList<>(4);
        for (int faceIndex = 0; faceIndex < 4; faceIndex++) {
            BlockFace face = BlockFace.fromHorizontalIndex(faceIndex);
            if (isSpawnFaceValid(face)) {
                validFaces.add(face);
            }
        }
        
        return validFaces;
    }

    public Entity spawnOccupant(Occupant occupant, List<BlockFace> validFaces) {
        if (validFaces != null && validFaces.isEmpty()) {
            return null;
        }
        
        CompoundTag saveData = occupant.saveData.clone();

        ThreadLocalRandom random = ThreadLocalRandom.current();

        Position lookAt;
        Position spawnPosition;
        if (validFaces != null) {
            BlockFace face = validFaces.get(random.nextInt(validFaces.size()));
            spawnPosition = add(
                    face.getXOffset() * 0.25 - face.getZOffset() * 0.5,
                    face.getYOffset() + (face.getYOffset() < 0 ? -0.4 : 0.2),
                    face.getZOffset() * 0.25 - face.getXOffset() * 0.5
            );
    
            saveData.putList(new ListTag<DoubleTag>("Pos")
                    .add(new DoubleTag("0", spawnPosition.x))
                    .add(new DoubleTag("1", spawnPosition.y))
                    .add(new DoubleTag("2", spawnPosition.z))
            );
    
            saveData.putList(new ListTag<DoubleTag>("Motion")
                    .add(new DoubleTag("0", 0))
                    .add(new DoubleTag("1", 0))
                    .add(new DoubleTag("2", 0))
            );
    
            lookAt = getSide(face, 2);
        } else {
            spawnPosition = add(random.nextDouble(), 0.2, random.nextDouble());
            lookAt = spawnPosition.add(random.nextDouble(), 0, random.nextDouble());
        }
    
        double dx = lookAt.getX() - spawnPosition.getX();
        double dz = lookAt.getZ() - spawnPosition.getZ();
        float yaw = 0;

        if (dx != 0) {
            if (dx < 0) {
                yaw = (float) (1.5 * Math.PI);
            } else {
                yaw = (float) (0.5 * Math.PI);
            }
            yaw = yaw - (float) Math.atan(dz / dx);
        } else if (dz < 0) {
            yaw = (float) Math.PI;
        }

        yaw = -yaw * 180f / (float) Math.PI;

        saveData.putList(new ListTag<FloatTag>("Rotation")
                .add(new FloatTag("0", yaw))
                .add(new FloatTag("1", 0))
        );
        
        Entity entity = Entity.createEntity(occupant.actorIdentifier, spawnPosition.getChunk(), saveData);
        if (entity != null) {
            removeOccupant(occupant);
            level.addSound(this, Sound.BLOCK_BEEHIVE_EXIT);
        }

        EntityBee bee = entity instanceof EntityBee? (EntityBee) entity : null;
        
        if (occupant.getHasNectar() && occupant.getTicksLeftToStay() <= 0) {
            if (!isHoneyFull()) {
                setHoneyLevel(getHoneyLevel() + 1);
            }
            if (bee != null) {
                bee.nectarDelivered(this);
            }
        } else {
            if (bee != null) {
                bee.leftBeehive(this);
            }
        }
        
        if (entity != null) {
            entity.spawnToAll();
        }

        return entity;
    }
    
    /** Pure exact witness; actor preparation and source acceptance are separate operations. */
    public ExplosionOccupants captureExplosionOccupants() {
        return new ExplosionOccupants(this);
    }

    public static final class ExplosionOccupants implements AutoCloseable {
        private final BlockEntityBeehive hive;
        private final cn.nukkit.level.Level level;
        private final FullChunk chunk;
        private final Occupant[] original;
        private final CompoundTag[] authored;
        private final CompoundTag tileState;
        private final CompoundTag emptiedState;
        private final double x, y, z;
        private final long id;
        private final java.util.List<EntityBee.PreparedBee> actors = new ArrayList<>();
        private final java.util.List<Occupant> emptied = new ArrayList<>();
        private final boolean angry;
        private boolean prepared, consumed, closed;

        private ExplosionOccupants(BlockEntityBeehive hive) {
            this.hive = hive; this.level = hive.level; this.chunk = hive.chunk;
            this.original = hive.getOccupants(); this.authored = new CompoundTag[original.length];
            for (int i = 0; i < original.length; i++) authored[i] = original[i].saveNBT().copy();
            this.tileState = hive.namedTag.copy();
            this.emptiedState = tileState.copy().putList(new ListTag<CompoundTag>("Occupants"));
            this.x=hive.x; this.y=hive.y; this.z=hive.z; this.id=hive.getId();
            this.angry = level != null && level.getBlock(hive.down()).getId() != BlockID.CAMPFIRE_BLOCK;
        }

        public boolean matches() { return matches(false,false); }
        public boolean matchesAfterNativeRemoval() {
            return prepared && !closed && matches(true,true) && actors.stream().allMatch(EntityBee.PreparedBee::isValid);
        }
        /** Rollback retains exact occupants; validity of unfunded actors is not debit authority. */
        public boolean matchesRetainedForNativeRollback() { return matches(true,false); }
        private boolean matches(boolean afterNativeRemoval,boolean requireAir) {
            if (consumed || level == null || hive.closed || hive.level != level || hive.chunk != chunk
                    || hive.getId()!=id || hive.x!=x || hive.y!=y || hive.z!=z
                    || level.getChunkIfLoaded(hive.getFloorX() >> 4, hive.getFloorZ() >> 4) != chunk
                    || !tileState.equals(hive.namedTag) || hive.occupants.size() != original.length) return false;
            if (afterNativeRemoval) {
                BlockEntity coordinate=level.getBlockEntityIfLoaded(hive);
                BlockEntity chunkEntry=chunk.getBlockEntities().get(id);
                if (level.getBlockEntityById(id)!=hive || requireAir && !level.getBlock(hive).isAir()
                        || !(coordinate==hive && chunkEntry==hive || coordinate==null && chunkEntry==null)) return false;
            } else if (level.getBlockEntity(hive)!=hive) return false;
            for (int i = 0; i < original.length; i++) {
                if (hive.occupants.get(i) != original[i] || !authored[i].equals(original[i].saveNBT())) return false;
            }
            return true;
        }

        public boolean prepare() {
            if (closed || consumed) return false;
            if (prepared) return matches() && actors.stream().allMatch(EntityBee.PreparedBee::isValid);
            if (!matches()) return false;
            try {
                for (Occupant occupant : original) {
                    CompoundTag nbt = occupant.getSaveData();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    Position pos = hive.add(random.nextDouble(), 0.2, random.nextDouble());
                    Position look = pos.add(random.nextDouble(), 0, random.nextDouble());
                    double dx = look.x - pos.x, dz = look.z - pos.z;
                    float yaw = dx == 0 ? (dz < 0 ? (float) Math.PI : 0)
                            : (float) ((dx < 0 ? 1.5 * Math.PI : 0.5 * Math.PI) - Math.atan(dz / dx));
                    nbt.putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("0", pos.x))
                            .add(new DoubleTag("1", pos.y)).add(new DoubleTag("2", pos.z)));
                    nbt.putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("0", -yaw * 180f / (float) Math.PI))
                            .add(new FloatTag("1", 0)));
                    EntityBee.PreparedBee actor = EntityBee.prepareExplosionBee(occupant.getActorIdentifier(), pos.getChunk(), nbt);
                    if (actor == null) { close(); return false; }
                    actors.add(actor);
                }
                prepared = true;
                if (!matches() || !actors.stream().allMatch(EntityBee.PreparedBee::isValid)) { close(); return false; }
                return true;
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (RuntimeException | Error discardFailure) { failure.addSuppressed(discardFailure); }
                return false;
            }
        }

        /** No callbacks between exact complete debit and assignment-only sibling releases. */
        public Throwable clearAccepted() { return clearAccepted(false); }
        public Throwable clearAfterNativeRemoval() { return clearAccepted(true); }
        /** Assignment-only sibling acceptance runs before any Bee publication callback. */
        public Throwable clearAfterNativeRemoval(Runnable acceptSiblings) {
            return clearAccepted(true, java.util.Objects.requireNonNull(acceptSiblings));
        }
        private Throwable clearAccepted(boolean afterNativeRemoval) {
            return clearAccepted(afterNativeRemoval, () -> {});
        }
        private Throwable clearAccepted(boolean afterNativeRemoval, Runnable acceptSiblings) {
            if (!prepared || closed || !(afterNativeRemoval ? matchesAfterNativeRemoval() : matches())
                    || !actors.stream().allMatch(EntityBee.PreparedBee::isValid))
                throw new IllegalStateException("Prepared explosion hive occupants changed before acceptance");
            hive.occupants = emptied;
            hive.namedTag = emptiedState;
            consumed = true;
            for (EntityBee.PreparedBee actor : actors) actor.release();
            acceptSiblings.run();
            Throwable failure = null;
            for (EntityBee.PreparedBee actor : actors) {
                try { actor.notifyPublished(angry); }
                catch (RuntimeException | Error notification) {
                    if (failure == null) failure = notification;
                    else if (failure != notification) failure.addSuppressed(notification);
                }
            }
            return failure;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            Throwable failure = null;
            for (EntityBee.PreparedBee actor : actors) {
                try { actor.close(); }
                catch (RuntimeException | Error discarded) {
                    if (failure == null) failure = discarded;
                    else if (failure != discarded) failure.addSuppressed(discarded);
                }
            }
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
        }
    }

    @Override
    public void onBreak() {
        if (!isEmpty()) {
            for (Occupant occupant : getOccupants()) {
                Entity entity = spawnOccupant(occupant, null);
                if (level == null || level.getBlock(down()).getId() != BlockID.CAMPFIRE_BLOCK) {
                    if (entity instanceof EntityBee) {
                        ((EntityBee) entity).setAngry(true);
                    }
                }
            }
        }
    }
    
    public void angerBees(Player player) {
        if (!isEmpty()) {
            List<BlockFace> validFaces = scanValidSpawnFaces();
            if (isSpawnFaceValid(BlockFace.UP)) {
                validFaces.add(BlockFace.UP);
            }
            if (isSpawnFaceValid(BlockFace.DOWN)) {
                validFaces.add(BlockFace.DOWN);
            }
            for (Occupant occupant : getOccupants()) {
                Entity entity = spawnOccupant(occupant, validFaces);
                if (entity instanceof EntityBee) {
                    EntityBee bee = (EntityBee) entity;
                    bee.setAngry(true);
                    bee.setTarget(player, true);
                }
            }
        }
    }
    
    @Override
    public boolean onUpdate() {
        if (this.closed || isEmpty()) {
            return false;
        }

        List<BlockFace> validSpawnFaces = null;

        // getOccupants will avoid ConcurrentModificationException if plugins changes the contents while iterating
        for (Occupant occupant: getOccupants()) {
            if (--occupant.ticksLeftToStay <= 0) {
                if (validSpawnFaces == null) {
                    validSpawnFaces = scanValidSpawnFaces(true);
                }

                if (spawnOccupant(occupant, validSpawnFaces) == null) {
                    occupant.ticksLeftToStay = 600;
                }
            } else if (!occupant.isMuted() && ThreadLocalRandom.current().nextDouble() < 0.005) {
                level.addSound(add(0.5, 0, 0.5), occupant.workSound, 1f, occupant.workSoundPitch);
            }
        }

        return true;
    }
    
    @Override
    public boolean isBlockEntityValid() {
        int id = this.getBlock().getId();
        return id == Block.BEEHIVE || id == Block.BEE_NEST;
    }

    public static final class Occupant implements Cloneable {

        public static final Occupant[] EMPTY_ARRAY = new Occupant[0];
        
        private int ticksLeftToStay;
        private String actorIdentifier;
        private CompoundTag saveData;
        private Sound workSound = Sound.BLOCK_BEEHIVE_WORK;
        private float workSoundPitch = 1;
        private boolean hasNectar;
        private boolean muted;

        public Occupant(int ticksLeftToStay, String actorIdentifier, CompoundTag saveData) {
            this.ticksLeftToStay = ticksLeftToStay;
            this.actorIdentifier = actorIdentifier;
            this.saveData = saveData;
        }

        private Occupant(CompoundTag saved) {
            this.ticksLeftToStay = saved.getInt("TicksLeftToStay");
            this.actorIdentifier = saved.getString("ActorIdentifier");
            this.saveData = saved.getCompound("SaveData").clone();
            if (saved.contains("WorkSound")) {
                try {
                    this.workSound = Sound.valueOf(saved.getString("WorkSound"));
                } catch (IllegalArgumentException ignored) {

                }
            }
            if (saved.contains("WorkSoundPitch")) {
                this.workSoundPitch = saved.getFloat("WorkSoundPitch");
            }
            this.hasNectar = saved.getBoolean("HasNectar");
            this.muted = saved.getBoolean("Muted");
        }

        public CompoundTag saveNBT() {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("ActorIdentifier", actorIdentifier)
                    .putInt("TicksLeftToStay", ticksLeftToStay)
                    .putCompound("SaveData", saveData)
                    .putString("WorkSound", workSound.name())
                    .putFloat("WorkSoundPitch", workSoundPitch)
                    .putBoolean("HasNectar", hasNectar)
                    .putBoolean("Muted", muted);
            return compoundTag;
        }

        public boolean getHasNectar() {
            return hasNectar;
        }

        public void setHasNectar(boolean hasNectar) {
            this.hasNectar = hasNectar;
        }

        public int getTicksLeftToStay() {
            return ticksLeftToStay;
        }

        public void setTicksLeftToStay(int ticksLeftToStay) {
            this.ticksLeftToStay = ticksLeftToStay;
        }

        public String getActorIdentifier() {
            return actorIdentifier;
        }

        public void setActorIdentifier(String actorIdentifier) {
            this.actorIdentifier = actorIdentifier;
        }

        public CompoundTag getSaveData() {
            return saveData.clone();
        }

        public void setSaveData(CompoundTag saveData) {
            this.saveData = saveData.clone();
        }

        public Sound getWorkSound() {
            return workSound;
        }

        public void setWorkSound(Sound workSound) {
            this.workSound = workSound;
        }

        public float getWorkSoundPitch() {
            return workSoundPitch;
        }

        public void setWorkSoundPitch(float workSoundPitch) {
            this.workSoundPitch = workSoundPitch;
        }

        public boolean isMuted() {
            return muted;
        }

        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        @Override
        public String toString() {
            return "Occupant{" +
                    "ticksLeftToStay=" + ticksLeftToStay +
                    ", actorIdentifier='" + actorIdentifier + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Occupant occupant = (Occupant) o;
            return ticksLeftToStay == occupant.ticksLeftToStay &&
                    Objects.equals(actorIdentifier, occupant.actorIdentifier) &&
                    Objects.equals(saveData, occupant.saveData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ticksLeftToStay, actorIdentifier, saveData);
        }

        @Override
        protected Occupant clone() {
            try {
                Occupant occupant = (Occupant) super.clone();
                occupant.saveData = this.saveData.clone();
                return occupant;
            } catch (CloneNotSupportedException e) {
                throw new InternalError("Unexpected exception", e);
            }
        }
    }
}
