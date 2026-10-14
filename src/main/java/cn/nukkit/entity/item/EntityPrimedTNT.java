package cn.nukkit.entity.item;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

/**
 * @author MagicDroidX
 */
public class EntityPrimedTNT extends Entity implements EntityExplosive {

    public static final int NETWORK_ID = 65;

    @Override
    public float getWidth() {
        return 0.98f;
    }

    @Override
    public float getLength() {
        return 0.98f;
    }

    @Override
    public float getHeight() {
        return 0.98f;
    }

    @Override
    protected float getGravity() {
        return 0.04f;
    }

    @Override
    protected float getDrag() {
        return 0.02f;
    }

    @Override
    protected float getBaseOffset() {
        return 0.49f;
    }

    protected int fuse;

    protected Entity source;

    public EntityPrimedTNT(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityPrimedTNT(FullChunk chunk, CompoundTag nbt, Entity source) {
        super(chunk, nbt);
        this.source = source;
    }

    private EntityPrimedTNT(FullChunk chunk, CompoundTag nbt, Entity source, boolean prepared) {
        super(chunk, nbt, prepared);
        this.source=source;
    }
    /** A sealed, non-ticking actor until its exact TNT source cell has been accepted. */
    public static PreparedPrime preparePrime(FullChunk chunk, CompoundTag nbt, Entity source) {
        if(chunk==null || chunk.getProvider()==null || chunk.getProvider().getLevel()==null)return null;
        EntityPrimedTNT entity=new EntityPrimedTNT(chunk,nbt.copy(),source,true);
        PreparedPrime ticket=new PreparedPrime(entity,chunk,nbt.copy(),source);
        if(!ticket.isValid()){ticket.discard();return null;}
        return ticket;
    }
    public static final class PreparedPrime {
        private final EntityPrimedTNT entity;
        private final FullChunk chunk;
        private final CompoundTag authored;
        private final Entity source;
        private boolean accepted,discarded;
        private PreparedPrime(EntityPrimedTNT entity,FullChunk chunk,CompoundTag authored,Entity source){this.entity=entity;this.chunk=chunk;this.authored=authored;this.source=source;}
        public EntityPrimedTNT getEntity(){return entity;}
        public boolean isValid(){
            var pos=authored.getList("Pos",cn.nukkit.nbt.tag.DoubleTag.class);
            var motion=authored.getList("Motion",cn.nukkit.nbt.tag.DoubleTag.class);
            return !accepted && !discarded && entity.getClass()==EntityPrimedTNT.class
                    && entity.isDeferredPublicationIntact() && entity.isAlive() && entity.chunk==chunk
                    && entity.level==chunk.getProvider().getLevel() && entity.source==source
                    && entity.level.getChunkIfLoaded(chunk.getX(),chunk.getZ())==chunk
                    && pos.size()==3 && motion.size()==3 && entity.x==pos.get(0).data && entity.y==pos.get(1).data && entity.z==pos.get(2).data
                    && entity.motionX==motion.get(0).data && entity.motionY==motion.get(1).data && entity.motionZ==motion.get(2).data
                    && entity.fuse==authored.getByte("Fuse") && entity.yaw==0 && entity.pitch==0;
        }
        /** Assignment-only release; no plugin or world notifications. */
        public void release(){if(!accepted && !discarded){entity.releaseDeferredPublication();accepted=true;}}
        public void discard(){if(!accepted && !discarded){discarded=true;entity.discardDeferredPublication();}}
        public void notifyPublished(){if(accepted && !discarded){entity.scheduleUpdate();entity.spawnToAll();}}
    }


    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return source.getCause() == DamageCause.VOID && super.attack(source);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        if (namedTag.contains("Fuse")) {
            fuse = namedTag.getByte("Fuse");
        } else {
            fuse = 80;
        }

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_IGNITED, true);
        this.setDataProperty(new IntEntityData(DATA_FUSE_LENGTH, fuse));

        if (!isPublicationDeferred()) this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_FIZZ);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        namedTag.putByte("Fuse", fuse);
    }

    @Override
    public boolean onUpdate(int currentTick) {

        if (closed || isPublicationDeferred()) {
            return false;
        }

        int tickDiff = currentTick - lastUpdate;

        if (tickDiff <= 0 && !justCreated) {
            return true;
        }

        if (fuse % 5 == 0) {
            this.setDataProperty(new IntEntityData(DATA_FUSE_LENGTH, fuse));
        }

        lastUpdate = currentTick;

        boolean hasUpdate = entityBaseTick(tickDiff);

        if (isAlive()) {

            if (!isOnGround()) {
                motionY -= getGravity();
            }

            move(motionX, motionY, motionZ);

            float friction = 1 - getDrag();

            motionX *= friction;
            motionY *= friction;
            motionZ *= friction;

            updateMovement();

            if (onGround) {
                motionY *= -0.5;
                motionX *= 0.7;
                motionZ *= 0.7;
            }

            fuse -= tickDiff;

            if (fuse <= 0) {
                if (this.level.getGameRules().getBoolean(GameRule.TNT_EXPLODES)) {
                    this.explode();
                }
                this.close();
                return false;
            }
        }

        return hasUpdate || fuse >= 0 || Math.abs(motionX) > 0.00001 || Math.abs(motionY) > 0.00001 || Math.abs(motionZ) > 0.00001;
    }

    @Override
    public void explode() {
        EntityExplosionPrimeEvent event = new EntityExplosionPrimeEvent(this, 4);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        Explosion explosion = new Explosion(this, event.getForce(), this);
        if (event.isBlockBreaking()) {
            explosion.explodeA();
        }
        explosion.explodeB();
    }

    public Entity getSource() {
        return source;
    }
}
