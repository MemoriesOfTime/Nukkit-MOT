package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityPrimedTNT;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntitySmallFireBall;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.level.sound.TNTPrimeSound;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationType;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BlockColor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created on 2015/12/8 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockTNT extends BlockSolid {

    @Override
    public String getName() {
        return "TNT";
    }

    @Override
    public int getId() {
        return TNT;
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
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public int getBurnChance() {
        return 15;
    }

    @Override
    public int getBurnAbility() {
        return 100;
    }

    public void prime() {
        this.prime(80);
    }

    public void prime(int fuse) {
        prime(fuse, null);
    }

    private static final ThreadLocal<java.util.Set<PrimeCell>> PRIMING=ThreadLocal.withInitial(java.util.HashSet::new);
    private record PrimeCell(Level level,int x,int y,int z,int layer){}
    public void prime(int fuse, Entity source) { primeAccepted(fuse,source,()->{}); }
    /** Keep the source intact until a sealed native actor and raw cell removal both succeed. */
    public boolean primeAccepted(int fuse,Entity source,Runnable acceptedCallback) { return primeAccepted(fuse,source,()->{},acceptedCallback); }
    public boolean primeAccepted(int fuse,Entity source,Runnable validateAccepted,Runnable acceptedCallback) {
        PrimeCell key=new PrimeCell(level,getFloorX(),getFloorY(),getFloorZ(),layer);
        var active=PRIMING.get();if(active.size()>=64 || !active.add(key))return false;
        try{return preparePrimeAccepted(fuse,source,validateAccepted,acceptedCallback);}
        finally{active.remove(key);if(active.isEmpty())PRIMING.remove();}
    }
    private boolean preparePrimeAccepted(int fuse,Entity source,Runnable validateAccepted,Runnable acceptedCallback) {
        if(layer!=0 || level.getBlock(this,layer).getFullId()!=getFullId() || level.getBlockEntity(this)!=null)return false;
        double mot = (ThreadLocalRandom.current().nextFloat() * 2 - 1) * 6.283185307179586;
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", this.x + 0.5))
                        .add(new DoubleTag("", this.y))
                        .add(new DoubleTag("", this.z + 0.5)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", -Math.sin(mot) * 0.02))
                        .add(new DoubleTag("", 0.2))
                        .add(new DoubleTag("", -Math.cos(mot) * 0.02)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)))
                .putShort("Fuse", fuse);
        EntityPrimedTNT.PreparedPrime ticket=EntityPrimedTNT.preparePrime(level.getChunk(getFloorX()>>4,getFloorZ()>>4),nbt,source);
        if(ticket==null)return false;
        boolean[] accepted={false};Throwable failure=null;
        try {
            if(!ticket.isValid() || level.getBlock(this,layer).getFullId()!=getFullId() || level.getBlockEntity(this)!=null)return false;
            try {
                level.setBlock(this,Block.get(BlockID.AIR),true,true,()->{
                    if(!ticket.isValid() || !level.getBlock(this,layer).isAir() || level.getBlockEntity(this)!=null)throw new IllegalStateException("Prepared TNT source changed before publication");
                    validateAccepted.run();accepted[0]=true;
                    Throwable acceptedFailure=null;
                    try{level.getServer().getPluginManager().callEvent(new cn.nukkit.event.entity.EntityPrimeAcceptedEvent(ticket.getEntity(),this));}
                    catch(RuntimeException|Error error){acceptedFailure=error;}
                    try{acceptedCallback.run();}
                    catch(RuntimeException|Error error){if(acceptedFailure==null)acceptedFailure=error;else if(acceptedFailure!=error)acceptedFailure.addSuppressed(error);}
                    finally{ticket.release();}
                    if(acceptedFailure instanceof Error error)throw error;
                    if(acceptedFailure instanceof RuntimeException error)throw error;
                });
            }catch(RuntimeException|Error error){failure=error;}
            if(!accepted[0]) {
                if(level.getBlock(this,layer).isAir())level.setBlock(this,clone(),false,false);
                if(failure instanceof Error error)throw error;if(failure instanceof RuntimeException error)throw error;
                return false;
            }
            try {
                ticket.notifyPublished();
                level.addLevelSoundEvent(this,cn.nukkit.network.protocol.LevelSoundEventPacket.SOUND_FIZZ);
                level.addSound(new TNTPrimeSound(this));
                level.getVibrationManager().callVibrationEvent(new VibrationEvent(source!=null?source:this,add(0.5,0.5,0.5),VibrationType.PRIME_FUSE));
            } catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            if(failure instanceof Error error)throw error;if(failure instanceof RuntimeException error)throw error;
            return true;
        }finally{if(!accepted[0])ticket.discard();}
    }

    @Override
    public int onUpdate(int type) {
        if ((type == Level.BLOCK_UPDATE_NORMAL || type == Level.BLOCK_UPDATE_REDSTONE) && this.level.isBlockPowered(this.getLocation())) {
            this.prime();
        }

        return 0;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.FLINT_STEEL) {
            item.useOn(this);
            this.prime(80, player);
            return true;
        } else if (item.getId() == Item.FIRE_CHARGE) {
            if (!player.isCreative()) item.count--;
            this.level.addSoundToViewers(this, Sound.MOB_GHAST_FIREBALL);
            this.prime(80, player);
            return true;
        } else if (item instanceof ItemTool && item.hasEnchantment(Enchantment.ID_FIRE_ASPECT)) {
            item.useOn(this);
            this.prime(80, player);
            return true;
        }

        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.TNT_BLOCK_COLOR;
    }

    @Override
    public boolean hasEntityCollision() {
        return true;
    }

    @Override
    public void onEntityCollide(Entity entity) {
        if ((entity instanceof EntityArrow && entity.isOnFire()) || entity instanceof EntitySmallFireBall) {
            entity.close();
            this.prime();
        }
    }
}
