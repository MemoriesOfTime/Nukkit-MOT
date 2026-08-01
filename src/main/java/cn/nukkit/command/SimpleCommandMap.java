package cn.nukkit.command;

import cn.nukkit.Server;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.defaults.*;
import cn.nukkit.command.simple.*;
import cn.nukkit.command.utils.CommandLogger;
import cn.nukkit.lang.CommandOutputContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginClassLoader;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.utils.Utils;
import io.netty.util.internal.EmptyArrays;

import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class SimpleCommandMap implements CommandMap {

    protected final Map<String, Command> knownCommands = new HashMap<>();

    /**
     * 插件注册命令时被抢占的映射快照：插件 → (key → 被替换的命令)。<br>
     * Snapshot of command mappings displaced during plugin registration:
     * plugin → (key → displaced command). Replayed on {@link #unregister(Plugin)}.
     * <p>
     * 仅记录其他插件或原版命令，避免卸载时恢复同一插件自己的旧命令。<br>
     * Only mappings owned by other plugins or vanilla are retained, so unloading never restores
     * an older command owned by the same plugin.
     */
    private final Map<Plugin, Map<String, Command>> displacedCommandsByPlugin = new IdentityHashMap<>();

    private final Server server;

    public SimpleCommandMap(Server server) {
        this.server = server;
        this.setDefaultCommands();
    }

    private void setDefaultCommands() {
        this.register("nukkit", new VersionCommand("version"));
        this.register("nukkit", new PluginsCommand("plugins"));
        this.register("nukkit", new HelpCommand("help"));
        this.register("nukkit", new StopCommand("stop"));
        this.register("nukkit", new TellCommand("tell"));
        this.register("nukkit", new BanCommand("ban"));
        this.register("nukkit", new BanIpCommand("ban-ip"));
        this.register("nukkit", new BanListCommand("banlist"));
        this.register("nukkit", new PardonCommand("pardon"));
        this.register("nukkit", new PardonIpCommand("pardon-ip"));
        this.register("nukkit", new ListCommand("list"));
        this.register("nukkit", new KickCommand("kick"));
        this.register("nukkit", new OpCommand("op"));
        this.register("nukkit", new DeopCommand("deop"));
        this.register("nukkit", new SaveCommand("save"));
        this.register("nukkit", new GiveCommand("give"));
        this.register("nukkit", new EffectCommand("effect"));
        this.register("nukkit", new EnchantCommand("enchant"));
        this.register("nukkit", new FogCommand("fog"));
        this.register("nukkit", new GamemodeCommand("gamemode"));
        this.register("nukkit", new KillCommand("kill"));
        this.register("nukkit", new SetWorldSpawnCommand("setworldspawn"));
        this.register("nukkit", new TeleportCommand("tp"));
        this.register("nukkit", new TimeCommand("time"));
        this.register("nukkit", new ReloadCommand("reload"));
        this.register("nukkit", new WeatherCommand("weather"));
        this.register("nukkit", new XpCommand("xp"));
        this.register("nukkit", new StatusCommand("status"));
        this.register("nukkit", new SummonCommand("summon"));
        this.register("nukkit", new WorldCommand("world"));
        this.register("nukkit", new GenerateWorldCommand("genworld"));
        this.register("nukkit", new WhitelistCommand("whitelist"));
        this.register("nukkit", new GameruleCommand("gamerule"));
        this.register("nukkit", new SpawnCommand("spawn"));
        this.register("nukkit", new DefaultGamemodeCommand("defaultgamemode"));
        this.register("nukkit", new SayCommand("say"));
        this.register("nukkit", new MeCommand("me"));
        this.register("nukkit", new DifficultyCommand("difficulty"));
        this.register("nukkit", new ParticleCommand("particle"));
        this.register("nukkit", new SpawnpointCommand("spawnpoint"));
        this.register("nukkit", new TitleCommand("title"));
        this.register("nukkit", new TransferServerCommand("transfer"));
        this.register("nukkit", new SeedCommand("seed"));
        this.register("nukkit", new PlaySoundCommand("playsound"));
        this.register("nukkit", new StopSoundCommand("stopsound"));
        this.register("nukkit", new DebugPasteCommand("debugpaste"));
        this.register("nukkit", new GarbageCollectorCommand("gc"));
        this.register("nukkit", new ClearCommand("clear"));
        this.register("nukkit", new HudCommand("hud"));
        this.register("nukkit", new CameraShakeCommand("camerashake"));
        this.register("nukkit", new CameraCommand("camera"));
        this.register("nukkit", new TimingsCommand("timings"));
        this.register("nukkit", new ConvertCommand("convert"));
        this.register("nukkit", new InputPermissionCommand("inputpermission"));
        this.register("nukkit", new AbilityCommand("ability"));
        this.register("nukkit", new DamageCommand("damage"));
        this.register("nukkit", new DayLockCommand("daylock"));
        this.register("nukkit", new FillCommand("fill"));
        this.register("nukkit", new SetBlockCommand("setblock"));
        this.register("nukkit", new PlayAnimationCommand("playanimation"));
        this.register("nukkit", new ScoreboardCommand("scoreboard"));
        this.register("nukkit", new SetMaxPlayersCommand("setmaxplayers"));
        this.register("nukkit", new SpreadPlayersCommand("spreadplayers"));
        this.register("nukkit", new TagCommand("tag"));
        this.register("nukkit", new TellrawCommand("tellraw"));
        this.register("nukkit", new TitlerawCommand("titleraw"));
        this.register("nukkit", new TestForCommand("testfor"));
        this.register("nukkit", new TestForBlockCommand("testforblock"));
        this.register("nukkit", new TestForBlocksCommand("testforblocks"));
        this.register("nukkit", new ClearSpawnPointCommand("clearspawnpoint"));
        this.register("nukkit", new CloneCommand("clone"));
        this.register("nukkit", new ExecuteCommand("execute"));
        this.register("nukkit", new TickingAreaCommand("tickingarea"));
    }

    @Override
    public void registerAll(String fallbackPrefix, List<? extends Command> commands) {
        for (Command command : commands) {
            this.register(fallbackPrefix, command);
        }
    }

    @Override
    public boolean register(String fallbackPrefix, Command command) {
        return this.register(fallbackPrefix, command, null);
    }

    @Override
    public boolean register(String fallbackPrefix, Command command, String label) {
        if (label == null) {
            label = command.getName();
        }
        label = label.trim().toLowerCase(Locale.ROOT);
        fallbackPrefix = fallbackPrefix.trim().toLowerCase(Locale.ROOT);

        Plugin owner = resolveOwner(fallbackPrefix, command);
        Map<String, Command> displaced = owner == null ? null : new LinkedHashMap<>();

        boolean registered = this.registerAlias(command, false, fallbackPrefix, label, owner, displaced);

        List<String> aliases = new ArrayList<>(Arrays.asList(command.getAliases()));

        for (Iterator<String> iterator = aliases.iterator(); iterator.hasNext(); ) {
            String alias = iterator.next();
            if (!this.registerAlias(command, true, fallbackPrefix, alias, owner, displaced)) {
                iterator.remove();
            }
        }
        command.setAliases(aliases.toArray(new String[0]));

        if (!registered) {
            command.setLabel(fallbackPrefix + ':' + label);
        }

        command.register(this);

        // 仅当确实有映射被抢占时才登记，并保留该插件第一次替换的底层映射。
        // Track only actual displacement and retain the first underlying mapping replaced by this plugin.
        if (displaced != null && !displaced.isEmpty()) {
            Map<String, Command> retained = this.displacedCommandsByPlugin.computeIfAbsent(owner, k -> new LinkedHashMap<>());
            displaced.forEach(retained::putIfAbsent);
        }

        return registered;
    }

    /**
     * 解析命令所属插件：优先使用命令自带 owner，插件管理器已初始化时再按 fallbackPrefix 查找。<br>
     * Resolves command ownership from the command first, then by fallbackPrefix once PluginManager is available.
     */
    private Plugin resolveOwner(String fallbackPrefix, Command command) {
        if (command instanceof PluginIdentifiableCommand) {
            Plugin plugin = ((PluginIdentifiableCommand) command).getPlugin();
            if (plugin != null) {
                return plugin;
            }
        }
        var pluginManager = this.server.getPluginManager();
        return pluginManager == null ? null : pluginManager.getPlugin(fallbackPrefix);
    }

    @Override
    public void unregister(String... commands) {
        for (String name : commands) {
            Command command = this.getCommand(name);
            if (command != null) {
                if (command.unregister(this)) {
                    removeCommand(command);
                }
            }
        }
    }

    @Override
    public void unregister(Command... commands) {
        for (Command command : commands) {
            if (command.unregister(this)) {
                removeCommand(command);
            }
        }
    }

    @Override
    public void unregister(Plugin plugin) {
        if (plugin == null) {
            return;
        }
        // Set 去重收集命令对象（多别名 key 指向同一 Command），避免 List.contains 的 O(n²)
        Set<Command> owned = new HashSet<>();
        for (Command command : knownCommands.values()) {
            if (ownsCommand(command, plugin)) {
                owned.add(command);
            }
        }
        for (Command command : owned) {
            command.unregister(this);
        }
        if (!owned.isEmpty()) {
            knownCommands.entrySet().removeIf(entry -> owned.contains(entry.getValue()));
        }

        Map<String, Command> displaced = this.displacedCommandsByPlugin.remove(plugin);
        rebaseDisplacedCommands(plugin, displaced);
        if (displaced != null) {
            displaced.forEach((key, cmd) -> {
                if (!this.knownCommands.containsKey(key)) {
                    this.knownCommands.put(key, cmd);
                }
            });
        }
    }

    /**
     * 将其他插件快照中指向已卸载插件的映射下压到底层，避免逆序卸载时复活旧命令。<br>
     * Rebases snapshots that point to the unloading plugin onto its underlying mappings,
     * preventing reverse-order unload from resurrecting stale commands.
     */
    private void rebaseDisplacedCommands(Plugin plugin, Map<String, Command> underlying) {
        for (Map<String, Command> displaced : this.displacedCommandsByPlugin.values()) {
            for (Iterator<Entry<String, Command>> iterator = displaced.entrySet().iterator(); iterator.hasNext(); ) {
                Entry<String, Command> entry = iterator.next();
                if (!ownsCommand(entry.getValue(), plugin)) {
                    continue;
                }
                Command replacement = underlying == null ? null : underlying.get(entry.getKey());
                if (replacement == null) {
                    iterator.remove();
                } else {
                    entry.setValue(replacement);
                }
            }
        }
    }

    /**
     * 判断命令是否归属于指定插件。<br>
     * Determines whether a command belongs to the given plugin.
     * <p>
     * 普通命令用 ClassLoader 匹配（普通 Command 子类也会钉住插件 ClassLoader）；仅当插件由
     * {@link PluginClassLoader} 加载时生效，避免误删服务端加载的原版命令。插件一律用 {@code ==} 比较。
     * <p>
     * Plain commands match by ClassLoader (a plain Command subclass pins the loader too); only applies when the
     * plugin is loaded by a {@link PluginClassLoader}, so vanilla commands are never removed. Plugins use {@code ==}.
     */
    private boolean ownsCommand(Command command, Plugin plugin) {
        if (command instanceof PluginIdentifiableCommand) {
            return plugin == ((PluginIdentifiableCommand) command).getPlugin();
        }
        ClassLoader pluginLoader = plugin.getClass().getClassLoader();
        return pluginLoader instanceof PluginClassLoader && command.getClass().getClassLoader() == pluginLoader;
    }

    private boolean removeCommand(Command command) {
        return knownCommands.entrySet()
                .removeIf(entry -> entry.getValue().equals(command));
    }

    @Override
    public void registerSimpleCommands(Object object) {
        Plugin owner = resolveSimpleCommandOwner(object);
        for (Method method : object.getClass().getDeclaredMethods()) {
            cn.nukkit.command.simple.Command def = method.getAnnotation(cn.nukkit.command.simple.Command.class);
            if (def != null) {
                SimpleCommand sc = new SimpleCommand(object, method, owner, def.name(), def.description(), def.usageMessage(), def.aliases());

                Arguments args = method.getAnnotation(Arguments.class);
                if (args != null) {
                    sc.setMaxArgs(args.max());
                    sc.setMinArgs(args.min());
                }

                CommandPermission perm = method.getAnnotation(CommandPermission.class);
                if (perm != null) {
                    sc.setPermission(perm.value());
                }

                if (method.isAnnotationPresent(ForbidConsole.class)) {
                    sc.setForbidConsole(true);
                }

                CommandParameters commandParameters = method.getAnnotation(CommandParameters.class);
                if (commandParameters != null) {
                    Map<String, CommandParameter[]> map = Arrays.stream(commandParameters.parameters())
                            .collect(Collectors.toMap(Parameters::name, parameters -> Arrays.stream(parameters.parameters())
                                    .map(parameter -> new CommandParameter(parameter.name(), parameter.type(), parameter.optional()))
                                    .distinct()
                                    .toArray(CommandParameter[]::new)));

                    sc.commandParameters.putAll(map);
                }

                this.register(def.name(), sc);
            }
        }
    }

    /**
     * 解析注解命令 handler 的所属插件，兼容插件注册前的 onLoad 阶段。<br>
     * Resolves an annotated handler owner, including the onLoad phase before PluginManager registration.
     */
    private Plugin resolveSimpleCommandOwner(Object object) {
        if (object instanceof Plugin plugin) {
            return plugin;
        }
        ClassLoader classLoader = object.getClass().getClassLoader();
        if (!(classLoader instanceof PluginClassLoader pluginClassLoader)) {
            return null;
        }
        Plugin loadingPlugin = pluginClassLoader.getPlugin();
        if (loadingPlugin != null) {
            return loadingPlugin;
        }
        var pluginManager = this.server.getPluginManager();
        if (pluginManager == null) {
            return null;
        }
        for (Plugin plugin : pluginManager.getPlugins().values()) {
            if (plugin.getClass().getClassLoader() == classLoader) {
                return plugin;
            }
        }
        return null;
    }

    private boolean registerAlias(Command command, boolean isAlias, String fallbackPrefix, String label,
                                  Plugin owner, Map<String, Command> displaced) {
        putCommand(fallbackPrefix + ':' + label, command, owner, displaced);

        //if you're registering a command alias that is already registered, then return false
        Command existingCommand = this.knownCommands.get(label);
        boolean alreadyRegistered = existingCommand != null;
        boolean existingCommandIsNotVanilla = alreadyRegistered && !(existingCommand instanceof VanillaCommand);
        //basically, if we're an alias and it's already registered, or we're a vanilla command, then we can't override it
        if ((command instanceof VanillaCommand || isAlias) && alreadyRegistered && existingCommandIsNotVanilla) {
            return false;
        }

        // If you're registering a name (alias or label) which is identical to another command who's primary name is the same
        // So basically we can't override the main name of a command, but we can override aliases if we're not an alias

        // Added the last statement which will allow us to override a VanillaCommand unconditionally
        if (alreadyRegistered && existingCommand.getLabel() != null && existingCommand.getLabel().equals(label) && existingCommandIsNotVanilla) {
            return false;
        }

        // You can now assume that the command is either uniquely named, or overriding another command's alias (and is not itself, an alias)

        if (!isAlias) {
            command.setLabel(label);
        }

        // Then we need to check if there isn't any command conflicts with vanilla commands
        ArrayList<String> toRemove = new ArrayList<>();

        for (Entry<String, Command> entry : knownCommands.entrySet()) {
            Command cmd = entry.getValue();
            if (cmd.getLabel().equalsIgnoreCase(command.getLabel()) && !cmd.equals(command)) { // If the new command conflicts... (But if it isn't the same command)
                if (cmd instanceof VanillaCommand) { // And if the old command is a vanilla command...
                    // Remove it!
                    toRemove.add(entry.getKey());
                }
            }
        }

        // Now we loop the toRemove list to remove the command conflicts from the knownCommands map
        for (String cmd : toRemove) {
            Command removed = knownCommands.remove(cmd);
            trackDisplaced(cmd, removed, command, owner, displaced);
        }

        putCommand(label, command, owner, displaced);

        return true;
    }

    private void putCommand(String key, Command command, Plugin owner, Map<String, Command> displaced) {
        Command previous = this.knownCommands.put(key, command);
        trackDisplaced(key, previous, command, owner, displaced);
    }

    private void trackDisplaced(String key, Command previous, Command replacement,
                                Plugin owner, Map<String, Command> displaced) {
        if (displaced != null && previous != null && previous != replacement && !ownsCommand(previous, owner)) {
            displaced.putIfAbsent(key, previous);
        }
    }

    public static ArrayList<String> parseArguments(String cmdLine) {
        StringBuilder sb = new StringBuilder(cmdLine);
        ArrayList<String> args = new ArrayList<>();
        boolean notQuoted = true;
        int curlyBraceCount = 0;
        int start = 0;

        for (int i = 0; i < sb.length(); i++) {
            if ((sb.charAt(i) == '{' && curlyBraceCount >= 1) || (sb.charAt(i) == '{' && (i == 0 || sb.charAt(i - 1) == ' ') && curlyBraceCount == 0)) {
                curlyBraceCount++;
            } else if (sb.charAt(i) == '}' && curlyBraceCount > 0) {
                curlyBraceCount--;
                if (curlyBraceCount == 0) {
                    args.add(sb.substring(start, i + 1));
                    start = i + 1;
                }
            }
            if (curlyBraceCount == 0) {
                if (sb.charAt(i) == ' ' && notQuoted) {
                    String arg = sb.substring(start, i);
                    if (!arg.isEmpty()) {
                        args.add(arg);
                    }
                    start = i + 1;
                } else if (sb.charAt(i) == '"') {
                    sb.deleteCharAt(i);
                    --i;
                    notQuoted = !notQuoted;
                }
            }
        }

        String arg = sb.substring(start);
        if (!arg.isEmpty()) {
            args.add(arg);
        }
        return args;
    }

    @Override
    public boolean dispatch(CommandSender sender, String cmdLine) {
        ArrayList<String> parsed = parseArguments(cmdLine);
        if (parsed.isEmpty()) {
            return false;
        }

        String sentCommandLabel = parsed.remove(0).toLowerCase(Locale.ROOT);//command name
        String[] args = parsed.toArray(EmptyArrays.EMPTY_STRINGS);
        Command target = this.getCommand(sentCommandLabel);

        if (target == null) {
            sender.sendCommandOutput(new CommandOutputContainer(TextFormat.RED + "%commands.generic.unknown", new String[]{sentCommandLabel}, 0));
            return false;
        }

        boolean output;
        try {
            if (target.hasParamTree()) {
                var plugin = target instanceof PluginCommand<?> pluginCommand ? pluginCommand.getPlugin() : InternalPlugin.INSTANCE;
                var result = target.getParamTree().matchAndParse(sender, sentCommandLabel, args);
                if (result == null) output = false;
                else if (target.testPermissionSilent(sender)) {
                    try {
                        output = target.execute(sender, sentCommandLabel, result, new CommandLogger(target, sender, sentCommandLabel, args, result.getValue().getMessageContainer(), plugin)) == 1;
                    } catch (UnsupportedOperationException e) {
                        this.server.getLogger().error("If you use paramtree, you must override execute(CommandSender sender, String commandLabel, Map.Entry<String, ParamList> result, CommandLogger log) method to run the command!");
                        output = false;
                    }
                } else {
                    var log = new CommandLogger(target, sender, sentCommandLabel, args, plugin);
                    if (target.getPermissionMessage() == null) {
                        log.addMessage("nukkit.command.generic.permission").output();
                    } else if (!target.getPermissionMessage().isEmpty()) {
                        log.addError(target.getPermissionMessage().replace("<permission>", target.getPermission())).output();
                    }
                    output = false;
                }
            } else {
                output = target.execute(sender, sentCommandLabel, args);
            }
        } catch (Exception e) {
            this.server.getLogger().error(this.server.getLanguage().translateString("nukkit.command.exception", cmdLine, target.toString(), Utils.getExceptionMessage(e)), e);
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "%commands.generic.exception"));
            output = false;
        }

        return output;
    }

    @Override
    public void clearCommands() {
        for (Command command : this.knownCommands.values()) {
            command.unregister(this);
        }
        this.knownCommands.clear();
        this.displacedCommandsByPlugin.clear();
        this.setDefaultCommands();
    }

    @Override
    public Command getCommand(String name) {
        if (this.knownCommands.containsKey(name)) {
            return this.knownCommands.get(name);
        }
        return null;
    }

    public Map<String, Command> getCommands() {
        return knownCommands;
    }
}
