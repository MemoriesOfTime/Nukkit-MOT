package cn.nukkit.entity.item;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLiquid;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityPickupClaim;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.ItemBurnEvent;
import cn.nukkit.event.entity.ItemDespawnEvent;
import cn.nukkit.event.entity.ItemSpawnEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.AddItemEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.EntityEventPacket;
import lombok.Getter;
import lombok.Setter;

/**
 * @author MagicDroidX
 */
public class EntityItem extends Entity {

    public static final int NETWORK_ID = 64;

    @Setter
    @Getter
    protected String owner;

    @Setter
    @Getter
    protected String thrower;

    @Getter
    protected Item item;

    @Setter
    @Getter
    protected int pickupDelay;

    protected boolean floatsInLava;

    /**
     * Whether this entity may join a neighbouring equal stack on the ground.
     *
     * <p>Merging keeps only one of the two entities, so any server data written on the absorbed
     * entity (its tags, not the item's) silently disappears. An entity that carries such data
     * opts out through the {@code Mergeable} NBT flag, the same key PowerNukkitX uses. Both
     * sides are checked: a mergeable neighbour can neither absorb nor be absorbed by an entity
     * that opted out.
     */
    // initEntity runs from the super constructor; do not overwrite its NBT or spawn-listener decision.
    protected boolean mergeItems;

