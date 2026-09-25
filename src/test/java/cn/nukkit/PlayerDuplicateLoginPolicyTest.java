package cn.nukkit;

import cn.nukkit.entity.EntityHuman;
import cn.nukkit.permission.BanList;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * keep-existing-session-on-duplicate-login: the player who is already in the world keeps playing
 * and the newcomer is the one that is refused, for an equal name and for an equal identity alike.
 */
class PlayerDuplicateLoginPolicyTest {

    private static final String REFUSED = "disconnectionScreen.loggedinOtherLocation";

    @Test
    void anEqualNameIsRefusedWhileTheServerKeepsTheExistingSession() throws Exception {
        UUID playing = UUID.randomUUID();
        Server server = server(true, existing("Steve", playing));
        AtomicReference<String> reason = new AtomicReference<>();
        Player newcomer = newcomer(server, "steve", UUID.randomUUID(), reason);

        newcomer.processLogin();

        assertEquals(REFUSED, reason.get(), "the newcomer is refused");
        verify(onlyPlayer(server), never()).close(anyString(), anyString());
    }

    @Test
    void anEqualIdentityIsRefusedWhileTheServerKeepsTheExistingSession() throws Exception {
        UUID identity = UUID.randomUUID();
        Server server = server(true, existing("Alex", identity));
        AtomicReference<String> reason = new AtomicReference<>();
        Player newcomer = newcomer(server, "Steve", identity, reason);

        newcomer.processLogin();

        assertEquals(REFUSED, reason.get(), "the newcomer is refused");
        verify(onlyPlayer(server), never()).close(anyString(), anyString());
        verify(server.getLogger()).warning(contains("Refused the login of Steve"));
    }

    @Test
    void theHistoricalBehaviourStillClosesTheOlderSessionFirst() throws Exception {
        UUID playing = UUID.randomUUID();
        Server server = server(false, existing("Steve", playing));
        AtomicReference<String> reason = new AtomicReference<>();
        Player newcomer = newcomer(server, "steve", UUID.randomUUID(), reason);

        // The login goes on to load player data after the check; only the eviction matters here.
        try {
            newcomer.processLogin();
        } catch (RuntimeException ignored) {
        }

        verify(onlyPlayer(server)).close("", REFUSED);
        assertNotEquals(REFUSED, reason.get(), "the newcomer is not refused by default");
    }

    private static Player existing(String name, UUID identity) {
        Player player = mock(Player.class);
        player.username = name;
        when(player.getUniqueId()).thenReturn(identity);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static Server server(boolean keepExisting, Player existing) throws Exception {
        Server server = mock(Server.class);
        when(server.isDuplicateLoginKeepingExistingSession()).thenReturn(keepExisting);
        when(server.isWhitelisted(anyString())).thenReturn(true);
        BanList bans = mock(BanList.class);
        when(server.getNameBans()).thenReturn(bans);
        when(server.getIPBans()).thenReturn(bans);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        Map<UUID, Player> players = new HashMap<>();
        players.put(existing.getUniqueId(), existing);
        Field field = Server.class.getDeclaredField("playerList");
        field.setAccessible(true);
        field.set(server, players);
        return server;
    }

    private static Player onlyPlayer(Server server) {
        return server.playerList.values().iterator().next();
    }

    private static Player newcomer(Server server, String name, UUID identity, AtomicReference<String> reason) throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        player.username = name;
        Field uuid = EntityHuman.class.getDeclaredField("uuid");
        uuid.setAccessible(true);
        uuid.set(player, identity);
        Field serverField = cn.nukkit.entity.Entity.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(player, server);
        doReturn("127.0.0.1").when(player).getAddress();
        doAnswer(invocation -> {
            reason.set(invocation.getArgument(1));
            return null;
        }).when(player).close(anyString(), anyString());
        return player;
    }
}
