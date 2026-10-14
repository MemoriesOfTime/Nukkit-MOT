package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityBeehive;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityArthropod;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.mob.EntityFlyingMob;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Utils;

import java.util.HashMap;

public class EntityBee extends EntityFlyingMob implements EntityArthropod { // A mob because it needs to have an attack behavior

    public static final int NETWORK_ID = 122;

    private boolean angry;

    private CompoundTag preparedExplosionTag;
    private float preparedExplosionMaxHealth;

    public EntityBee(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, false);
    }

    private EntityBee(FullChunk chunk, CompoundTag nbt, boolean prepared) {
        super(chunk, nbt, prepared);
    }

    /** All actors remain sealed until the exact occupants have been debited from their hive. */
    public static PreparedBee prepareExplosionBee(String identifier, FullChunk chunk, CompoundTag nbt) {
        if (!resolvesToImplementation(identifier, EntityBee.class) || chunk == null
                || chunk.getProvider() == null || chunk.getProvider().getLevel() == null) return null;
        EntityBee actor = new EntityBee(chunk, nbt.copy(), true);
        PreparedBee ticket = new PreparedBee(actor, chunk);
        if (!ticket.isValid()) { ticket.close(); return null; }
        return ticket;
    }

    public static final class PreparedBee implements AutoCloseable {
        private final EntityBee actor;
        private final FullChunk chunk;
        private final CompoundTag authored;
        private final float maxHealth;
        private boolean released, discarded;

        private PreparedBee(EntityBee actor, FullChunk chunk) {
            this.actor = actor; this.chunk = chunk;
            this.authored = actor.preparedExplosionTag;
            this.maxHealth = actor.preparedExplosionMaxHealth;
        }

        public boolean isValid() {
            if (released || discarded || authored == null || !actor.isDeferredPublicationIntact()
                    || !actor.isAlive() || actor.chunk != chunk || actor.level != chunk.getProvider().getLevel()
                    || actor.level.getChunkIfLoaded(chunk.getX(), chunk.getZ()) != chunk
                    || chunk.getEntities().get(actor.getId()) != actor || actor.level.getEntity(actor.getId()) != actor
                    || !actor.saveWithChunk
                    || actor.getMaxHealth() != maxHealth || actor.getHealth() != maxHealth
                    || !authored.equals(actor.namedTag)) return false;
            var pos = authored.getList("Pos", cn.nukkit.nbt.tag.DoubleTag.class);
            var motion = authored.getList("Motion", cn.nukkit.nbt.tag.DoubleTag.class);
            var rotation = authored.getList("Rotation", cn.nukkit.nbt.tag.FloatTag.class);
            return pos.size() == 3 && motion.size() == 3 && rotation.size() == 2
                    && actor.x == pos.get(0).data && actor.y == pos.get(1).data && actor.z == pos.get(2).data
                    && actor.motionX == motion.get(0).data && actor.motionY == motion.get(1).data && actor.motionZ == motion.get(2).data
                    && actor.yaw == rotation.get(0).data && actor.pitch == rotation.get(1).data;
        }

        /** Assignment only: every sibling is validated and debited before the first release. */
        public void release() {
            if (!released && !discarded) { actor.releaseDeferredPublication(); released = true; }
        }

        public void notifyPublished(boolean angry) {
            if (released && !discarded) {
                actor.setAngry(angry); actor.scheduleUpdate(); actor.spawnToAll();
            }
        }

        @Override public void close() {
            if (!released && !discarded) { discarded = true; actor.discardDeferredPublication(); }
        }
    }

    /** Unfunded occupants cannot emit a Living death event, loot or experience. */
    @Override public void kill() {
        if (isPublicationDeferred()) {
            this.health=0;
            discardDeferredPublication();
            return;
        }
        super.kill();
    }

    @Override public boolean onUpdate(int tick) {
        return isPublicationDeferred() || super.onUpdate(tick);
    }

    @Override
    public int getKillExperience() {
        return Utils.rand(1, 3);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.275f;
        }
        return 0.55f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.25f;
        }
        return 0.5f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(10);

        super.initEntity();
        this.setDamage(new int[]{0, 2, 2, 3});
        if (isPublicationDeferred()) {
            this.preparedExplosionTag = this.namedTag.copy();
            this.preparedExplosionMaxHealth = this.getMaxHealth();
        }
    }

    @Override
    public double getSpeed() {
        return 1.2;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && this.distanceSquared(player) < 1.3) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());
            if (player instanceof Player) {
                float points = 0;
                for (Item i : ((Player) player).getInventory().getArmorContents()) {
                    points += this.getArmorPoints(i.getId());
                }
                damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                        (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(
                                damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
            }
            if (player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage))) {
                if (this.getServer().getDifficulty() == 2) {
                    player.addEffect(Effect.getEffect(Effect.POISON).setDuration(200), EntityPotionEffectEvent.Cause.ATTACK);
                } else if (this.getServer().getDifficulty() == 3) {
                    player.addEffect(Effect.getEffect(Effect.POISON).setDuration(360), EntityPotionEffectEvent.Cause.ATTACK);
                }
            }
        }
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        return this.isAngry() && super.targetOption(creature, distance);
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        if (super.attack(ev)) {
            if (ev instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) ev).getDamager() instanceof Player) {
                this.setAngry(true);
            }
            return true;
        }

        return false;
    }

    public boolean isAngry() {
        return this.angry;
    }

    public void setAngry(boolean angry) {
        this.angry = angry;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ANGRY, angry);
    }

    public boolean getHasNectar() {
        return false; //TODO
    }

    public void setHasNectar(boolean hasNectar) {
        //TODO
    }

    public void nectarDelivered(BlockEntityBeehive blockEntityBeehive) {
        //TODO
    }

    public void leftBeehive(BlockEntityBeehive blockEntityBeehive) {
        //TODO
    }

    @Override
    protected boolean canBeDamagedBySweetBerryBush() {
        return false;
    }
}
