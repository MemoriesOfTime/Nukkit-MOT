package cn.nukkit.command.defaults;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.utils.CommandLogger;
import cn.nukkit.permission.BanList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdministrativeCommandProfileIoTest {
    private static final String NAME = "MiXeD Name";

    @BeforeAll static void initialize() { MockServer.init(); }

    @ParameterizedTest
    @ValueSource(strings = {"op", "deop", "pardon"})
    void commandsUseLiteralNameAndPreserveMessages(String commandName) {
        Fixture f = new Fixture(commandName);
        when(f.server.getOfflinePlayer(anyString())).thenThrow(new AssertionError("profile IO on command thread"));

        assertEquals(1, f.execute());

        f.verifyNameMutation();
        verify(f.server, never()).getOfflinePlayer(anyString());
        verify(f.log).addSuccess("commands." + (commandName.equals("pardon") ? "unban" : commandName) + ".success", NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"op", "deop"})
    void onlinePermissionChangesStillCallPlayerSetOpAndWhisper(String commandName) {
        Fixture f = new Fixture(commandName);
        Player online = mock(Player.class);
        when(online.getName()).thenReturn(NAME);
        when(online.isOp()).thenReturn(commandName.equals("deop"));
        when(online.isOnline()).thenReturn(true);
        when(online.getPlayer()).thenReturn(online);
        when(f.server.getPlayerExact(NAME.toLowerCase(Locale.ROOT))).thenReturn(online);

        assertEquals(1, f.execute());

        verify(online).setOp(commandName.equals("op"));
        verify(f.log).outputObjectWhisper(eq(online), anyString());
        verify(f.server, never()).addOp(anyString());
        verify(f.server, never()).removeOp(anyString());
        verify(f.server, never()).getOfflinePlayer(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"op", "deop", "pardon"})
    void permissionAndClientParameterDefinitionsRemainUnchanged(String commandName) {
        Fixture f = new Fixture(commandName);
        String permission = switch (commandName) {
            case "op" -> "nukkit.command.op.give";
            case "deop" -> "nukkit.command.op.take";
            default -> "nukkit.command.unban.player";
        };
        assertEquals(permission, f.command.getPermission());
        when(f.sender.hasPermission(permission)).thenReturn(false);
        assertFalse(f.command.testPermissionSilent(f.sender));
        when(f.sender.hasPermission(permission)).thenReturn(true);
        assertTrue(f.command.testPermissionSilent(f.sender));
        var parameter = f.command.getCommandParameters("default")[0];
        assertEquals("player", parameter.name);
        assertEquals(CommandParamType.TARGET, parameter.type);
        assertFalse(parameter.optional);
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final CommandSender sender = mock(CommandSender.class);
        final CommandLogger log = mock(CommandLogger.class, RETURNS_SELF);
        final BanList bans = mock(BanList.class);
        final VanillaCommand command;
        final String name;

        Fixture(String name) {
            this.name = name;
            command = switch (name) {
                case "op" -> new OpCommand(name);
                case "deop" -> new DeopCommand(name);
                case "pardon" -> new PardonCommand(name);
                default -> new WhitelistCommand(name);
            };
            when(sender.getServer()).thenReturn(server);
            when(sender.hasPermission(anyString())).thenReturn(true);
            when(server.isOp(NAME.toLowerCase(Locale.ROOT))).thenReturn(name.equals("deop"));
            when(server.getNameBans()).thenReturn(bans);
        }

        int execute() {
            try (var global = mockStatic(Server.class)) {
                global.when(Server::getInstance).thenReturn(server);
                return parseAndExecute();
            }
        }

        int parseAndExecute() {
            String[] args = name.equals("whitelist") ? new String[]{"add", NAME} : new String[]{NAME};
            var parsed = command.getParamTree().matchAndParse(sender, name, args);
            assertNotNull(parsed);
            assertTrue(command.testPermissionSilent(sender));
            return command.execute(sender, name, parsed, log);
        }

        void verifyNameMutation() {
            switch (name) {
                case "op" -> verify(server).addOp(NAME.toLowerCase(Locale.ROOT));
                case "deop" -> verify(server).removeOp(NAME.toLowerCase(Locale.ROOT));
                default -> verify(bans).remove(NAME);
            }
        }
    }
}
