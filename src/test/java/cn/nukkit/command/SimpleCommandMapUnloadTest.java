package cn.nukkit.command;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.command.defaults.SayCommand;
import cn.nukkit.command.defaults.VanillaCommand;
import cn.nukkit.command.simple.SimpleCommand;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.scoreboard.manager.IScoreboardManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：插件卸载时的命令清理与原版命令恢复。<br>
 * Regression tests for command cleanup and vanilla-command restore on plugin unload.
 * <p>
 * 覆盖三项修复：被抢占原版命令的恢复、{@link SimpleCommand} 的插件归属、卸载时不误删非归属命令。
 * Covers: restore of displaced vanilla commands, {@link SimpleCommand} attribution,
 * and that {@code unregister(Plugin)} never removes commands it does not own.
 */
class SimpleCommandMapUnloadTest {

    private SimpleCommandMap map;
    private Server server;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        server = MockServer.get();
        // SimpleCommandMap 构造某些原版命令时读取计分板管理器，stub 为空集避免 NPE。
        // Some vanilla commands read the scoreboard manager during construction; stub it to avoid NPE.
        IScoreboardManager sm = Mockito.mock(IScoreboardManager.class);
        Mockito.lenient().when(sm.getScoreboards()).thenReturn(Collections.emptyMap());
        Mockito.when(server.getScoreboardManager()).thenReturn(sm);
        map = new SimpleCommandMap(server);
    }

    @Test
    void vanillaCommandIsRestoredAfterUnloadingDisplacingPlugin() throws Exception {
        // 原版 say 命令在 setDefaultCommands 中已注册 / vanilla `say` is registered by setDefaultCommands
        Command originalSay = map.getCommand("say");
        assertNotNull(originalSay);
        assertInstanceOf(SayCommand.class, originalSay);
        assertInstanceOf(VanillaCommand.class, originalSay);

        Plugin plugin = stubPlugin("OverridePlugin");
        // 插件注册同名命令 say，抢占原版 / plugin registers a colliding `say`, displacing vanilla
        Command pluginCmd = new PlainCommand("say", plugin);
        map.register("OverridePlugin", pluginCmd);

        assertSame(pluginCmd, map.getCommand("say"), "插件命令应覆盖原版 / plugin command should win");

        // 卸载插件应删除其命令并恢复原版 say / unload should drop the plugin command and restore vanilla say
        map.unregister(plugin);

        Command restored = map.getCommand("say");
        assertNotNull(restored, "卸载后原版 say 应被恢复 / vanilla say should be restored after unload");
        assertSame(originalSay, restored, "应恢复同一个原版实例 / the same vanilla instance should be restored");
        assertNull(map.getCommand("OverridePlugin:say"),
                "插件前缀 key 也应被移除 / plugin-prefixed key should be removed too");
    }

    @Test
    void restoreIsSkippedWhenKeyIsAlreadyReclaimed() throws Exception {
        Plugin first = stubPlugin("FirstPlugin");
        Plugin second = stubPlugin("SecondPlugin");

        Command vanilla = map.getCommand("say");
        assertNotNull(vanilla);

        // FirstPlugin 抢占 say / FirstPlugin displaces say
        Command firstCmd = new PlainCommand("say", first);
        map.register("FirstPlugin", firstCmd);

        // 模拟"恢复前 key 已被重新占用"：手动把另一个非原版命令放回 say key。
        // Simulate "key reclaimed before restore": place a different non-vanilla command at the `say` key.
        PlainCommand reclaim = new PlainCommand("say", second);
        map.getCommands().put("say", reclaim);

        map.unregister(first);
        // 恢复逻辑应检测到 key 已被占用而跳过，保留 reclaim，不覆盖。
        // Restore must detect the occupied key and skip, leaving reclaim in place.
        assertSame(reclaim, map.getCommand("say"),
                "已被重新占用的 key 不应被原版恢复覆盖 / an already-reclaimed key must not be overwritten by restore");
        assertNull(map.getCommand("FirstPlugin:say"),
                "FirstPlugin 的前缀 key 仍应被清理 / FirstPlugin's prefixed key should still be cleared");
    }

    @Test
    void unregisterNeverTouchesCommandsNotOwnedByPlugin() {
        Plugin plugin = stubPlugin("HarmlessPlugin");
        // 不注册任何命令即卸载，所有原版命令必须原封不动
        // Unload without ever registering: every vanilla command must be untouched
        int before = map.getCommands().size();
        map.unregister(plugin);
        assertEquals(before, map.getCommands().size(),
                "未注册命令的插件卸载不应改变已知命令表 / unloading a plugin that registered nothing must not change knownCommands");
        // 确认 ownsCommand 对原版命令返回 false / confirm ownsCommand is false for vanilla commands
        for (Command command : map.getCommands().values()) {
            assertFalse(command instanceof PluginIdentifiableCommand,
                    "原版命令不应被归属到任何插件 / vanilla commands must not be attributed to a plugin");
        }
    }

    @Test
    void simpleCommandIsAttributedAndCleanedUpWhenObjectIsPlugin() throws Exception {
        Plugin plugin = stubPlugin("AnnotatedPlugin");

        // 用反射构造一个 SimpleCommand，模拟 registerSimpleCommands(plugin) 的产物。
        // Build a SimpleCommand via reflection, mirroring what registerSimpleCommands(plugin) produces.
        Method target = SimpleCommandMapUnloadTest.class.getDeclaredMethod(
                "simpleCommandTarget", CommandSender.class, String.class, String[].class);
        SimpleCommand sc = new SimpleCommand(plugin, target, plugin, "mysimplecmd", "desc", "/mysimplecmd", new String[0]);
        map.register("AnnotatedPlugin", sc);

        assertSame(sc, map.getCommand("mysimplecmd"));
        assertSame(plugin, sc.getPlugin(), "SimpleCommand 应记录所属插件 / SimpleCommand must record its owning plugin");

        map.unregister(plugin);
        assertNull(map.getCommand("mysimplecmd"),
                "SimpleCommand 应随插件一起注销 / SimpleCommand must be unregistered with its plugin");
        assertNull(map.getCommand("AnnotatedPlugin:mysimplecmd"));
    }

    @Test
    void clearCommandsWipesDisplacementTracking() {
        Plugin plugin = stubPlugin("WipePlugin");
        map.register("WipePlugin", new PlainCommand("say", plugin));
        // clearCommands 应清空跟踪表，使后续重建不会携带陈旧恢复记录
        // clearCommands must drop tracking so a later rebuild carries no stale restore records
        map.clearCommands();
        // 重建后 say 重新来自 setDefaultCommands / after rebuild say comes fresh from setDefaultCommands
        Command say = map.getCommand("say");
        assertNotNull(say);
        assertInstanceOf(VanillaCommand.class, say);
        // 此时再卸载一个从未注册的插件，不应抛异常或改变状态
        // Unloading an unrelated plugin now must be a no-op and not throw
        Plugin other = stubPlugin("OtherPlugin");
        assertDoesNotThrow(() -> map.unregister(other));
    }

    /**
     * 测试用 SimpleCommand 目标方法，签名必须与 SimpleCommand 反射调用约定一致。<br>
     * Target method for the test SimpleCommand; signature must match SimpleCommand's reflective call.
     */
    @SuppressWarnings("unused")
    public static boolean simpleCommandTarget(CommandSender sender, String label, String[] args) {
        return true;
    }

    /**
     * 构造一个 mock 插件，并在 mock PluginManager 上登记 name → plugin。<br>
     * Builds a mock plugin and registers name → plugin on the mock PluginManager.
     * <p>
     * {@code SimpleCommandMap.register} 解析归属时调用 {@code server.getPluginManager().getPlugin(fallbackPrefix)}，
     * 因此必须 stub 该方法才能让 displaced 跟踪生效。
     */
    private Plugin stubPlugin(String name) {
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.lenient().when(server.getPluginManager().getPlugin(name)).thenReturn(plugin);
        return plugin;
    }

    /** 简易 PluginIdentifiableCommand 实现，用于抢占测试。 / Minimal PluginIdentifiableCommand for displacement tests. */
    private static final class PlainCommand extends Command implements PluginIdentifiableCommand {
        private final Plugin owner;

        PlainCommand(String name, Plugin owner) {
            super(name);
            this.owner = owner;
        }

        @Override
        public Plugin getPlugin() {
            return owner;
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return true;
        }
    }
}