    public EntityItem(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    private EntityItem(FullChunk chunk, CompoundTag nbt, boolean prepared) {
        super(chunk, nbt, prepared);
    }

    /** A capability kept by the transaction, never exposed by the entity shown to spawn listeners. */
    public static final class PreparedDrop {
        private final EntityItem entity;
        private final FullChunk chunk;
        private final cn.nukkit.level.Level level;
        private final double x, y, z, motionX, motionY, motionZ;
        private final Item approvedItem;

        private PreparedDrop(EntityItem entity, FullChunk chunk, CompoundTag authored, Item expected) {
            this.entity = entity;
            this.chunk = chunk;
            this.level = chunk.getProvider().getLevel();
            var pos = authored.getList("Pos", cn.nukkit.nbt.tag.DoubleTag.class);
            var motion = authored.getList("Motion", cn.nukkit.nbt.tag.DoubleTag.class);
            this.x = pos.get(0).data; this.y = pos.get(1).data; this.z = pos.get(2).data;
            this.motionX = motion.get(0).data; this.motionY = motion.get(1).data; this.motionZ = motion.get(2).data;
            // Spawn listeners may sanitize server metadata, but cannot change the physical quantity or variant.
            Item actual = entity.item;
            this.approvedItem = actual == null ? Item.get(Item.AIR) : actual.clone();
            if (actual == null || actual.getId() != expected.getId()
                    || !actual.getNamespaceId().equals(expected.getNamespaceId())
                    || actual.getDamage() != expected.getDamage() || actual.getCount() != expected.getCount()) {
                entity.deferredItemInvalid = true;
            }
        }

        public boolean isValid() {
            return this.entity.isDeferredPublicationIntact() && !this.entity.deferredItemInvalid
                    && this.entity.isAlive() && this.entity.chunk == this.chunk && this.entity.level == this.level
                    && this.chunk.getEntities().get(this.entity.getId()) == this.entity
                    && this.level.getEntity(this.entity.getId()) == this.entity
                    && this.entity.x == this.x && this.entity.y == this.y && this.entity.z == this.z
                    && this.entity.motionX == this.motionX && this.entity.motionY == this.motionY && this.entity.motionZ == this.motionZ
                    && this.entity.item != null && this.entity.item.equalsExact(this.approvedItem);
        }

        /** Preconditions were checked by the coordinator; publication itself is assignment-only. */
        public void release() { this.entity.releaseDeferredPublication(); }
        public void discard() { this.entity.discardDeferredPublication(); }
        public void notifyPublished() {
            this.entity.scheduleUpdate();
            this.entity.spawnToAll();
        }
    }

    private boolean deferredItemInvalid;

    public static PreparedDrop prepareDrop(FullChunk chunk, CompoundTag nbt, Item expected) {
        // Keep a detached geometry snapshot: the constructor owns and exposes its own NBT to listeners.
        CompoundTag authored = nbt.clone();
        EntityItem entity = new EntityItem(chunk, nbt, true);
        try {
            PreparedDrop ticket = new PreparedDrop(entity, chunk, authored, expected);
            if (!ticket.isValid()) {
                ticket.discard();
                return null;
            }
            return ticket;
        } catch (RuntimeException | Error failure) {
            try { entity.discardDeferredPublication(); } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.25f;
    }

    @Override
    public float getLength() {
        return 0.25f;
    }

    @Override
    public float getHeight() {
        return 0.25f;
    }

    @Override
    public float getGravity() {
        return 0.04f;
    }

    @Override
    public float getDrag() {
        return 0.02f;
    }

    @Override
    protected float getBaseOffset() {
        return 0.125f;
    }

    @Override
    public boolean canCollide() {
        return false;
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.setMaxHealth(5);
        this.setHealth(this.namedTag.getShort("Health"));

        if (this.namedTag.contains("Age")) {
            this.age = this.namedTag.getShort("Age");
        }

        if (this.namedTag.contains("PickupDelay")) {
            this.pickupDelay = this.namedTag.getShort("PickupDelay");
        }

        if (this.namedTag.contains("Owner")) {
            this.owner = this.namedTag.getString("Owner");
        }

        if (this.namedTag.contains("Thrower")) {
            this.thrower = this.namedTag.getString("Thrower");
        }

        this.mergeItems = mergeableFromNbt(this.namedTag);

        if (!this.namedTag.contains("Item")) {
            this.close();
            return;
        }

        this.item = NBTIO.getItemHelper(this.namedTag.getCompound("Item"));

        int id = this.item.getId();
        if (id >= Item.NETHERITE_INGOT && id <= Item.NETHERITE_SCRAP) {
            this.fireProof = true; // Netherite items are fireproof
            this.floatsInLava = true;
        }

        this.server.getPluginManager().callEvent(new ItemSpawnEvent(this));
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (isPublicationDeferred()) return false;
        DamageCause cause = source.getCause();
        if ((cause == DamageCause.VOID || cause == DamageCause.CONTACT || cause == DamageCause.FIRE_TICK
                || (cause == DamageCause.ENTITY_EXPLOSION || cause == DamageCause.BLOCK_EXPLOSION) && !this.isInsideOfWater()
                && (this.item == null || this.item.getId() != Item.NETHER_STAR)) && super.attack(source)) {
            if (this.item == null || this.isAlive()) {
                return true;
            }
            int id = this.item.getId();
            if (id != Item.SHULKER_BOX && id != Item.UNDYED_SHULKER_BOX) {
                return true;
            }
            CompoundTag nbt = this.item.getNamedTag();
            if (nbt == null) {
                return true;
            }
            ListTag<CompoundTag> items = nbt.getList("Items", CompoundTag.class);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.get(i);
                Item item = NBTIO.getItemHelper(itemTag);
                if (item.isNull()) {
                    continue;
                }
                this.level.dropItem(this, item);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (isPublicationDeferred()) return false;
        if (this.closed) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }

        this.lastUpdate = currentTick;

        if (!this.fireProof && this.isInsideOfFire()) {
            this.server.getPluginManager().callEvent(new ItemBurnEvent(this));
            this.close();
            return true;
        }

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.isAlive()) {
            //Entity[] e = null;

            if (this.pickupDelay > 0 && this.pickupDelay < 32767) {
                this.pickupDelay -= tickDiff;
                if (this.pickupDelay < 0) {
                    this.pickupDelay = 0;
                }
            }/* else {
                e = this.getLevel().getNearbyEntities(getBoundingBox().grow(1, 1, 1), this, false);
                for (Entity entity : e) {
                    if (entity.isPlayer) {
                        if (((Player) entity).pickupEntity(this, true)) {
                            if (this.timing != null) this.timing.stopTiming();
                            return true;
                        }
                    }
                }
            }*/

            if (this.age > 6000) {
                ItemDespawnEvent ev = new ItemDespawnEvent(this);
                this.server.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    this.age = 0;
                } else {
                    this.close();
                    return true;
                }
            }

            if (this.mergeItems && this.age % 200 == 0 && this.onGround && this.item != null) {
                if (this.item.getCount() < this.item.getMaxStackSize()) {
                    //if (e == null) {
                    Entity[] e = this.getLevel().getNearbyEntities(getBoundingBox().grow(1, 1, 1), this, false);
                    //}

                    for (Entity entity : e) {
                        if (entity instanceof EntityItem) {
                            if (entity.closed || !entity.isAlive()) {
                                continue;
                            }
                            if (!this.mergeableWith((EntityItem) entity)) {
                                continue;
                            }
                            Item closeItem = ((EntityItem) entity).item;
                            if (!closeItem.equals(item, true, true)) {
                                continue;
                            }
                            if (!entity.isOnGround()) {
                                continue;
                            }
                            // Native despawn and merge-packet callbacks must not consume either participant.
                            try (EntityPickupClaim.Lease receiver = EntityPickupClaim.tryClaim(this);
                                 EntityPickupClaim.Lease donor = EntityPickupClaim.tryClaim(entity)) {
                                if (receiver == null || donor == null) continue;
                                int newAmount = this.item.getCount() + closeItem.getCount();
                                if (newAmount > this.item.getMaxStackSize()) {
                                    continue;
                                }
                                this.age = Math.min(this.age, entity.age);
                                closeItem.setCount(0);
                                this.item.setCount(newAmount);
                                entity.close();
                                EntityEventPacket packet = new EntityEventPacket();
                                packet.eid = getId();
                                packet.data = newAmount;
                                packet.event = EntityEventPacket.MERGE_ITEMS;
                                Server.broadcastPacket(this.getViewers().values(), packet);
                            }
                        }
                    }
                }
            }

            this.updateLiquidMovement();

            if (this.checkObstruction(this.x, this.y, this.z)) {
                hasUpdate = true;
            }

            this.move(this.motionX, this.motionY, this.motionZ);

            double friction = 1 - this.getDrag();

            Block block = this.getLevel().getBlock(this.getFloorX(), (int) Math.floor(this.y - 1), this.getFloorZ());
            if ((this.onGround || block instanceof BlockLiquid)
                    && (Math.abs(this.motionX) > 0.00001 || Math.abs(this.motionZ) > 0.00001)) {
                double frictionFactor;
                if (block instanceof BlockLiquid) {
                    frictionFactor = 0.8;
                } else {
                    frictionFactor = block.getFrictionFactor();
                }
                friction *= frictionFactor;
            }

            this.motionX *= friction;
            this.motionY *= 1 - this.getDrag();
            this.motionZ *= friction;

            if (this.onGround) {
                this.motionY *= -0.5;
            }

            this.updateMovement();
        }

        return hasUpdate || !this.onGround || Math.abs(this.motionX) > 0.00001 || Math.abs(this.motionY) > 0.00001 || Math.abs(this.motionZ) > 0.00001;
    }

