package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.entity.mob.EntityWither;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.entity.passive.EntitySkeletonHorse;
import cn.nukkit.entity.passive.EntityZombieHorse;
import cn.nukkit.level.Level;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WitherNaturalTargetTest {
    @Test
    void naturalPredicateRejectsUndeadIncludingFriendlyHorses() {
        Fixture f = new Fixture();
        for (EntityCreature undead : new EntityCreature[]{f.horse,
                alive(EntityZombieHorse.class), alive(EntityZombie.class), alive(EntityWither.class)}) {
            assertFalse(f.wither.targetOption(undead, 1), undead.getClass().getSimpleName());
        }
    }

    @Test
    void naturalScanKeepsLivingCandidateInsteadOfReplacingItWithUndead() {
        Fixture f = new Fixture();
        f.nearby(f.cow, f.horse);
        f.scan();
        assertSame(f.cow, f.wither.target);
    }

    @Test
    void retainedUndeadNaturalTargetIsReplacedByLivingCandidate() {
        Fixture f = new Fixture();
        f.wither.target = f.horse;
        f.nearby(f.cow);
        f.scan();
        assertSame(f.cow, f.wither.target);
    }

    @Test
    void explicitPluginFollowTargetKeepsItsExistingOverride() {
        Fixture f = new Fixture();
        f.wither.setTarget(f.horse);
        f.scan();
        assertSame(f.horse, f.wither.followTarget);
        verify(f.level, never()).getNearbyEntities(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void livingTargetHealthClosedStateAndRangeRemainUnchanged() {
        Fixture f = new Fixture();
        assertTrue(f.wither.targetOption(f.cow, 10000));
        assertFalse(f.wither.targetOption(f.cow, 10000.01));
        f.cow.closed = true;
        assertFalse(f.wither.targetOption(f.cow, 1));
        f.cow.closed = false;
        when(f.cow.isAlive()).thenReturn(false);
        assertFalse(f.wither.targetOption(f.cow, 1));
    }

    @Test
    void playerGameModePolicyRemainsUnchanged() {
        Fixture f = new Fixture();
        Player player = alive(Player.class);
        when(player.isSurvival()).thenReturn(true);
        assertTrue(f.wither.targetOption(player, 1));
        when(player.isSurvival()).thenReturn(false);
        when(player.isAdventure()).thenReturn(true);
        assertTrue(f.wither.targetOption(player, 1));
        when(player.isAdventure()).thenReturn(false);
        assertFalse(f.wither.targetOption(player, 1));
    }

    private static <T extends EntityCreature> T alive(Class<T> type) {
        T entity = mock(type);
        when(entity.isAlive()).thenReturn(true);
        when(entity.canBeFollowed()).thenReturn(true);
        return entity;
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final EntityWither wither = mock(EntityWither.class);
        final EntityCow cow = alive(EntityCow.class);
        final EntitySkeletonHorse horse = alive(EntitySkeletonHorse.class);

        Fixture() {
            wither.boundingBox = new SimpleAxisAlignedBB(0, 64, 0, 0.9, 67.5, 0.9);
            when(wither.getLevel()).thenReturn(level);
            when(wither.isFriendly()).thenReturn(false);
            when(cow.isFriendly()).thenReturn(true);
            when(horse.isFriendly()).thenReturn(true);
            when(wither.distanceSquared(any())).thenCallRealMethod();
            doCallRealMethod().when(wither).targetOption(any(), anyDouble());
            doCallRealMethod().when(wither).canTarget(any());
            doCallRealMethod().when(wither).setTarget(any());
            doCallRealMethod().when(wither).setTarget(any(), anyBoolean());
            doCallRealMethod().when((EntityFlying) wither).checkTarget();
        }

        void nearby(Entity... entities) {
            when(level.getNearbyEntities(any(), same(wither), eq(false), eq(true))).thenReturn(entities);
        }

        void scan() {
            ((EntityFlying) wither).checkTarget();
        }
    }
}
