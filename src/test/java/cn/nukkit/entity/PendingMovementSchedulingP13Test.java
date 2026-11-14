package cn.nukkit.entity;

import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.RouteFinderThreadPool;
import cn.nukkit.entity.route.WalkerRouteFinder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PendingMovementSchedulingP13Test {
    @Test
    void nativePendingTickAllocatesNoDuplicateTask() throws Exception {
        EntityWalking entity = movementTick();
        RouteFinderSearchTask pending = new RouteFinderSearchTask(entity.route);
        queue(pending);
        try (MockedConstruction<RouteFinderSearchTask> created = mockConstruction(RouteFinderSearchTask.class);
             MockedStatic<RouteFinderThreadPool> pool = mockStatic(RouteFinderThreadPool.class)) {
            entity.updateMove(1);
            System.out.println("p13 pending_tick_task_allocations=" + created.constructed().size());
            assertEquals(0, created.constructed().size(), "pending native tick must not construct another task");
            pool.verify(() -> RouteFinderThreadPool.executeRouteFinderThread(any()), never());
        } finally {
            discard(pending);
        }
    }

    @Test
    void releasedRouteRetainsItsNormalEveryTenTicksSubmission() throws Exception {
        EntityWalking entity = movementTick();
        RouteFinderSearchTask previous = new RouteFinderSearchTask(entity.route);
        queue(previous);
        discard(previous);
        try (MockedConstruction<RouteFinderSearchTask> created = mockConstruction(RouteFinderSearchTask.class);
             MockedStatic<RouteFinderThreadPool> pool = mockStatic(RouteFinderThreadPool.class)) {
            entity.updateMove(1);
            assertEquals(1, created.constructed().size());
            pool.verify(() -> RouteFinderThreadPool.executeRouteFinderThread(created.constructed().get(0)));
        }
    }

    private static EntityWalking movementTick() {
        EntityWalking entity = mock(EntityWalking.class, CALLS_REAL_METHODS);
        entity.age = 10;
        entity.riding = mock(Entity.class); // Exit before unrelated movement/physics.
        doReturn(true).when(entity).isInTickingRange();
        doReturn(false).when(entity).isImmobile();
        doReturn(true).when(entity).isKnockback();
        entity.route = new WalkerRouteFinder(entity);
        return entity;
    }

    private static void queue(RouteFinderSearchTask task) throws Exception {
        try {
            Method method = RouteFinderSearchTask.class.getDeclaredMethod("tryQueue");
            method.setAccessible(true);
            assertTrue((boolean) method.invoke(task));
        } catch (NoSuchMethodException oldVersion) {
            // Baseline has no reservation; native tick still builds the duplicate.
        }
    }

    private static void discard(RouteFinderSearchTask task) throws Exception {
        try {
            Method method = RouteFinderSearchTask.class.getDeclaredMethod("discardQueued");
            method.setAccessible(true);
            method.invoke(task);
        } catch (NoSuchMethodException oldVersion) {
            // No ownership was retained by baseline.
        }
    }
}
