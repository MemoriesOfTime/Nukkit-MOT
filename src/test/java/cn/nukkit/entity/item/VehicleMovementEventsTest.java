package cn.nukkit.entity.item;

import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.vehicle.VehicleMoveEvent;
import cn.nukkit.event.vehicle.VehicleUpdateEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleMovementEventsTest {
    private final EntityVehicle vehicle = mock(EntityVehicle.class, CALLS_REAL_METHODS);
    private final RecordingManager manager = new RecordingManager();
    private final PluginBase plugin = new PluginBase() {};
    private final List<Runnable> cleanup = new ArrayList<>();
    private final Level originalLevel = mock(Level.class);

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(manager);
        when(vehicle.getServer()).thenReturn(server);
        plugin.setEnabled(true);
        vehicle.lastX = 1;
        vehicle.lastY = 2;
        vehicle.lastZ = 3;
        vehicle.lastYaw = 4;
        vehicle.lastPitch = 5;
        vehicle.x = 6;
        vehicle.y = 7;
        vehicle.z = 8;
        vehicle.yaw = 9;
        vehicle.pitch = 10;
        vehicle.level = originalLevel;
    }

    @AfterEach
    void unregisterOwnListeners() {
        cleanup.forEach(Runnable::run);
    }

    @Test
    void noListenersMeansNoEventReachesPluginManager() {
        vehicle.dispatchVehicleMovementEvents();
        assertTrue(manager.events.isEmpty());
    }

    @Test
    void updateListenerRunsOnceEvenWithoutMovementListener() {
        List<EntityVehicle> updates = new ArrayList<>();
        listen(VehicleUpdateEvent.getHandlers(), event ->
                updates.add(((VehicleUpdateEvent) event).getVehicle()));
        vehicle.dispatchVehicleMovementEvents();
        assertEquals(List.of(vehicle), updates);
        assertEquals(1, manager.events.size());
        assertInstanceOf(VehicleUpdateEvent.class, manager.events.get(0));
    }

    @Test
    void movementListenerGetsOriginalCoordinatesAndRotations() {
        List<VehicleMoveEvent> moves = new ArrayList<>();
        listen(VehicleMoveEvent.getHandlers(), event -> moves.add((VehicleMoveEvent) event));
        vehicle.dispatchVehicleMovementEvents();
        assertEquals(1, moves.size());
        assertSame(vehicle, moves.get(0).getVehicle());
        assertLocation(moves.get(0).getFrom(), 1, 2, 3, 4, 5, originalLevel);
        assertLocation(moves.get(0).getTo(), 6, 7, 8, 9, 10, originalLevel);
    }

    @Test
    void updateMutationCannotRewriteTheMovementSnapshot() {
        Level changedLevel = mock(Level.class);
        listen(VehicleUpdateEvent.getHandlers(), event -> {
            vehicle.lastX = vehicle.lastY = vehicle.lastZ = 90;
            vehicle.lastYaw = vehicle.lastPitch = 91;
            vehicle.x = vehicle.y = vehicle.z = 92;
            vehicle.yaw = vehicle.pitch = 93;
            vehicle.level = changedLevel;
        });
        List<VehicleMoveEvent> moves = new ArrayList<>();
        listen(VehicleMoveEvent.getHandlers(), event -> moves.add((VehicleMoveEvent) event));
        vehicle.dispatchVehicleMovementEvents();
        assertEquals(2, manager.events.size());
        assertInstanceOf(VehicleUpdateEvent.class, manager.events.get(0));
        assertSame(moves.get(0), manager.events.get(1));
        assertLocation(moves.get(0).getFrom(), 1, 2, 3, 4, 5, originalLevel);
        assertLocation(moves.get(0).getTo(), 6, 7, 8, 9, 10, originalLevel);
    }

    @Test
    void listenerRegisteredDuringUpdateReceivesCurrentMove() {
        List<VehicleMoveEvent> moves = new ArrayList<>();
        listen(VehicleUpdateEvent.getHandlers(), event ->
                listen(VehicleMoveEvent.getHandlers(), move -> moves.add((VehicleMoveEvent) move)));
        vehicle.dispatchVehicleMovementEvents();
        assertEquals(1, moves.size());
        assertLocation(moves.get(0).getTo(), 6, 7, 8, 9, 10, originalLevel);
    }

    @Test
    void listenerRemovedDuringUpdateDoesNotCauseMoveDispatch() {
        RegisteredListener move = listen(VehicleMoveEvent.getHandlers(), event -> fail("Unregistered move listener"));
        listen(VehicleUpdateEvent.getHandlers(), event -> VehicleMoveEvent.getHandlers().unregister(move));
        vehicle.dispatchVehicleMovementEvents();
        assertEquals(1, manager.events.size());
        assertInstanceOf(VehicleUpdateEvent.class, manager.events.get(0));
    }

    @Test
    void onlyRotationChangeDoesNotBecomeAMove() {
        listen(VehicleMoveEvent.getHandlers(), event -> fail("Rotation alone was not a move"));
        vehicle.x = vehicle.lastX;
        vehicle.y = vehicle.lastY;
        vehicle.z = vehicle.lastZ;
        vehicle.dispatchVehicleMovementEvents();
        assertTrue(manager.events.isEmpty());
    }

    @Test
    void comparisonPreservesSignedZeroAndNaNSemantics() {
        List<VehicleMoveEvent> moves = new ArrayList<>();
        listen(VehicleMoveEvent.getHandlers(), event -> moves.add((VehicleMoveEvent) event));
        vehicle.x = 0.0;
        vehicle.lastX = -0.0;
        vehicle.y = vehicle.lastY;
        vehicle.z = vehicle.lastZ;
        vehicle.dispatchVehicleMovementEvents();
        assertTrue(moves.isEmpty());
        vehicle.x = vehicle.lastX = Double.NaN;
        vehicle.dispatchVehicleMovementEvents();
        assertEquals(1, moves.size());
    }

    private RegisteredListener listen(HandlerList handlers, Consumer<Event> action) {
        RegisteredListener listener = new RegisteredListener(new Listener() {},
                (ignored, event) -> action.accept(event), EventPriority.NORMAL, plugin, false);
        handlers.register(listener);
        cleanup.add(() -> handlers.unregister(listener));
        return listener;
    }

    private static void assertLocation(Location actual, double x, double y, double z,
                                       double yaw, double pitch, Level level) {
        assertEquals(x, actual.x);
        assertEquals(y, actual.y);
        assertEquals(z, actual.z);
        assertEquals(yaw, actual.yaw);
        assertEquals(pitch, actual.pitch);
        assertSame(level, actual.level);
    }

    private static final class RecordingManager extends PluginManager {
        private final List<Event> events = new ArrayList<>();

        private RecordingManager() {
            super(null, null);
        }

        @Override
        public void callEvent(Event event) {
            events.add(event);
            super.callEvent(event);
        }
    }
}
