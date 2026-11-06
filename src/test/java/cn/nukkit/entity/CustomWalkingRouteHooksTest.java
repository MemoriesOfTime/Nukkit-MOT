package cn.nukkit.entity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.route.Node;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomWalkingRouteHooksTest {
    @Test void customArrivalVetoRemainsAuthoritative() throws Exception {
        Fixture f = new Fixture(false);
        f.entity.updateMove(1);
        assertSame(f.previousTarget, f.entity.target);
    }
    @Test void customNextWaypointTransformationRemainsAuthoritative() throws Exception {
        Fixture f = new Fixture(true);
        f.entity.updateMove(1);
        assertSame(f.route.transformed, f.entity.target);
    }
    private static final class LegacyRoute extends WalkerRouteFinder {
        final boolean arrived;
        final Vector3 transformed = new Vector3(73, 64, 19);
        LegacyRoute(EntityWalking entity, boolean arrived) { super(entity); this.arrived = arrived; }
        @Override public boolean hasArrivedNode(Vector3 position) { return arrived; }
        @Override public Vector3 next() { super.next(); return transformed; }
    }
    private static final class Fixture {
        final EntityWalking entity = mock(EntityWalking.class, CALLS_REAL_METHODS);
        final Vector3 previousTarget = new Vector3(9.5, 64, 0.5);
        final LegacyRoute route;
        Fixture(boolean arrived) throws Exception {
            Level level = mock(Level.class);
            Server server = mock(Server.class);
            Block stone = mock(Block.class);
            when(stone.getId()).thenReturn(Block.STONE);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(false))).thenReturn(stone);
            entity.level = level; entity.x = 0.5; entity.y = 64; entity.z = 0.5;
            entity.age = 1; entity.onGround = true; entity.target = previousTarget;
            doReturn(server).when(entity).getServer();
            doReturn(stone).when(entity).getLevelBlock();
            doReturn(true).when(entity).isInTickingRange();
            doReturn(false).when(entity).isImmobile();
            doReturn(false).when(entity).isKnockback();
            doReturn(false).when(entity).checkJump(anyDouble(), anyDouble());
            doReturn(false).when(entity).move(anyDouble(), anyDouble(), anyDouble());
            doNothing().when(entity).updateMovement();
            route = new LegacyRoute(entity, arrived);
            Constructor<Node> constructor = Node.class.getDeclaredConstructor(Vector3.class);
            constructor.setAccessible(true);
            route.addNode(constructor.newInstance(new Vector3(0.5, 64, 0.5)));
            route.addNode(constructor.newInstance(new Vector3(1.5, 64, 0.5)));
            entity.route = route;
        }
    }
}
