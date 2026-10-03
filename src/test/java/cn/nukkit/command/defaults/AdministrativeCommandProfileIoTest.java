package cn.nukkit.command.defaults;

import cn.nukkit.IPlayer;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.utils.CommandLogger;
import cn.nukkit.permission.BanList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdministrativeCommandProfileIoTest {
    private static final String NAME = "MiXeD Name";

    @BeforeAll static void initialize() { MockServer.init(); }

    @ParameterizedTest
    @ValueSource(strings = {"op", "deop", "pardon"})
    void enabledCommandsUseLiteralNameAndPreserveMessages(String commandName) {
        Fixture f = new Fixture(commandName, true);
        when(f.server.getOfflinePlayer(anyString())).thenThrow(new AssertionError("profile IO on command thread"));

        assertEquals(1, f.execute());

        f.verifyNameMutation();
        verify(f.server, never()).getOfflinePlayer(anyString());
        verify(f.log).addSuccess("commands." + (commandName.equals("pardon") ? "unban" : commandName) + ".success", NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"op", "deop", "pardon"})
    void disabledCommandsKeepLegacyProfileResolution(String commandName) {
        Fixture f = new Fixture(commandName, false);
        assertEquals(1, f.execute());
        verify(f.server).getOfflinePlayer(NAME);
        if (commandName.equals("pardon")) verify(f.bans).remove(NAME);
        else verify(f.offline).setOp(commandName.equals("op"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"op", "deop"})
    void onlinePermissionChangesStillCallPlayerSetOpAndWhisper(String commandName) {
        Fixture f = new Fixture(commandName, true);
        Player online = mock(Player.class);
        when(online.getName()).thenReturn(NAME);
        when(online.isOp()).thenReturn(commandName.equals("deop"));
        when(online.isOnline()).thenReturn(true);
        when(online.getPlayer()).thenReturn(online);
        when(f.server.getPlayerExact(NAME.toLowerCase(java.util.Locale.ROOT))).thenReturn(online);

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
        Fixture f = new Fixture(commandName, true);
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

    /** Synthetic command-thread IO benchmark; does not claim whole-server tick timings. */
    @ParameterizedTest
    @ValueSource(strings = {"whitelist", "op", "deop", "pardon"})
    @EnabledIfEnvironmentVariable(named = "NUKKIT_COMMAND_IO_BENCH", matches = "1")
    void controlledProfileReadDelayBeforeAfter(String commandName) throws Exception {
        final int samples = 5;
        final long delayMillis = 200;
        for (boolean enabled : new boolean[]{false, true}) {
            Fixture f = new Fixture(commandName, enabled);
            AtomicInteger profileReads = new AtomicInteger(), mutations = new AtomicInteger();
            when(f.server.getOfflinePlayer(NAME)).thenAnswer(call -> {
                profileReads.incrementAndGet();
                Thread.sleep(delayMillis);
                return f.offline;
            });
            doAnswer(call -> { mutations.incrementAndGet(); return null; }).when(f.offline).setOp(anyBoolean());
            doAnswer(call -> { mutations.incrementAndGet(); return null; }).when(f.offline).setWhitelisted(anyBoolean());
            doAnswer(call -> { mutations.incrementAndGet(); return null; }).when(f.server).addOp(anyString());
            doAnswer(call -> { mutations.incrementAndGet(); return null; }).when(f.server).removeOp(anyString());
            doAnswer(call -> { mutations.incrementAndGet(); return null; }).when(f.server).addWhitelist(anyString());
            doAnswer(call -> { mutations.incrementAndGet(); return null; }).when(f.bans).remove(anyString());
            long[] nanos = new long[samples];
            // Install the mock outside timing: only command parsing and execution are measured.
            try (var global = mockStatic(Server.class)) {
                global.when(Server::getInstance).thenReturn(f.server);
                for (int i = 0; i < samples; i++) {
                    long start = System.nanoTime();
                    assertEquals(1, f.parseAndExecute());
                    nanos[i] = System.nanoTime() - start;
                }
            }
            assertEquals(enabled ? 0 : samples, profileReads.get());
            assertEquals(samples, mutations.get(), "the same membership mutation must complete in both modes");
            Arrays.sort(nanos);
            System.out.printf(Locale.ROOT,
                    "COMMAND_PROFILE_IO command=%s async=%s readDelayMs=%d samples=%d profileReads=%d mutations=%d maxMs=%.3f p99Ms=%.3f%n",
                    commandName, enabled, delayMillis, samples, profileReads.get(), mutations.get(),
                    nanos[samples - 1] / 1_000_000.0, nanos[(int) Math.ceil(samples * 0.99) - 1] / 1_000_000.0);
        }
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final CommandSender sender = mock(CommandSender.class);
        final CommandLogger log = mock(CommandLogger.class, RETURNS_SELF);
        final IPlayer offline = mock(IPlayer.class);
        final BanList bans = mock(BanList.class);
        final VanillaCommand command;
        final String name;

        Fixture(String name, boolean enabled) {
            this.name = name;
            server.asyncProfileIo = enabled;
            command = switch (name) {
                case "op" -> new OpCommand(name);
                case "deop" -> new DeopCommand(name);
                case "pardon" -> new PardonCommand(name);
                default -> new WhitelistCommand(name);
            };
            when(sender.getServer()).thenReturn(server);
            when(sender.hasPermission(anyString())).thenReturn(true);
            when(server.getOfflinePlayer(NAME)).thenReturn(offline);
            when(offline.getName()).thenReturn(NAME);
            when(offline.isOp()).thenReturn(name.equals("deop"));
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