    private void updateLiquidMovement() {
        Block block = level.getBlock((int) x, (int) boundingBox.getMaxY(), (int) z);
        if (block.isLiquidSource()) {
            //item is fully in liquid
            motionY -= getGravity() * -0.015;
            return;
        }

        Block floor = getLevelBlock();
        if (floor.isLiquidSource() || (floor = level.getBlock(floor, 1)).isLiquidSource()) {
            double height = floor.y + 1 - ((BlockLiquid) floor).getFluidHeightPercent() - 0.1111111;
            if (this.getEyeY() < height) {
                //item is going up in liquid, don't let it go back down too fast
                motionY = getGravity() - 0.06;
                return;
            }
        }

        //item is not in liquid
        motionY -= getGravity();
    }

    @Override
    public void saveNBT() {
        if (isPublicationDeferred()) return;
        super.saveNBT();
        if (this.item != null) { // Yes, a item can be null... I don't know what causes this, but it can happen.
            this.namedTag.putCompound("Item", NBTIO.putItemHelper(this.item, -1));
            this.namedTag.putShort("Health", (int) this.getHealth());
            this.namedTag.putShort("Age", this.age);
            this.namedTag.putShort("PickupDelay", this.pickupDelay);
            if (this.owner != null) {
                this.namedTag.putString("Owner", this.owner);
            }

            if (this.thrower != null) {
                this.namedTag.putString("Thrower", this.thrower);
            }
            if (this.mergeItems) {
                this.namedTag.remove("Mergeable");
            } else {
                this.namedTag.putBoolean("Mergeable", false);
            }
        }
    }

