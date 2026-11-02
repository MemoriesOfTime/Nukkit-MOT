package cn.nukkit.entity.projectile;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native pearl impact and private teleport body; only destination/world/network and actor mocked. */
class EnderPearlImpactCancellationTest {
    private EntityEnderPearl pearl;
    private EntityLiving victim;
    private PluginManager plugins;
    private Player shooter;

    @BeforeAll static void boot() { MockServer.init(); }
    @BeforeEach void setup() throws Exception {
        MockServer.init();
        plugins = Server.getInstance().getPluginManager();
        reset(plugins);
        pearl = mock(EntityEnderPearl.class, CALLS_REAL_METHODS);
        Field server = Entity.class.getDeclaredField("server");
        server.setAccessible(true); server.set(pearl, Server.getInstance());
        pearl.level = mock(Level.class);
        pearl.namedTag = new CompoundTag();
        pearl.x=4.2; pearl.y=65; pearl.z=3.7;
        shooter = mock(Player.class);
        when(shooter.getLevel()).thenReturn(pearl.level);
        when(shooter.isOnline()).thenReturn(true);
        when(shooter.isAlive()).thenReturn(true);
        when(shooter.getGamemode()).thenReturn(Player.SURVIVAL);
        when(shooter.teleport(any(Vector3.class), eq(TeleportCause.ENDER_PEARL))).thenReturn(true);
        when(shooter.attack(any(EntityDamageEvent.class))).thenReturn(true);
        pearl.shootingEntity=shooter;
        victim = mock(EntityLiving.class);
        when(victim.attack(any(EntityDamageEvent.class))).thenReturn(true);
        doNothing().when(pearl).close();
    }

    @Test void cancelledHitMustNotTeleportOrDamageOwner() {
        doAnswer(call -> { ((ProjectileHitEvent)call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(ProjectileHitEvent.class));
        pearl.onCollideWithEntity(victim);
        verify(shooter, never()).teleport(any(Vector3.class), eq(TeleportCause.ENDER_PEARL));
        verify(shooter, never()).attack(any(EntityDamageEvent.class));
        verify(victim, never()).attack(any(EntityDamageEvent.class));
    }

    @Test void nestedHitCallbackTeleportsAndChargesOnlyOnce() {
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> {
            if (once.compareAndSet(false, true)) pearl.onCollideWithEntity(victim);
            return null;
        }).when(plugins).callEvent(any(ProjectileHitEvent.class));
        pearl.onCollideWithEntity(victim);
        verify(shooter, times(1)).teleport(any(Vector3.class), eq(TeleportCause.ENDER_PEARL));
        verify(shooter, times(1)).attack(any(EntityDamageEvent.class));
    }

    @Test void refusedTeleportMustNotChargeFallDamage() {
        when(shooter.teleport(any(Vector3.class), eq(TeleportCause.ENDER_PEARL))).thenReturn(false);
        pearl.onCollideWithEntity(victim);
        verify(shooter, times(1)).teleport(any(Vector3.class), eq(TeleportCause.ENDER_PEARL));
        verify(shooter, never()).attack(any(EntityDamageEvent.class));
    }

    @Test void acceptedSurvivalTeleportRetainsOneFallChargeAndCenteredTarget() {
        pearl.onCollideWithEntity(victim);
        var destination=org.mockito.ArgumentCaptor.forClass(Vector3.class);
        verify(shooter, times(1)).teleport(destination.capture(), eq(TeleportCause.ENDER_PEARL));
        assertEquals(4.5, destination.getValue().x); assertEquals(65, destination.getValue().y);
        assertEquals(3.5, destination.getValue().z);
        var damage=org.mockito.ArgumentCaptor.forClass(EntityDamageEvent.class);
        verify(shooter, times(1)).attack(damage.capture());
        assertEquals(EntityDamageEvent.DamageCause.FALL, damage.getValue().getCause());
        assertEquals(5f, damage.getValue().getDamage());
        verify(pearl, times(1)).close();
    }

    @Test void acceptedCreativeTeleportRetainsNoFallCharge() {
        when(shooter.getGamemode()).thenReturn(Player.CREATIVE);
        pearl.onCollideWithEntity(victim);
        verify(shooter, times(1)).teleport(any(Vector3.class), eq(TeleportCause.ENDER_PEARL));
        verify(shooter, never()).attack(any(EntityDamageEvent.class));
    }

    @Test void ownerInAnotherWorldDoesNotTeleportOrTakeFallDamage() {
        when(shooter.getLevel()).thenReturn(mock(Level.class));
        pearl.onCollideWithEntity(victim);
        verify(shooter, never()).teleport(any(Vector3.class), eq(TeleportCause.ENDER_PEARL));
        verify(shooter, never()).attack(any(EntityDamageEvent.class));
    }
}
