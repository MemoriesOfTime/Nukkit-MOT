package cn.nukkit.level.explosion;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.projectile.EntityGhastFireBall;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.*;
import cn.nukkit.level.Level;
import cn.nukkit.level.WeakExplosion;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.AxisAlignedBB;
import java.util.Arrays;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.RegisteredListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real projectile construction/motion/impact/dispatcher; player network/session boundary mocked. */
@Isolated("native Server.instance and event handlers")
class GhastFireballReflectionP15Test {
    @BeforeAll static void boot() throws Exception { ProjectileNativeFixture.boot(); }
    @AfterAll static void restore() throws Exception { ProjectileNativeFixture.restoreServer(); }

    @Test void everyAcceptedStrikeChangesDirectionAndOwner() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.ball.attack(f.strike(f.a)));
            assertSame(f.a, f.ball.directionChanged);
            assertSame(f.a, f.ball.shootingEntity);
            assertEquals(-1, f.ball.motionX, 1e-9);
            assertTrue(f.ball.attack(f.strike(f.b)));
            assertSame(f.b, f.ball.directionChanged);
            assertSame(f.b, f.ball.shootingEntity);
            assertEquals(1, f.ball.motionX, 1e-9);
        }
    }

    @Test void samePlayerCanDeflectAgainWithNewAim() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.ball.attack(f.strike(f.a)));
            f.a.yaw = 180;
            assertTrue(f.ball.attack(f.strike(f.a)));
            assertEquals(-1, f.ball.motionZ, 1e-9);
        }
    }

    @Test void precancelledStrikeCannotReflect() throws Exception {
        try (Fixture f = new Fixture()) {
            var event=f.strike(f.a);event.setCancelled();
            assertFalse(f.ball.attack(event));
            assertNull(f.ball.directionChanged);
            assertSame(f.original, f.ball.shootingEntity);
            assertEquals(2, f.ball.motionX);
        }
    }

    @Test void damageListenerVetoPreservesPreviousReflection() throws Exception {
        try (Fixture f = new Fixture()) {
            f.ball.attack(f.strike(f.a));
            AtomicInteger calls=new AtomicInteger();
            f.listen(EntityDamageEvent.getHandlers(), e -> {
                if (((EntityDamageEvent)e).getEntity()==f.ball) { calls.incrementAndGet();((EntityDamageEvent)e).setCancelled(); }
            });
            assertFalse(f.ball.attack(f.strike(f.b)));
            assertEquals(1,calls.get());
            assertSame(f.a,f.ball.directionChanged);
            assertSame(f.a,f.ball.shootingEntity);
            assertEquals(-1,f.ball.motionX,1e-9);
        }
    }

    @Test void rejectedNativeMotionCannotReserveTheNextOwner() throws Exception {
        try (Fixture f=new Fixture()) {
            f.ball.attack(f.strike(f.a));
            AtomicInteger calls=new AtomicInteger();
            f.listen(EntityMotionEvent.getHandlers(),e->{
                if (((EntityMotionEvent)e).getEntity()==f.ball) {calls.incrementAndGet();((EntityMotionEvent)e).setCancelled();}
            });
            assertFalse(f.ball.attack(f.strike(f.b)));
            assertEquals(1,calls.get());
            assertSame(f.a,f.ball.directionChanged);
            assertSame(f.a,f.ball.shootingEntity);
            assertEquals(-1,f.ball.motionX,1e-9);
        }
    }

    @Test void actorWorldChangedByStrikeCallbackCannotReflect() throws Exception {
        try (Fixture f=new Fixture()) {
            f.listen(EntityDamageEvent.getHandlers(),e->{if(((EntityDamageEvent)e).getEntity()==f.ball)f.a.level=mock(Level.class);});
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertNull(f.ball.directionChanged);
            assertSame(f.original,f.ball.shootingEntity);
        }
    }

    @Test void actorInvalidatedByStrikeCallbackCannotReflect() throws Exception {
        try (Fixture f=new Fixture()) {
            f.listen(EntityDamageEvent.getHandlers(),e->{if(((EntityDamageEvent)e).getEntity()==f.ball)doReturn(false).when(f.a).isAlive();});
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertNull(f.ball.directionChanged);
        }
    }

    @Test void closedSourceCannotReflect() throws Exception {
        try (Fixture f=new Fixture()) {
            f.ball.close();
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertNull(f.ball.directionChanged);
        }
    }

    @Test void sourceClosedByStrikeCallbackCannotReflect() throws Exception {
        try (Fixture f=new Fixture()) {
            f.listen(EntityDamageEvent.getHandlers(),e->{if(((EntityDamageEvent)e).getEntity()==f.ball)f.ball.close();});
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertTrue(f.ball.closed);
            assertNull(f.ball.directionChanged);
        }
    }

    @Test void nestedStrikeCannotOverwriteAcceptedOuterOwner() throws Exception {
        try (Fixture f=new Fixture()) {
            AtomicBoolean nested=new AtomicBoolean();
            f.listen(EntityDamageEvent.getHandlers(),e->{
                if(((EntityDamageEvent)e).getEntity()==f.ball && nested.compareAndSet(false,true)) {
                    assertFalse(f.ball.attack(f.strike(f.b)));
                }
            });
            assertTrue(f.ball.attack(f.strike(f.a)));
            assertTrue(nested.get());
            assertSame(f.a,f.ball.directionChanged);
            assertSame(f.a,f.ball.shootingEntity);
        }
    }

    @Test void invalidAimCannotPoisonProjectileMotion() throws Exception {
        try (Fixture f=new Fixture()) {
            f.a.yaw=Double.NaN;
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertEquals(2,f.ball.motionX);
            assertNull(f.ball.directionChanged);
        }
    }

    @Test void currentDeflectorIsIgnoredButPreviousDeflectorCanBeHit() throws Exception {
        try (Fixture f=new Fixture()) {
            f.ball.attack(f.strike(f.a));f.ball.attack(f.strike(f.b));
            doReturn(true).when(f.a).attack(any(EntityDamageEvent.class));
            doReturn(true).when(f.b).attack(any(EntityDamageEvent.class));
            f.ball.onCollideWithEntity(f.b);
            verify(f.b,never()).attack(any(EntityDamageEvent.class));
            assertFalse(f.ball.isCollided);
            f.ball.onCollideWithEntity(f.a);
            var impact=org.mockito.ArgumentCaptor.forClass(EntityDamageByChildEntityEvent.class);
            verify(f.a,times(1)).attack(impact.capture());
            assertSame(f.b,impact.getValue().getDamager());
            assertTrue(f.ball.isCollided);
        }
    }

    @Test void duplicateImpactCannotReplayDamageOrBurn() throws Exception {
        try (Fixture f=new Fixture()) {
            var target=f.target();AtomicInteger combustions=new AtomicInteger();
            f.listen(EntityCombustEvent.getHandlers(),e->{if(((EntityCombustEvent)e).getEntity()==target)combustions.incrementAndGet();});
            f.ball.onCollideWithEntity(target);f.ball.onCollideWithEntity(target);
            assertEquals(1,target.attacks);assertEquals(1,combustions.get());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void acceptedImpactSurvivesNormalTickMovement(boolean refusedDamage) throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.ball.setPosition(new Vector3(4, 64, 4)));
            var target = new TickTarget(f.world.chunk,
                    Entity.getDefaultNBT(new Vector3(5, 64, 4)));
            target.refuseDamage=refusedDamage;
            target.setMaxHealth(40);
            target.setHealth(40);
            f.ball.setExplode(true);
            AtomicInteger primes = new AtomicInteger();
            f.listen(EntityExplosionPrimeEvent.getHandlers(), event -> {
                if (((EntityExplosionPrimeEvent) event).getEntity() == f.ball) {
                    primes.incrementAndGet();
                    ((EntityExplosionPrimeEvent) event).setCancelled();
                }
            });
            assertFalse(f.ball.keepMovement);
            assertFalse(f.ball.noClip);

            // Native chunk query, ray interception, collision callback and unrestricted move.
            f.ball.onUpdate(2);

            assertEquals(1, target.attacks, "The normal tick must reach the real target");
            assertTrue(f.ball.x > 4, "The same tick must perform its free-air move");
            boolean reflectedAfterImpact = f.ball.attack(f.strike(f.a));
            f.ball.onUpdate(3);
            f.ball.onUpdate(4);

            assertAll(
                    () -> assertFalse(reflectedAfterImpact, "Movement cannot rearm a consumed fireball"),
                    () -> assertEquals(1, primes.get(), "The accepted impact must prime exactly once"),
                    () -> assertTrue(f.ball.closed),
                    () -> assertEquals(1, target.attacks));
        }
    }

    @Test void closedSourceCannotReplayAnImpact() throws Exception {
        try (Fixture f=new Fixture()) {
            var target=f.target();f.ball.close();f.ball.onCollideWithEntity(target);
            assertEquals(0,target.attacks);
        }
    }

    @Test void nestedHitCallbackCannotSettleTheSourceTwice() throws Exception {
        try (Fixture f=new Fixture()) {
            var target=f.target();AtomicBoolean nested=new AtomicBoolean();
            f.listen(ProjectileHitEvent.getHandlers(),e->{
                if(((ProjectileHitEvent)e).getEntity()==f.ball && nested.compareAndSet(false,true))f.ball.onCollideWithEntity(target);
            });
            f.ball.onCollideWithEntity(target);
            assertEquals(1,target.attacks);
        }
    }

    @Test void strikeAfterAcceptedImpactCannotRearmSource() throws Exception {
        try (Fixture f=new Fixture()) {
            f.ball.attack(f.strike(f.a));f.ball.onCollideWithEntity(f.target());
            assertFalse(f.ball.attack(f.strike(f.b)));
            assertSame(f.a,f.ball.shootingEntity);
            assertSame(f.a,f.ball.directionChanged);
        }
    }

    @Test void cancelledHitKeepsOneAvailableSourceForNextImpact() throws Exception {
        try (Fixture f=new Fixture()) {
            var target=f.target();AtomicBoolean cancel=new AtomicBoolean(true);
            f.listen(ProjectileHitEvent.getHandlers(),e->{if(((ProjectileHitEvent)e).getEntity()==f.ball)((ProjectileHitEvent)e).setCancelled(cancel.get());});
            f.ball.onCollideWithEntity(target);assertFalse(f.ball.isCollided);assertEquals(0,target.attacks);
            cancel.set(false);f.ball.onCollideWithEntity(target);assertEquals(1,target.attacks);assertTrue(f.ball.isCollided);
        }
    }

    @Test void repeatedReflectionDoesNotCreateItemsOrEntitiesAndExplodesOnceForNewestOwner() throws Exception {
        try (Fixture f=new Fixture()) {
            long id=f.ball.getId();
            for(int i=0;i<100;i++)assertTrue(f.ball.attack(f.strike((i&1)==0?f.a:f.b)));
            assertEquals(id,f.ball.getId());assertSame(f.ball,f.world.getEntity(id));
            assertTrue(f.world.allDrops().isEmpty());
            assertSame(f.b,f.ball.shootingEntity);
            AtomicInteger primes=new AtomicInteger();
            f.listen(EntityExplosionPrimeEvent.getHandlers(),e->{
                if(((EntityExplosionPrimeEvent)e).getEntity()==f.ball){primes.incrementAndGet();assertSame(f.b,f.ball.shootingEntity);((EntityExplosionPrimeEvent)e).setCancelled();}
            });
            f.ball.setExplode(true);f.ball.onCollideWithEntity(f.target());
            f.ball.onUpdate(2);f.ball.onUpdate(3);f.ball.explode();
            assertEquals(1,primes.get());assertTrue(f.ball.closed);assertTrue(f.world.allDrops().isEmpty());
        }
    }

    @Test void actorWorldChangedByMotionCallbackCannotCommit() throws Exception {
        try(Fixture f=new Fixture()) {
            f.listen(EntityMotionEvent.getHandlers(),e->{if(((EntityMotionEvent)e).getEntity()==f.ball)f.a.level=mock(Level.class);});
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertEquals(2,f.ball.motionX);assertNull(f.ball.directionChanged);assertSame(f.original,f.ball.shootingEntity);
        }
    }

    @Test void sourceClosedByMotionCallbackCannotCommit() throws Exception {
        try(Fixture f=new Fixture()) {
            f.listen(EntityMotionEvent.getHandlers(),e->{if(((EntityMotionEvent)e).getEntity()==f.ball)f.ball.close();});
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertEquals(2,f.ball.motionX);assertNull(f.ball.directionChanged);assertTrue(f.ball.closed);
        }
    }

    @Test void invalidListenerMotionCannotCommit() throws Exception {
        try(Fixture f=new Fixture()) {
            f.listen(EntityMotionEvent.getHandlers(),e->{if(((EntityMotionEvent)e).getEntity()==f.ball)((EntityMotionEvent)e).getMotion().x=Double.NaN;});
            assertFalse(f.ball.attack(f.strike(f.a)));
            assertEquals(2,f.ball.motionX);assertNull(f.ball.directionChanged);
        }
    }

    @Test void nestedImpactDuringMotionCannotConsumeHalfCommittedOwner() throws Exception {
        try(Fixture f=new Fixture()) {
            var target=f.target();
            f.listen(EntityMotionEvent.getHandlers(),e->{if(((EntityMotionEvent)e).getEntity()==f.ball)f.ball.onCollideWithEntity(target);});
            assertTrue(f.ball.attack(f.strike(f.a)));
            assertEquals(0,target.attacks);assertFalse(f.ball.isCollided);assertSame(f.a,f.ball.shootingEntity);
            f.ball.onCollideWithEntity(target);assertEquals(1,target.attacks);
        }
    }

    @Test void nestedDamageCallbackCannotReplayAcceptedImpact() throws Exception {
        try(Fixture f=new Fixture()) {
            var target=f.target();AtomicBoolean nested=new AtomicBoolean();
            f.listen(EntityDamageEvent.getHandlers(),e->{if(((EntityDamageEvent)e).getEntity()==target && nested.compareAndSet(false,true))f.ball.onCollideWithEntity(target);});
            f.ball.onCollideWithEntity(target);assertTrue(nested.get());assertEquals(1,target.attacks);
        }
    }

    @Test void sourceClosedByHitCallbackCannotDamage() throws Exception {
        try(Fixture f=new Fixture()) {
            var target=f.target();
            f.listen(ProjectileHitEvent.getHandlers(),e->{if(((ProjectileHitEvent)e).getEntity()==f.ball)f.ball.close();});
            f.ball.onCollideWithEntity(target);assertEquals(0,target.attacks);
        }
    }

    @Test void spectatorAndOfflineActorsCannotReflect() throws Exception {
        try(Fixture f=new Fixture()) {
            doReturn(true).when(f.a).isSpectator();assertFalse(f.ball.attack(f.strike(f.a)));
            doReturn(false).when(f.b).isOnline();assertFalse(f.ball.attack(f.strike(f.b)));
            assertNull(f.ball.directionChanged);assertEquals(2,f.ball.motionX);
        }
    }

    @Test void movementNotificationSeesCommittedOwnerAndCannotUndoAcceptedMotion() throws Exception {
        try(Fixture f=new Fixture()) {
            var source=spy(f.ball);
            doAnswer(invocation->{assertSame(f.a,source.shootingEntity);assertSame(f.a,source.directionChanged);
                f.a.level=mock(Level.class);throw new AssertionError("movement notification failed");}).when(source).updateMovement();
            var strike=new EntityDamageByEntityEvent(f.a,source,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);
            assertThrows(AssertionError.class,()->source.attack(strike));
            assertSame(f.a,source.shootingEntity);assertEquals(-1,source.motionX,1e-9);
        }
    }

    @Test void reflectedBlastCanHitCurrentOwnerAndCreditsThatOwner() throws Exception {
        try(Fixture f=new Fixture()) {
            f.ball.attack(f.strike(f.a));f.ball.attack(f.strike(f.b));
            f.b.x=1;f.b.y=64;
            doReturn(true).when(f.b).attack(any(EntityDamageEvent.class));
            doReturn(true).when(f.b).setMotion(any(Vector3.class));
            var target=f.target();target.x=1;
            f.world.candidates=new Entity[]{f.b,target};
            f.listen(EntityExplosionPrimeEvent.getHandlers(),e->{if(((EntityExplosionPrimeEvent)e).getEntity()==f.ball)((EntityExplosionPrimeEvent)e).setBlockBreaking(false);});
            f.ball.setExplode(true);f.ball.isCollided=true;f.ball.onUpdate(2);f.ball.onUpdate(3);
            var damage=org.mockito.ArgumentCaptor.forClass(EntityDamageByEntityEvent.class);
            verify(f.b,times(1)).attack(damage.capture());
            assertSame(f.b,damage.getValue().getDamager());assertEquals(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,damage.getValue().getCause());
            assertEquals(1,target.attacks);assertSame(f.b,((EntityDamageByEntityEvent)target.getLastDamageCause()).getDamager());
            assertSame(f.ball,f.world.ignoredQueries.get(0));
        }
    }

    @Test void cancelledPrimeLeavesCurrentOwnerUndamaged() throws Exception {
        try(Fixture f=new Fixture()) {
            f.ball.attack(f.strike(f.b));f.b.x=1;f.b.y=64;f.world.candidates=new Entity[]{f.b};
            f.listen(EntityExplosionPrimeEvent.getHandlers(),e->{if(((EntityExplosionPrimeEvent)e).getEntity()==f.ball)((EntityExplosionPrimeEvent)e).setCancelled();});
            f.ball.setExplode(true);f.ball.isCollided=true;f.ball.onUpdate(2);f.ball.onUpdate(3);
            verify(f.b,never()).attack(any(EntityDamageEvent.class));assertTrue(f.ball.closed);
        }
    }

    @Test void legacyWeakExplosionConstructorRetainsItsExistingOwnerExclusion() throws Exception {
        try(Fixture f=new Fixture()) {
            var owner=f.target();owner.x=1;var other=f.target();other.x=1;
            f.world.candidates=new Entity[]{owner,other};
            assertTrue(new WeakExplosion(new Position(0,64,0,f.world),1.2,owner).explodeB());
            assertEquals(0,owner.attacks);assertEquals(1,other.attacks);
            assertSame(owner,((EntityDamageByEntityEvent)other.getLastDamageCause()).getDamager());
            assertSame(owner,f.world.ignoredQueries.get(0));
        }
    }

    @Test void nestedTickFromStrikeDispatcherCannotMoveSource() throws Exception {
        try (Fixture f = new Fixture()) {
            f.ball.setPosition(new Vector3(4,64,4));
            f.listen(EntityDamageEvent.getHandlers(), event -> {
                if (((EntityDamageEvent) event).getEntity()==f.ball) {
                    f.ball.onUpdate(2);
                }
            });
            assertTrue(f.ball.attack(f.strike(f.a)));
            assertEquals(4,f.ball.x,"A nested tick cannot move an uncommitted reflection");
            assertSame(f.a,f.ball.shootingEntity);
        }
    }
    @Test void nestedTickFromVictimCannotPrimeBeforeImpactReturns() throws Exception {
        try (Fixture f = new Fixture()) {
            f.ball.setPosition(new Vector3(4,64,4));f.ball.setExplode(true);
            TickTarget target=new TickTarget(f.world.chunk,Entity.getDefaultNBT(new Vector3(5,64,4)));
            target.setMaxHealth(40);target.setHealth(40);
            AtomicInteger primes=new AtomicInteger();
            f.listen(EntityExplosionPrimeEvent.getHandlers(),event->{primes.incrementAndGet();((EntityExplosionPrimeEvent)event).setCancelled();});
            target.beforeDamage=()->{
                f.ball.onUpdate(3);
                assertFalse(f.ball.closed,"Nested tick cannot retire an in-progress impact");
                assertEquals(0,primes.get());
            };
            f.ball.onUpdate(2);
            assertEquals(1,target.attacks);
            f.ball.onUpdate(4);assertEquals(1,primes.get());assertTrue(f.ball.closed);
        }
    }

    @Test void customMotionOverrideRemainsTheReflectionEntryPoint() throws Exception {
        try (Fixture f = new Fixture()) {
            CustomMotionBall ball = new CustomMotionBall(f.world.chunk, Entity.getDefaultNBT(new Vector3(0,64,0)), f.original);
            ball.motionCalls=0;
            assertTrue(ball.attack(new EntityDamageByEntityEvent(f.a, ball, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1)));
            assertEquals(1, ball.motionCalls);
            assertEquals(7, ball.motionX);
        }
    }
    static final class CustomMotionBall extends EntityGhastFireBall {
        int motionCalls;
        CustomMotionBall(FullChunk chunk, CompoundTag nbt, Entity owner) { super(chunk, nbt, owner); }
        @Override public boolean setMotion(Vector3 motion) { motionCalls++; motionX=7; return true; }
    }

    static final class World extends ProjectileNativeFixture.MemoryLevel {
        Entity[] candidates;
        List<Entity> ignoredQueries;
        @Override public Entity[] getNearbyEntities(AxisAlignedBB box,Entity ignored) {
            ignoredQueries.add(ignored);
            return Arrays.stream(candidates).filter(entity->entity!=ignored).toArray(Entity[]::new);
        }
    }

    static final class Target extends Entity {
        int attacks;
        Target(FullChunk chunk,CompoundTag nbt){super(chunk,nbt);}
        @Override public int getNetworkId(){return 0;}
        @Override public boolean attack(EntityDamageEvent event){attacks++;return super.attack(event);}
    }

    static final class TickTarget extends EntityLiving {
        int attacks;
        Runnable beforeDamage;
        boolean refuseDamage;
        TickTarget(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }
        @Override public int getNetworkId() { return 0; }
        @Override public float getWidth() { return 0.6f; }
        @Override public float getHeight() { return 1.8f; }
        @Override public boolean attack(EntityDamageEvent event) { attacks++; if(beforeDamage!=null)beforeDamage.run(); return !refuseDamage && super.attack(event); }
    }

    static final class Fixture implements AutoCloseable {
        final World world;
        final Entity original=mock(Entity.class);
        final boolean previousMotionEvent=ProjectileNativeFixture.server.callEntityMotionEv;
        final EntityGhastFireBall ball;
        final Player a,b;
        final List<RegisteredListener> listeners=new ArrayList<>();
        final List<HandlerList> lists=new ArrayList<>();
        Fixture() throws Exception {
            ProjectileNativeFixture.server.callEntityMotionEv=true;
            world=ProjectileNativeFixture.level(World.class,Block.AIR);
            world.candidates=new Entity[0];world.ignoredQueries=new ArrayList<>();
            ball=new EntityGhastFireBall(world.chunk,Entity.getDefaultNBT(new Vector3(0,64,0)),original);
            ball.justCreated=false;ball.motionX=2;
            a=actor(90);b=actor(270);
        }
        Player actor(double yaw) {
            Player p=mock(Player.class,CALLS_REAL_METHODS);p.level=world;p.yaw=yaw;
            doReturn(new SimpleAxisAlignedBB(-0.3,0,-0.3,0.3,1.8,0.3)).when(p).getBoundingBox();
            doReturn(false).when(p).hasEffect(anyInt());doReturn(true).when(p).isAlive();
            doReturn(true).when(p).isOnline();doReturn(false).when(p).isSpectator();
            return p;
        }
        EntityDamageByEntityEvent strike(Player p){return new EntityDamageByEntityEvent(p,ball,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);}
        Target target(){var t=new Target(world.chunk,Entity.getDefaultNBT(new Vector3(3,64,0)));t.setMaxHealth(40);t.setHealth(40);return t;}
        void listen(HandlerList handlers,Consumer<Event> callback) {
            var plugin=new PluginBase(){};plugin.setEnabled(true);
            var listener=new RegisteredListener(new Listener(){},(ignored,e)->callback.accept(e),EventPriority.HIGHEST,plugin,false);
            handlers.register(listener);listeners.add(listener);lists.add(handlers);
        }
        @Override public void close(){for(int i=listeners.size()-1;i>=0;i--)lists.get(i).unregister(listeners.get(i));ProjectileNativeFixture.server.callEntityMotionEv=previousMotionEvent;}
    }
}
