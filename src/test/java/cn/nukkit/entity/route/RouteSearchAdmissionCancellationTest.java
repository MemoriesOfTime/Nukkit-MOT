package cn.nukkit.entity.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteSearchAdmissionCancellationTest {
    @Test
    void cancellationImmediatelyAfterOwnerPublicationCannotMissAdmission() {
        RouteFinder route = mock(RouteFinder.class, CALLS_REAL_METHODS);
        doAnswer(call -> {
            boolean reserved = (boolean) call.callRealMethod();
            if (reserved) {
                route.cancelQueuedSearch();
            }
            return reserved;
        }).when(route).reserveSearch(any());
        RouteFinderSearchTask task = new RouteFinderSearchTask(route);

        assertFalse(task.tryQueue(), "cancellation must reject the unpublished queue request");
        assertFalse(route.hasPendingSearch());
        task.run();
        verify(route, never()).research();
    }

    @Test
    void closeWinningBeforeOwnerPublicationCannotLeaveAQueuedOwner() {
        RouteFinder route = mock(RouteFinder.class, CALLS_REAL_METHODS);
        doReturn(true).when(route).isSearchClosed();
        RouteFinderSearchTask task = new RouteFinderSearchTask(route);

        assertFalse(task.tryQueue(), "a closed finder cannot acquire a new queued request");
        assertFalse(route.hasPendingSearch());
        task.run();
        verify(route, never()).research();
    }

    @Test
    void failedReservationDoesNotPreventTheSameRequestFromBeingRetried() {
        RouteFinder route = mock(RouteFinder.class, CALLS_REAL_METHODS);
        RouteFinderSearchTask owner = new RouteFinderSearchTask(route);
        RouteFinderSearchTask waiting = new RouteFinderSearchTask(route);

        assertTrue(owner.tryQueue());
        assertFalse(waiting.tryQueue());
        owner.discardQueued();
        assertTrue(waiting.tryQueue());
        assertTrue(route.hasPendingSearch());
        waiting.discardQueued();
        assertFalse(route.hasPendingSearch());
    }
}
