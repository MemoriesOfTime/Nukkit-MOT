package cn.nukkit.entity.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityBoatPassengerPositionTest {
    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothPlayersFollowTheBoatAcrossChunks(boolean sendLinks) {
        EntityBoat boat = boat();
        Player driver = passenger(Player.class, 1);
        Player rider = passenger(Player.class, 2);
        boat.getPassengers().add(driver);
        boat.getPassengers().add(rider);

        boat.updatePassengers(sendLinks);
        boat.x = 56.5;
        boat.z = -40.5;
        boat.updatePassengers(sendLinks);

        assertFollows(boat, driver, 2);
        assertFollows(boat, rider, 2);
        assertSame(driver, boat.getPassengers().get(0));
        assertEquals(EntityBoat.RIDER_PLAYER_OFFSET.add(EntityBoat.RIDER_PASSENGER_OFFSET),
                driver.getSeatPosition());
        assertEquals(EntityBoat.RIDER_PLAYER_OFFSET.add(EntityBoat.PASSENGER_OFFSET),
                rider.getSeatPosition());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void playerBecomesDriverAndBothPassengersFollow(boolean sendLinks) {
        EntityBoat boat = boat();
        Entity mob = passenger(Entity.class, 2);
        Player driver = passenger(Player.class, 1);
        boat.getPassengers().add(mob);
        boat.getPassengers().add(driver);

        boat.updatePassengers(sendLinks);

        assertSame(driver, boat.getPassengers().get(0));
        assertSame(mob, boat.getPassengers().get(1));
        assertFollows(boat, driver, 1);
        assertFollows(boat, mob, 1);
        assertEquals(EntityBoat.RIDER_OFFSET.add(EntityBoat.PASSENGER_OFFSET), mob.getSeatPosition());
        verify(mob).setRotation(boat.yaw + 90, mob.pitch);
    }

    @Test
    void bothNonPlayerPassengersFollow() {
        EntityBoat boat = boat();
        Entity first = passenger(Entity.class, 1);
        Entity second = passenger(Entity.class, 2);
        boat.getPassengers().add(first);
        boat.getPassengers().add(second);

        boat.updatePassengers();

        assertFollows(boat, first, 1);
        assertFollows(boat, second, 1);
        assertSame(first, boat.getPassengers().get(0));
    }

    @Test
    void singlePlayerStillFollowsWithoutTwoPassengerOffset() {
        EntityBoat boat = boat();
        Player driver = passenger(Player.class, 1);
        boat.getPassengers().add(driver);

        boat.updatePassengers();

        assertFollows(boat, driver, 1);
        assertEquals(EntityBoat.RIDER_PLAYER_OFFSET, driver.getSeatPosition());
    }

    private static void assertFollows(EntityBoat boat, Entity rider, int updates) {
        ArgumentCaptor<Vector3> positions = ArgumentCaptor.forClass(Vector3.class);
        verify(rider, times(updates)).setPosition(positions.capture());
        Vector3 last = positions.getAllValues().get(updates - 1);
        Vector3 expected = boat.add(rider.getSeatPosition().asVector3());
        assertEquals(expected.x, last.x, 0.00001);
        assertEquals(expected.y, last.y, 0.00001);
        assertEquals(expected.z, last.z, 0.00001);
        assertEquals(boat.getFloorX() >> 4, last.getFloorX() >> 4);
        assertEquals(boat.getFloorZ() >> 4, last.getFloorZ() >> 4);
    }

    private static <T extends Entity> T passenger(Class<T> type, long id) {
        T passenger = mock(type);
        when(passenger.isAlive()).thenReturn(true);
        lenient().when(passenger.getId()).thenReturn(id);
        AtomicReference<Vector3f> seat = new AtomicReference<>(new Vector3f());
        doAnswer(call -> {
            seat.set(call.getArgument(0));
            return null;
        }).when(passenger).setSeatPosition(any(Vector3f.class));
        when(passenger.getSeatPosition()).thenAnswer(call -> seat.get());
        return passenger;
    }

    private static EntityBoat boat() {
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(MockServer.get());
        lenient().when(level.getMinBlockY()).thenReturn(-64);
        lenient().when(level.getMaxBlockY()).thenReturn(319);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        lenient().when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        lenient().when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        level.isBeingConverted = true;
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 8.5)).add(new DoubleTag("", 64.0)).add(new DoubleTag("", 8.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0)).add(new DoubleTag("", 0)).add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0)).add(new FloatTag("", 0)));
        return new EntityBoat(chunk, nbt);
    }
}
