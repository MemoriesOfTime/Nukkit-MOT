package cn.nukkit.command.tree.node;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.exceptions.SelectorSyntaxException;
import cn.nukkit.command.selector.EntitySelectorAPI;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.tree.ParamTree;
import cn.nukkit.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NameOnlyPlayerTargetsNodeTest {
    @BeforeAll static void initialize() { MockServer.init(); }

    @Test void literalRetainsExactNameWithoutResolvingOfflineProfile() {
        Fixture f = new Fixture();
        try (var global = mockStatic(Server.class)) {
            global.when(Server::getInstance).thenReturn(f.server);
            f.fill("MiXeD Name");
            var target = f.targets().get(0);
            assertEquals("MiXeD Name", target.getName());
            assertFalse(target.isOnline());
            target.setOp(true);
            verify(f.server).addOp("mixed name");
            verify(f.server, never()).getOfflinePlayer(anyString());
        }
    }

    @Test void onlineLiteralKeepsPlayerPermissionAndPacketEffects() {
        Fixture f = new Fixture();
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Canonical Name");
        when(player.isOnline()).thenReturn(true);
        when(player.getPlayer()).thenReturn(player);
        when(f.server.getPlayerExact("canonical name")).thenReturn(player);
        try (var global = mockStatic(Server.class)) {
            global.when(Server::getInstance).thenReturn(f.server);
            f.fill("canonical name");
            var target = f.targets().get(0);
            assertEquals("Canonical Name", target.getName());
            assertTrue(target.isOnline());
            assertSame(player, target.getPlayer());
            target.setOp(true);
            verify(player).setOp(true);
            verify(f.server, never()).addOp(anyString());
            verify(f.server, never()).getOfflinePlayer(anyString());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unicodeCaseExpansionCannotChangeTheOnlineOperatorTarget(boolean grant) {
        Fixture f = new Fixture();
        String literal = "İİİ";
        String normalized = literal.toLowerCase(Locale.ROOT);
        assertTrue("iii".equalsIgnoreCase(literal), "raw lookup would incorrectly match the ASCII player");
        assertFalse("iii".equalsIgnoreCase(normalized), "ROOT normalization expands dotted I before matching");
        Player ascii = mock(Player.class);
        when(ascii.getName()).thenReturn("iii");
        when(f.server.getOnlinePlayers()).thenReturn(Map.of(new UUID(1, 2), ascii));
        when(f.server.getPlayerExact(anyString())).thenCallRealMethod();
        when(f.server.isOp(normalized)).thenReturn(!grant);
        try (var global = mockStatic(Server.class)) {
            global.when(Server::getInstance).thenReturn(f.server);
            f.fill(literal);
            var target = f.targets().get(0);
            assertEquals(literal, target.getName());
            assertNull(target.resolved());
            target.setOp(grant);
            verify(f.server).getPlayerExact(normalized);
            verify(f.server, never()).getPlayerExact(literal);
            verify(ascii, never()).setOp(anyBoolean());
            if (grant) verify(f.server).addOp(normalized);
            else verify(f.server).removeOp(normalized);
            verify(f.server, never()).getOfflinePlayer(anyString());
        }
    }

    @Test void selectorsRetainPlayerObjectsAndFilterNonPlayers() throws Exception {
        Fixture f = new Fixture();
        Player player = mock(Player.class);
        Entity nonPlayer = mock(Entity.class);
        EntitySelectorAPI selectors = mock(EntitySelectorAPI.class);
        when(selectors.checkValid("@a")).thenReturn(true);
        when(selectors.matchEntities(f.sender, "@a")).thenReturn(List.of(nonPlayer, player));
        try (var global = mockStatic(Server.class); var api = mockStatic(EntitySelectorAPI.class)) {
            global.when(Server::getInstance).thenReturn(f.server);
            api.when(EntitySelectorAPI::getAPI).thenReturn(selectors);
            f.fill("@a");
            assertEquals(1, f.targets().size());
            f.targets().get(0).setOp(false);
            verify(player).setOp(false);
            verify(f.server, never()).getOfflinePlayer(anyString());
        }
    }

    @Test void selectorFailureKeepsItsOriginalErrorAndDoesNotFallBackToName() throws Exception {
        Fixture f = new Fixture();
        EntitySelectorAPI selectors = mock(EntitySelectorAPI.class);
        when(selectors.checkValid("@a[bad]")).thenReturn(true);
        when(selectors.matchEntities(f.sender, "@a[bad]")).thenThrow(new SelectorSyntaxException("selector failed"));
        try (var global = mockStatic(Server.class); var api = mockStatic(EntitySelectorAPI.class)) {
            global.when(Server::getInstance).thenReturn(f.server);
            api.when(EntitySelectorAPI::getAPI).thenReturn(selectors);
            f.fill("@a[bad]");
            assertFalse(f.node.hasResult());
            assertNotEquals(Integer.MIN_VALUE, f.list.getError());
            assertEquals("selector failed", f.list.getMessageContainer().getMessages().get(0).getMessageId());
            verifyNoInteractions(f.server);
        }
    }

    @Test void resetDoesNotReusePreviousNameAfterBlankInput() {
        Fixture f = new Fixture();
        try (var global = mockStatic(Server.class)) {
            global.when(Server::getInstance).thenReturn(f.server);
            f.fill("Earlier");
            assertTrue(f.node.hasResult());
            f.list.reset();
            f.fill("   ");
            assertFalse(f.node.hasResult());
            assertNotEquals(Integer.MIN_VALUE, f.list.getError());
        }
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final CommandSender sender = mock(CommandSender.class);
        final ParamList list;
        final NameOnlyPlayerTargetsNode node = new NameOnlyPlayerTargetsNode();

        Fixture() {
            ParamTree tree = mock(ParamTree.class);
            when(tree.getSender()).thenReturn(sender);
            list = new ParamList(tree);
            list.add(node.init(list, "player", false, CommandParamType.TARGET, null, null));
        }

        void fill(String name) {
            list.getIndexAndIncrement();
            node.fill(name);
        }

        List<NameOnlyPlayerTargetsNode.Target> targets() { return node.get(); }
    }
}
