package cn.nukkit.command.defaults;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.utils.CommandLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WhitelistCommandProfileIoTest {

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "remove"})
    void nameMembershipDoesNotLoadProfile(String action) {
        Fixture f = new Fixture(action, true);
        // Reaching this path would also wait for pending saves of an offline player.
        when(f.server.getOfflinePlayer(anyString())).thenThrow(new AssertionError("profile IO on command thread"));

        assertEquals(1, f.execute());

        if (action.equals("add")) verify(f.server).addWhitelist("MiXeD Name");
        else verify(f.server).removeWhitelist("MiXeD Name");
        verify(f.server, never()).getOfflinePlayer(anyString());
        verify(f.log).addSuccess("commands.allowlist." + action + ".success", "MiXeD Name");
        verify(f.log).output(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "remove"})
    void permissionDenialStillPrecedesEveryMutation(String action) {
        Fixture f = new Fixture(action, false);

        assertEquals(0, f.execute());

        verifyNoInteractions(f.server);
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final CommandSender sender = mock(CommandSender.class);
        final CommandLogger log = mock(CommandLogger.class, RETURNS_SELF);
        final ParamList arguments = mock(ParamList.class);

        Fixture(String action, boolean allowed) {
            when(sender.getServer()).thenReturn(server);
            when(sender.hasPermission("nukkit.command.allowlist." + action)).thenReturn(allowed);
            when(arguments.getResult(0)).thenReturn(action);
            when(arguments.getResult(1)).thenReturn("MiXeD Name");
        }

        int execute() {
            return new WhitelistCommand("whitelist").execute(sender, "allowlist",
                    Map.entry("2args", arguments), log);
        }
    }
}