    /** True unless the entity opted out of merging; see {@link #mergeItems}. */
    public boolean isMergeable() {
        return !isPublicationDeferred() && this.mergeItems;
    }

    /**
     * Lets this entity join or absorb equal neighbouring stacks, or forbids it. The choice is
     * saved with the entity and survives chunk unloads and restarts.
     */
    public void setMergeable(boolean mergeable) {
        this.mergeItems = mergeable;
        if (this.namedTag != null) {
            if (mergeable) {
                this.namedTag.remove("Mergeable");
            } else {
                this.namedTag.putBoolean("Mergeable", false);
            }
        }
    }

    /** Both entities must allow merging; the item equality check stays with the caller. */
    protected boolean mergeableWith(EntityItem other) {
        return !this.isPublicationDeferred() && this.mergeItems && other != null
                && !other.isPublicationDeferred() && other.mergeItems
                && !EntityPickupClaim.isClaimed(this) && !EntityPickupClaim.isClaimed(other);
    }

    static boolean mergeableFromNbt(CompoundTag tag) {
        return tag == null || !tag.contains("Mergeable") || tag.getBoolean("Mergeable");
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : (this.item.hasCustomName() ? this.item.getCustomName() : this.item.getName());
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public DataPacket createAddEntityPacket() {
        AddItemEntityPacket addEntity = new AddItemEntityPacket();
        addEntity.entityUniqueId = this.getId();
        addEntity.entityRuntimeId = this.getId();
        addEntity.x = (float) this.x;
        addEntity.y = (float) this.y + this.getBaseOffset();
        addEntity.z = (float) this.z;
        addEntity.speedX = (float) this.motionX;
        addEntity.speedY = (float) this.motionY;
        addEntity.speedZ = (float) this.motionZ;
        addEntity.metadata = this.dataProperties.clone();
        addEntity.item = this.item;
        return addEntity;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (isPublicationDeferred()) return false;
        this.collisionBlocks = null;
        this.justCreated = false;

        if (!this.isAlive()) {
            this.despawnFromAll();
            this.close();
            return false;
        }

        boolean hasUpdate = false;

        this.checkBlockCollision();

        if (this.y <= (level.getMinBlockY() - 16) && this.isAlive()) {
            this.attack(new EntityDamageEvent(this, DamageCause.VOID, 10));
            hasUpdate = true;
        }

        if (this.fireTicks > 0) {
            if (this.fireProof) {
                this.fireTicks -= tickDiff << 2;
                if (this.fireTicks < 0) {
                    this.fireTicks = 0;
                }
            } else {
                if ((this.fireTicks % 20) == 0 || tickDiff > 20) {
                    this.attack(new EntityDamageEvent(this, DamageCause.FIRE_TICK, 1));
                }
                this.fireTicks -= tickDiff;
            }
            if (this.fireTicks <= 0) {
                this.extinguish();
            } else if (!this.fireProof) {
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_ONFIRE, true);
                hasUpdate = true;
            }
        }

        if (this.noDamageTicks > 0) {
            this.noDamageTicks -= tickDiff;
            if (this.noDamageTicks < 0) {
                this.noDamageTicks = 0;
            }
        }

        this.age += tickDiff;
        return hasUpdate;
    }
}
