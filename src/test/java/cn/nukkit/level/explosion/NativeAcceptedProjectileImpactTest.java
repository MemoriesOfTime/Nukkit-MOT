package cn.nukkit.level.explosion;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.projectile.*;
import cn.nukkit.event.*;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.plugin.*;
import cn.nukkit.potion.Effect;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Ordinary tick performs native chunk query/ray/callback; only player session/teleport boundary mocked. */
@Isolated("native Server.instance and event handlers")
class NativeAcceptedProjectileImpactTest {
    @BeforeAll static void boot() throws Exception { ProjectileNativeFixture.boot(); Effect.init(); }
    @AfterAll static void restore() throws Exception { ProjectileNativeFixture.restoreServer(); }

    @Test void cancelledArrowTickDoesNotDamageOrApplyEffect() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityArrow arrow=f.arrow();
            f.listen(e->((ProjectileHitEvent)e).setCancelled());
            arrow.onUpdate(2);
            assertEquals(0,f.target.attacks); assertFalse(f.target.hasEffect(Effect.POISON)); assertFalse(arrow.closed);
        }
    }
    @Test void refusedArrowDamageDoesNotApplyEffect() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityArrow arrow=f.arrow(); f.target.refuse=true;
            arrow.onUpdate(2);
            assertEquals(1,f.target.attacks); assertFalse(f.target.hasEffect(Effect.POISON)); assertTrue(arrow.closed);
        }
    }
    @Test void nestedArrowHitFromDispatcherCannotDamageTwice() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityArrow arrow=f.arrow(); AtomicBoolean once=new AtomicBoolean();
            f.listen(e->{if(once.compareAndSet(false,true))arrow.onCollideWithEntity(f.target);});
            arrow.onUpdate(2);
            assertEquals(1,f.target.attacks); assertTrue(f.target.hasEffect(Effect.POISON)); assertTrue(arrow.closed);
        }
    }
    @Test void acceptedArrowCannotBeManuallyTriggeredAgainAfterNativeClose() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityArrow arrow=f.arrow(); arrow.onUpdate(2); arrow.onCollideWithEntity(f.target); arrow.onUpdate(3);
            assertEquals(1,f.target.attacks); assertTrue(arrow.closed); assertEquals(10,f.target.getEffect(Effect.POISON).getDuration());
        }
    }
    @Test void nativeTickStillDispatchesCustomArrowCollisionOverride() throws Exception {
        try (Fixture f=new Fixture()) {
            VetoArrow arrow=new VetoArrow(f.world.chunk,f.nbt()); arrow.motionX=2; arrow.justCreated=false;
            arrow.onUpdate(2); assertEquals(1,arrow.calls); assertEquals(0,f.target.attacks); assertFalse(arrow.closed);
        }
    }
    @Test void cancelledPearlTickDoesNotTeleportOrChargeOwner() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityEnderPearl pearl=f.pearl();f.listen(e->((ProjectileHitEvent)e).setCancelled());pearl.onUpdate(2);
            verify(f.owner,never()).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            verify(f.owner,never()).attack(any(EntityDamageEvent.class));assertFalse(pearl.closed);assertEquals(0,f.target.attacks);
        }
    }
    @Test void teleportVetoConsumesPearlWithoutFallChargeOrRetry() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityEnderPearl pearl=f.pearl();when(f.owner.teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL))).thenReturn(false);
            pearl.onUpdate(2);pearl.onUpdate(3);pearl.onCollideWithEntity(f.target);
            verify(f.owner,times(1)).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            verify(f.owner,never()).attack(any(EntityDamageEvent.class));assertTrue(pearl.closed);
        }
    }
    @Test void nestedPearlHitFromDispatcherTeleportsOnlyOnce() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityEnderPearl pearl=f.pearl();AtomicBoolean once=new AtomicBoolean();
            f.listen(e->{if(once.compareAndSet(false,true))pearl.onCollideWithEntity(f.target);});pearl.onUpdate(2);
            verify(f.owner,times(1)).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            verify(f.owner,times(1)).attack(any(EntityDamageEvent.class));assertEquals(1,f.target.attacks);
        }
    }
    @Test void teleportCallbackCannotReenterBlockCollisionUpdate() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityEnderPearl pearl=f.pearl();AtomicBoolean once=new AtomicBoolean();
            when(f.owner.teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL))).thenAnswer(call->{
                if(once.compareAndSet(false,true)){pearl.isCollided=true;pearl.onUpdate(3);}return true;
            });pearl.onUpdate(2);
            verify(f.owner,times(1)).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            verify(f.owner,times(1)).attack(any(EntityDamageEvent.class));assertEquals(1,f.target.attacks);assertTrue(pearl.closed);
        }
    }
    @Test void acceptedPearlImpactStillTeleportsWhenVictimRefusesDamage() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityEnderPearl pearl=f.pearl();f.target.refuse=true;pearl.onUpdate(2);
            verify(f.owner,times(1)).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            verify(f.owner,times(1)).attack(any(EntityDamageEvent.class));assertEquals(1,f.target.attacks);assertTrue(pearl.closed);
        }
    }
    @Test void nativeTickStillDispatchesCustomPearlCollisionOverride() throws Exception {
        try (Fixture f=new Fixture()) {
            VetoPearl pearl=new VetoPearl(f.world.chunk,f.nbt(),f.owner);pearl.motionX=2;pearl.justCreated=false;
            pearl.onUpdate(2);assertEquals(1,pearl.calls);assertEquals(0,f.target.attacks);
            verify(f.owner,never()).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
        }
    }
    @Test void nestedGenericCollisionCannotDamageTwice() throws Exception {
        try (Fixture f=new Fixture()) {
            EntitySnowball ball=new EntitySnowball(f.world.chunk,f.nbt());ball.motionX=2;ball.justCreated=false;
            AtomicBoolean once=new AtomicBoolean();f.listen(e->{if(once.compareAndSet(false,true))ball.onCollideWithEntity(f.target);});
            ball.onUpdate(2);assertEquals(1,f.target.attacks);assertTrue(ball.closed);
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"arrow","snowball","skull","blue"})
    void manualDifferentTargetRedirectRejectsAncestorCycle(String kind) throws Exception {
        try (Fixture f=new Fixture()) {
            EntityProjectile projectile=switch(kind){
                case "arrow"->f.arrow();
                case "snowball"->new EntitySnowball(f.world.chunk,f.nbt());
                case "skull"->new EntityWitherSkull(f.world.chunk,f.nbt());
                default->new EntityBlueWitherSkull(f.world.chunk,f.nbt());
            };
            projectile.motionX=2;projectile.justCreated=false;
            Target inner=new Target(f.world.chunk,Entity.getDefaultNBT(new Vector3(8,64,4)));
            java.util.concurrent.atomic.AtomicInteger hits=new java.util.concurrent.atomic.AtomicInteger();
            f.listen(event->{
                ProjectileHitEvent hit=(ProjectileHitEvent)event;hits.incrementAndGet();
                if(hit.getMovingObjectPosition().entityHit==f.target){projectile.onCollideWithEntity(inner);hit.setCancelled();}
                else if(hit.getMovingObjectPosition().entityHit==inner)projectile.onCollideWithEntity(f.target);
            });
            projectile.onUpdate(2);
            assertEquals(2,hits.get(),"A to B is a permitted manual redirect; B to active A is rejected");
            assertEquals(0,f.target.attacks);assertEquals(1,inner.attacks);assertTrue(projectile.closed);
            if(kind.equals("arrow"))assertEquals(10,inner.getEffect(Effect.POISON).getDuration());
        }
    }
    @Test void thrownVictimCallbackReleasesBothArrowInvocationFrames() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityArrow arrow=f.arrow();IllegalStateException failure=new IllegalStateException("victim callback");
            f.target.beforeDamage=()->{throw failure;};
            assertSame(failure,assertThrows(IllegalStateException.class,()->arrow.onUpdate(2)));
            f.target.beforeDamage=null;arrow.onCollideWithEntity(f.target);
            assertEquals(2,f.target.attacks);assertTrue(arrow.closed);assertTrue(f.target.hasEffect(Effect.POISON));
        }
    }
    @Test void cancelledArrowCanRetryAfterDispatcherReturns() throws Exception {
        try (Fixture f=new Fixture()) {
            EntityArrow arrow=f.arrow();AtomicBoolean first=new AtomicBoolean(true);
            f.listen(event->{if(first.getAndSet(false))((ProjectileHitEvent)event).setCancelled();});
            arrow.onUpdate(2);assertFalse(arrow.closed);arrow.onCollideWithEntity(f.target);
            assertEquals(1,f.target.attacks);assertTrue(arrow.closed);assertTrue(f.target.hasEffect(Effect.POISON));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"teleport","fall"})
    void acceptedPearlCallbackFailureCannotLeaveReusableProjectile(String callback) throws Exception {
        try (Fixture f=new Fixture()) {
            EntityEnderPearl pearl=f.pearl();IllegalStateException failure=new IllegalStateException(callback);
            if(callback.equals("teleport"))when(f.owner.teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL))).thenThrow(failure);
            else when(f.owner.attack(any(EntityDamageEvent.class))).thenThrow(failure);
            assertSame(failure,assertThrows(IllegalStateException.class,()->pearl.onUpdate(2)));
            assertTrue(pearl.closed,"An accepted physical pearl impact is consumed even if its callback throws");
            doReturn(true).when(f.owner).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            doReturn(true).when(f.owner).attack(any(EntityDamageEvent.class));
            pearl.onUpdate(3);pearl.onCollideWithEntity(f.target);
            verify(f.owner,times(1)).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            assertEquals(0,f.target.attacks);
        }
    }
    @Test void pearlCleanupFailureKeepsOriginalCallbackException() throws Exception {
        try (Fixture f=new Fixture()) {
            IllegalStateException original=new IllegalStateException("fall charge");
            IllegalStateException cleanup=new IllegalStateException("close observer");
            ThrowingClosePearl pearl=new ThrowingClosePearl(f.world.chunk,f.nbt(),f.owner,cleanup);
            pearl.motionX=2;pearl.justCreated=false;
            when(f.owner.attack(any(EntityDamageEvent.class))).thenThrow(original);
            assertSame(original,assertThrows(IllegalStateException.class,()->pearl.onUpdate(2)));
            assertArrayEquals(new Throwable[]{cleanup},original.getSuppressed());assertTrue(pearl.closed);
            pearl.onUpdate(3);verify(f.owner,times(1)).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
        }
    }
    static final class ThrowingClosePearl extends EntityEnderPearl {
        final RuntimeException failure;
        ThrowingClosePearl(FullChunk chunk,CompoundTag nbt,Entity owner,RuntimeException failure){super(chunk,nbt,owner);this.failure=failure;}
        @Override public void close(){super.close();throw failure;}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"accepted","hit-veto","teleport-veto"})
    void nativeBlockImpactRespectsHitAcceptanceAcrossFollowingTicks(String decision) throws Exception {
        try (Fixture f=new Fixture()) {
            StoneWorld world=ProjectileNativeFixture.level(StoneWorld.class,0);
            assertTrue(world.chunk.setBlock(5,64,4,cn.nukkit.block.Block.STONE,0));
            doReturn(world).when(f.owner).getLevel();
            EntityEnderPearl pearl=new EntityEnderPearl(world.chunk,f.nbt(),f.owner);
            pearl.motionX=2;pearl.justCreated=false;
            java.util.concurrent.atomic.AtomicInteger blockHits=new java.util.concurrent.atomic.AtomicInteger();
            f.listen(event->{
                ProjectileHitEvent hit=(ProjectileHitEvent)event;
                if(hit.getMovingObjectPosition().entityHit==null){blockHits.incrementAndGet();if(decision.equals("hit-veto"))hit.setCancelled();}
            });
            if(decision.equals("teleport-veto"))doReturn(false).when(f.owner).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
            pearl.onUpdate(2);
            assertTrue(pearl.isCollided,"Native movement must reach the solid chunk cell");
            assertTrue(blockHits.get()>0,"Native movement must dispatch the actual block-hit event");
            assertEquals(!decision.equals("hit-veto"),pearl.hadCollision,"Only accepted block events mark collision acceptance");
            pearl.onUpdate(3);pearl.onUpdate(4);
            if(decision.equals("hit-veto")){
                verify(f.owner,never()).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
                verify(f.owner,never()).attack(any(EntityDamageEvent.class));assertTrue(pearl.closed,"Keep the existing consumed block-impact policy");
            }else{
                verify(f.owner,times(1)).teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL));
                verify(f.owner,times(decision.equals("accepted")?1:0)).attack(any(EntityDamageEvent.class));assertTrue(pearl.closed);
            }
        }
    }
    static final class StoneWorld extends ProjectileNativeFixture.MemoryLevel {
        @Override public cn.nukkit.block.Block getBlock(int x,int y,int z,int layer){return super.getBlock(this.chunk,x,y,z,layer,false);}
    }
    static final class VetoArrow extends EntityArrow {
        int calls;VetoArrow(FullChunk chunk,CompoundTag nbt){super(chunk,nbt);}
        @Override public void onCollideWithEntity(Entity victim){calls++;}
    }
    static final class VetoPearl extends EntityEnderPearl {
        int calls;VetoPearl(FullChunk chunk,CompoundTag nbt,Entity owner){super(chunk,nbt,owner);}
        @Override public void onCollideWithEntity(Entity victim){calls++;}
    }
    static final class Target extends EntityLiving {
        int attacks;boolean refuse;Runnable beforeDamage;
        Target(FullChunk chunk,CompoundTag nbt){super(chunk,nbt);setMaxHealth(100);setHealth(100);}
        @Override public int getNetworkId(){return 0;}
        @Override public float getWidth(){return .6f;}
        @Override public float getHeight(){return 1.8f;}
        @Override public boolean attack(EntityDamageEvent event){attacks++;if(beforeDamage!=null)beforeDamage.run();return !refuse&&super.attack(event);}
    }
    static final class Fixture implements AutoCloseable {
        final ProjectileNativeFixture.MemoryLevel world=ProjectileNativeFixture.level(ProjectileNativeFixture.MemoryLevel.class,0);
        final Target target=new Target(world.chunk,Entity.getDefaultNBT(new Vector3(5,64,4)));
        final Player owner=mock(Player.class);
        final List<RegisteredListener> listeners=new ArrayList<>();
        Fixture() throws Exception {
            when(owner.getBoundingBox()).thenReturn(new cn.nukkit.math.SimpleAxisAlignedBB(0,64,0,0.6,65.8,0.6));
            when(owner.getLevel()).thenReturn(world);when(owner.isOnline()).thenReturn(true);when(owner.isAlive()).thenReturn(true);
            when(owner.getGamemode()).thenReturn(Player.SURVIVAL);
            when(owner.teleport(any(Vector3.class),eq(TeleportCause.ENDER_PEARL))).thenReturn(true);
            when(owner.attack(any(EntityDamageEvent.class))).thenReturn(true);
        }
        CompoundTag nbt(){return Entity.getDefaultNBT(new Vector3(4,64,4));}
        EntityArrow arrow(){
            CompoundTag nbt=nbt().putList(new ListTag<CompoundTag>("mobEffects").add(Effect.getEffect(Effect.POISON).setDuration(80).save()));
            EntityArrow arrow=new EntityArrow(world.chunk,nbt);arrow.motionX=2;arrow.justCreated=false;return arrow;
        }
        EntityEnderPearl pearl(){EntityEnderPearl pearl=new EntityEnderPearl(world.chunk,nbt(),owner);pearl.motionX=2;pearl.justCreated=false;return pearl;}
        void listen(Consumer<Event> action){
            PluginBase plugin=new PluginBase(){};plugin.setEnabled(true);
            RegisteredListener listener=new RegisteredListener(new Listener(){},(ignored,event)->action.accept(event),EventPriority.HIGHEST,plugin,false);
            ProjectileHitEvent.getHandlers().register(listener);listeners.add(listener);
        }
        @Override public void close(){listeners.forEach(ProjectileHitEvent.getHandlers()::unregister);}
    }
}
