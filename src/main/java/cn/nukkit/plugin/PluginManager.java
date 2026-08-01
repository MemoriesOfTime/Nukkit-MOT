package cn.nukkit.plugin;

import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.command.PluginCommand;
import cn.nukkit.command.SimpleCommandMap;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.*;
import cn.nukkit.event.plugin.PluginUnloadEvent;
import cn.nukkit.level.Level;
import cn.nukkit.permission.Permissible;
import cn.nukkit.permission.Permission;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.PluginException;
import cn.nukkit.utils.Utils;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * @author MagicDroidX
 */
@Log4j2
public class PluginManager {

    private final Server server;

    private final SimpleCommandMap commandMap;

    protected final Map<String, Plugin> plugins = new LinkedHashMap<>();

    protected final Map<String, Permission> permissions = new ConcurrentHashMap<>();

    protected final Map<String, Permission> defaultPerms = new ConcurrentHashMap<>();

    protected final Map<String, Permission> defaultPermsOp = new ConcurrentHashMap<>();

    protected final Map<String, Set<Permissible>> permSubs = new ConcurrentHashMap<>();

    protected final Set<Permissible> defSubs = ConcurrentHashMap.newKeySet();

    protected final Set<Permissible> defSubsOp = ConcurrentHashMap.newKeySet();

    protected final Map<String, PluginLoader> fileAssociations = new HashMap<>();

    /**
     * Cache: Event class -> HandlerList, avoiding repeated reflection lookups.
     */
    private final Map<Class<? extends Event>, HandlerList> handlerListCache = new ConcurrentHashMap<>();

    public PluginManager(Server server, SimpleCommandMap commandMap) {
        this.server = server;
        this.commandMap = commandMap;
    }

    public Plugin getPlugin(String name) {
        return this.plugins.get(name);
    }

    public boolean registerInterface(Class<? extends PluginLoader> loaderClass) {
        if (loaderClass != null) {
            try {
                Constructor<? extends PluginLoader> constructor = loaderClass.getDeclaredConstructor(Server.class);
                constructor.setAccessible(true);
                this.fileAssociations.put(loaderClass.getName(), constructor.newInstance(this.server));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public Map<String, Plugin> getPlugins() {
        return plugins;
    }

    public void loadInternalPlugin() {
        PluginLoader pluginLoader = fileAssociations.get(JavaPluginLoader.class.getName());
        InternalPlugin plugin = InternalPlugin.INSTANCE;
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Nukkit-MOT");
        info.put("version", server.getNukkitVersion());
        info.put("main", InternalPlugin.class.getName());
        File file;
        try {
            file = new File(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            file = new File(".");
        }
        PluginDescription description = new PluginDescription(info);
        plugin.init(pluginLoader, server, description, new File("Nukkit-MOT"), file);
        plugins.put(description.getName(), plugin);
        enablePlugin(plugin);
    }


    public Plugin loadPlugin(String path) {
        return this.loadPlugin(path, null);
    }

    public Plugin loadPlugin(File file) {
        return this.loadPlugin(file, null);
    }

    public Plugin loadPlugin(String path, Map<String, PluginLoader> loaders) {
        return this.loadPlugin(new File(path), loaders);
    }

    public Plugin loadPlugin(File file, Map<String, PluginLoader> loaders) {
        for (PluginLoader loader : (loaders == null ? this.fileAssociations : loaders).values()) {
            for (Pattern pattern : loader.getPluginFilters()) {
                if (pattern.matcher(file.getName()).matches()) {
                    PluginDescription description = loader.getPluginDescription(file);
                    if (description != null) {
                        try {
                            Plugin plugin = loader.loadPlugin(file);
                            if (plugin != null) {
                                this.plugins.put(plugin.getDescription().getName(), plugin);

                                List<PluginCommand> pluginCommands = this.parseYamlCommands(plugin);

                                if (!pluginCommands.isEmpty()) {
                                    this.commandMap.registerAll(plugin.getDescription().getName(), pluginCommands);
                                }

                                return plugin;
                            }
                        } catch (Exception e) {
                            Server.getInstance().getLogger().critical("Could not load plugin", e);
                            return null;
                        }
                    }
                }
            }
        }

        return null;
    }

    public Map<String, Plugin> loadPlugins(String dictionary) {
        return this.loadPlugins(new File(dictionary));
    }

    public Map<String, Plugin> loadPlugins(File dictionary) {
        return this.loadPlugins(dictionary, null);
    }

    public Map<String, Plugin> loadPlugins(String dictionary, List<String> newLoaders) {
        return this.loadPlugins(new File(dictionary), newLoaders);
    }

    public Map<String, Plugin> loadPlugins(File dictionary, List<String> newLoaders) {
        return this.loadPlugins(dictionary, newLoaders, false);
    }

    public Map<String, Plugin> loadPlugins(File dictionary, List<String> newLoaders, boolean includeDir) {
        if (dictionary.isDirectory()) {
            Map<String, File> plugins = new LinkedHashMap<>();
            Map<String, Plugin> loadedPlugins = new LinkedHashMap<>();
            Map<String, List<String>> dependencies = new LinkedHashMap<>();
            Map<String, List<String>> softDependencies = new LinkedHashMap<>();
            Map<String, PluginLoader> loaders = new LinkedHashMap<>();
            if (newLoaders != null) {
                for (String key : newLoaders) {
                    if (this.fileAssociations.containsKey(key)) {
                        loaders.put(key, this.fileAssociations.get(key));
                    }
                }
            } else {
                loaders = this.fileAssociations;
            }

            for (final PluginLoader loader : loaders.values()) {
                for (File file : dictionary.listFiles((dir, name) -> {
                    for (Pattern pattern : loader.getPluginFilters()) {
                        if (pattern.matcher(name).matches()) {
                            return true;
                        }
                    }
                    return false;
                })) {
                    if (file.isDirectory() && !includeDir) {
                        continue;
                    }
                    try {
                        PluginDescription description = loader.getPluginDescription(file);
                        if (description != null) {
                            String name = description.getName();

                            if (plugins.containsKey(name) || this.getPlugin(name) != null) {
                                this.server.getLogger().error(this.server.getLanguage().translateString("nukkit.plugin.duplicateError", name));
                                continue;
                            }

                            plugins.put(name, file);

                            softDependencies.put(name, description.getSoftDepend());

                            dependencies.put(name, description.getDepend());

                            for (String before : description.getLoadBefore()) {
                                if (softDependencies.containsKey(before)) {
                                    softDependencies.get(before).add(name);
                                } else {
                                    List<String> list = new ArrayList<>();
                                    list.add(name);
                                    softDependencies.put(before, list);
                                }
                            }
                        }
                    } catch (Exception e) {
                        this.server.getLogger().error(this.server.getLanguage().translateString("nukkit.plugin.fileError", file.getName(), dictionary.toString(), Utils
                                .getExceptionMessage(e)));
                        MainLogger logger = this.server.getLogger();
                        if (logger != null) {
                            logger.logException(e);
                        }
                    }
                }
            }

            while (!plugins.isEmpty()) {
                boolean missingDependency = true;
                for (String name : new ArrayList<>(plugins.keySet())) {
                    File file = plugins.get(name);
                    if (dependencies.containsKey(name)) {
                        for (String dependency : new ArrayList<>(dependencies.get(name))) {
                            if (loadedPlugins.containsKey(dependency) || this.getPlugin(dependency) != null) {
                                dependencies.get(name).remove(dependency);
                            } else if (!plugins.containsKey(dependency)) {
                                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.loadError", new String[]{name, "%nukkit.plugin.unknownDependency"}) + ' ' + dependency);
                                break;
                            }
                        }

                        if (dependencies.get(name).isEmpty()) {
                            dependencies.remove(name);
                        }
                    }

                    if (softDependencies.containsKey(name)) {
                        softDependencies.get(name).removeIf(dependency -> loadedPlugins.containsKey(dependency) || this.getPlugin(dependency) != null);

                        if (softDependencies.get(name).isEmpty()) {
                            softDependencies.remove(name);
                        }
                    }

                    if (!dependencies.containsKey(name) && !softDependencies.containsKey(name)) {
                        plugins.remove(name);
                        missingDependency = false;
                        Plugin plugin = this.loadPlugin(file, loaders);
                        if (plugin != null) {
                            loadedPlugins.put(name, plugin);
                        } else {
                            this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.genericLoadError", name));
                        }
                    }
                }

                if (missingDependency) {
                    for (String name : new ArrayList<>(plugins.keySet())) {
                        File file = plugins.get(name);
                        if (!dependencies.containsKey(name)) {
                            softDependencies.remove(name);
                            plugins.remove(name);
                            missingDependency = false;
                            Plugin plugin = this.loadPlugin(file, loaders);
                            if (plugin != null) {
                                loadedPlugins.put(name, plugin);
                            } else {
                                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.genericLoadError", name));
                            }
                        }
                    }

                    if (missingDependency) {
                        for (String name : plugins.keySet()) {
                            this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.loadError", new String[]{name, "%nukkit.plugin.circularDependency"}));
                        }
                        plugins.clear();
                    }
                }
            }

            return loadedPlugins;
        } else {
            return new HashMap<>();
        }
    }

    public Permission getPermission(String name) {
        if (this.permissions.containsKey(name)) {
            return this.permissions.get(name);
        }
        return null;
    }

    public boolean addPermission(Permission permission) {
        if (!this.permissions.containsKey(permission.getName())) {
            this.permissions.put(permission.getName(), permission);
            this.calculatePermissionDefault(permission);

            return true;
        }

        return false;
    }

    public void removePermission(String name) {
        this.permissions.remove(name);
    }

    public void removePermission(Permission permission) {
        this.removePermission(permission.getName());
    }

    public Map<String, Permission> getDefaultPermissions(boolean op) {
        if (op) {
            return this.defaultPermsOp;
        } else {
            return this.defaultPerms;
        }
    }

    public void recalculatePermissionDefaults(Permission permission) {
        if (this.permissions.containsKey(permission.getName())) {
            this.defaultPermsOp.remove(permission.getName());
            this.defaultPerms.remove(permission.getName());
            this.calculatePermissionDefault(permission);
        }
    }

    private void calculatePermissionDefault(Permission permission) {
        if (permission.getDefault().equals(Permission.DEFAULT_OP) || permission.getDefault().equals(Permission.DEFAULT_TRUE)) {
            this.defaultPermsOp.put(permission.getName(), permission);
            this.dirtyPermissibles(true);
        }

        if (permission.getDefault().equals(Permission.DEFAULT_NOT_OP) || permission.getDefault().equals(Permission.DEFAULT_TRUE)) {
            this.defaultPerms.put(permission.getName(), permission);
            this.dirtyPermissibles(false);
        }
    }

    private void dirtyPermissibles(boolean op) {
        for (Permissible p : this.getDefaultPermSubscriptions(op)) {
            p.recalculatePermissions();
        }
    }

    public void subscribeToPermission(String permission, Permissible permissible) {
        if (!this.permSubs.containsKey(permission)) {
            this.permSubs.put(permission, ConcurrentHashMap.newKeySet());
        }
        this.permSubs.get(permission).add(permissible);
    }

    public void unsubscribeFromPermission(String permission, Permissible permissible) {
        if (this.permSubs.containsKey(permission)) {
            this.permSubs.get(permission).remove(permissible);
            if (this.permSubs.get(permission).isEmpty()) {
                this.permSubs.remove(permission);
            }
        }
    }

    public Set<Permissible> getPermissionSubscriptions(String permission) {
        if (this.permSubs.containsKey(permission)) {
            return new HashSet<>(this.permSubs.get(permission));
        }
        return new HashSet<>();
    }

    public void subscribeToDefaultPerms(boolean op, Permissible permissible) {
        if (op) {
            this.defSubsOp.add(permissible);
        } else {
            this.defSubs.add(permissible);
        }
    }

    public void unsubscribeFromDefaultPerms(boolean op, Permissible permissible) {
        if (op) {
            this.defSubsOp.remove(permissible);
        } else {
            this.defSubs.remove(permissible);
        }
    }

    public Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        if (op) {
            return new HashSet<>(this.defSubsOp);
        } else {
            return new HashSet<>(this.defSubs);
        }
    }

    public Map<String, Permission> getPermissions() {
        return permissions;
    }

    public boolean isPluginEnabled(Plugin plugin) {
        if (plugin != null && this.plugins.containsKey(plugin.getDescription().getName())) {
            return plugin.isEnabled();
        } else {
            return false;
        }
    }

    public void enablePlugin(Plugin plugin) {
        if (!plugin.isEnabled()) {
            try {
                for (Permission permission : plugin.getDescription().getPermissions()) {
                    this.addPermission(permission);
                }
                plugin.getPluginLoader().enablePlugin(plugin);
            } catch (Throwable e) {
                MainLogger logger = this.server.getLogger();
                if (logger != null) {
                    logger.logException(new RuntimeException("plugin: " + plugin.getName() + " load failed!", e));
                }
                this.disablePlugin(plugin);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected List<PluginCommand> parseYamlCommands(Plugin plugin) {
        List<PluginCommand> pluginCmds = new ArrayList<>();

        for (Map.Entry entry : plugin.getDescription().getCommands().entrySet()) {
            String key = (String) entry.getKey();
            Object data = entry.getValue();
            if (key.contains(":")) {
                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.commandError", new String[]{key, plugin.getDescription().getFullName()}));
                continue;
            }
            if (data instanceof Map) {
                PluginCommand newCmd = new PluginCommand<>(key, plugin);

                if (((Map) data).containsKey("description")) {
                    newCmd.setDescription((String) ((Map) data).get("description"));
                }

                if (((Map) data).containsKey("usage")) {
                    newCmd.setUsage((String) ((Map) data).get("usage"));
                }

                if (((Map) data).containsKey("aliases")) {
                    Object aliases = ((Map) data).get("aliases");
                    if (aliases instanceof List) {
                        List<String> aliasList = new ArrayList<>();
                        for (String alias : (List<String>) aliases) {
                            if (alias.contains(":")) {
                                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.aliasError", new String[]{alias, plugin.getDescription().getFullName()}));
                                continue;
                            }
                            aliasList.add(alias);
                        }

                        newCmd.setAliases(aliasList.toArray(new String[0]));
                    }
                }

                if (((Map) data).containsKey("permission")) {
                    newCmd.setPermission((String) ((Map) data).get("permission"));
                }

                if (((Map) data).containsKey("permission-message")) {
                    newCmd.setPermissionMessage((String) ((Map) data).get("permission-message"));
                }

                pluginCmds.add(newCmd);
            }
        }

        return pluginCmds;
    }

    public void disablePlugins() {
        ListIterator<Plugin> plugins = new ArrayList<>(this.plugins.values()).listIterator(this.plugins.size());

        while (plugins.hasPrevious()) {
            Plugin previous = plugins.previous();
            if (previous != InternalPlugin.INSTANCE) {
                this.disablePlugin(previous);
            }
        }
    }

    public void disablePlugin(Plugin plugin) {
        if (InternalPlugin.INSTANCE == plugin) {
            throw new UnsupportedOperationException("The Nukkit-MOT Internal plugin can't be disabled.");
        }

        if (plugin.isEnabled()) {
            try {
                plugin.getPluginLoader().disablePlugin(plugin);
            } catch (Exception e) {
                MainLogger logger = this.server.getLogger();
                if (logger != null) {
                    logger.logException(e);
                }
            }

            this.server.getScheduler().cancelTask(plugin);
            HandlerList.unregisterAll(plugin);
            boolean dirtyOp = false;
            boolean dirtyNotOp = false;
            for (Permission permission : plugin.getDescription().getPermissions()) {
                dirtyOp |= this.defaultPermsOp.remove(permission.getName()) != null;
                dirtyNotOp |= this.defaultPerms.remove(permission.getName()) != null;
                this.removePermission(permission);
            }
            if (dirtyOp) {
                this.dirtyPermissibles(true);
            }
            if (dirtyNotOp) {
                this.dirtyPermissibles(false);
            }
        }
    }

    public void clearPlugins() {
        this.disablePlugins();
        this.plugins.clear();
        this.clearRegistries();
    }

    /**
     * 重置插件表之外的全局注册表（权限表、默认权限表、加载器关联、事件缓存）。<br>
     * Resets the global registries outside the plugin map (permission tables, default perms, loader associations, event cache).
     * <p>
     * 运行期经 {@link #addPermission} 动态注册的权限不在 plugin.yml 中，卸载流程无从得知，由 reload 在卸载后统一清除。<br>
     * Runtime permissions registered via {@link #addPermission} are absent from plugin.yml, so reload drops them here after unloading.
     */
    public void clearRegistries() {
        boolean dirtyOp = !this.defaultPermsOp.isEmpty();
        boolean dirtyNotOp = !this.defaultPerms.isEmpty();
        this.fileAssociations.clear();
        this.permissions.clear();
        this.defaultPerms.clear();
        this.defaultPermsOp.clear();
        this.handlerListCache.clear();
        if (dirtyOp) {
            this.dirtyPermissibles(true);
        }
        if (dirtyNotOp) {
            this.dirtyPermissibles(false);
        }
    }

    /**
     * 彻底卸载一个插件（停用 + 清理命令/附件/metadata/注册表 + 关闭 ClassLoader）。插件不存在或被取消时返回 false。<br>
     * Fully unloads a plugin (disable + clear commands/attachments/metadata/registry + close ClassLoader). false if unknown or cancelled.
     *
     * @param name 要卸载的插件名。<br>The name of the plugin to unload.
     */
    public boolean unloadPlugin(String name) {
        return this.unloadPlugin(this.getPlugin(name));
    }

    /**
     * 彻底卸载一个插件。见 {@link #unloadPlugin(String)}。<br>
     * Fully unloads a plugin. See {@link #unloadPlugin(String)}.
     *
     * @param plugin 要卸载的插件。<br>The plugin to unload.
     */
    public boolean unloadPlugin(Plugin plugin) {
        return this.unloadPlugin(plugin, false);
    }

    /**
     * 彻底卸载一个插件，可选强制模式。<br>
     * Fully unloads a plugin, optionally forcing it.
     *
     * @param plugin 要卸载的插件。<br>The plugin to unload.
     * @param force  为 true 时忽略 {@link PluginUnloadEvent} 的取消结果，用于 reload 等必须完成的流程。<br>
     *               When true a cancelled {@link PluginUnloadEvent} is ignored, for flows such as reload that must complete.
     * @return 卸载是否成功执行。<br>Whether the unload completed.
     */
    public boolean unloadPlugin(Plugin plugin, boolean force) {
        if (plugin == null) {
            return false;
        }
        return this.unloadPlugins(Collections.singletonList(plugin), force) > 0;
    }

    /**
     * 批量彻底卸载插件。按加载逆序卸载（依赖方先于被依赖方），附件与 metadata 只遍历一次世界。<br>
     * Fully unloads multiple plugins in reverse load order (dependents first), sweeping attachments/metadata once.
     *
     * @param targets 要卸载的插件集合，{@link InternalPlugin} 与 null 会被忽略。<br>
     *                The plugins to unload; {@link InternalPlugin} and null entries are ignored.
     * @param force   为 true 时忽略 {@link PluginUnloadEvent} 的取消结果。<br>When true, cancelled events are ignored.
     * @return 实际卸载的插件数量。<br>The number of plugins actually unloaded.
     */
    public int unloadPlugins(Collection<Plugin> targets, boolean force) {
        if (targets == null || targets.isEmpty()) {
            return 0;
        }

        // 加载逆序：依赖方先卸载，避免其 onDisable 访问已关闭的依赖 ClassLoader / reverse order: dependents first
        Set<Plugin> requested = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Plugin plugin : targets) {
            if (plugin != null && plugin != InternalPlugin.INSTANCE) {
                requested.add(plugin);
            }
        }
        List<Plugin> ordered = new ArrayList<>(requested.size());
        List<Plugin> registered = new ArrayList<>(this.plugins.values());
        for (int i = registered.size() - 1; i >= 0; i--) {
            Plugin plugin = registered.get(i);
            if (requested.remove(plugin)) {
                ordered.add(plugin);
            }
        }

        // 触发 PluginUnloadEvent；非强制模式下监听器可取消单个卸载 / fire event; listener may cancel unless forced
        List<Plugin> accepted = new ArrayList<>(ordered.size());
        for (Plugin plugin : ordered) {
            PluginUnloadEvent event = new PluginUnloadEvent(plugin);
            this.callEvent(event);
            if (event.isCancelled()) {
                if (!force) {
                    continue;
                }
                this.server.getLogger().warning("Ignoring cancelled PluginUnloadEvent for "
                        + plugin.getDescription().getFullName() + ": the unload was forced");
            }
            accepted.add(plugin);
        }
        if (accepted.isEmpty()) {
            return 0;
        }

        for (Plugin plugin : accepted) {
            String name = plugin.getDescription().getName();
            this.server.getLogger().info(this.server.getLanguage().translateString("nukkit.plugin.unload", plugin.getDescription().getFullName()));

            try {
                if (plugin.isEnabled()) {
                    this.disablePlugin(plugin);
                }
            } catch (Throwable e) {
                this.server.getLogger().logException(new RuntimeException("Error while disabling plugin " + name + " during unload", e));
            }

            // 无条件注销监听器/任务：插件可能在 onLoad 注册却从未启用，残留引用会钉住 ClassLoader
            // unconditional: a plugin may register listeners in onLoad without ever enabling, pinning the ClassLoader
            try {
                HandlerList.unregisterAll(plugin);
                this.server.getScheduler().cancelTask(plugin);
            } catch (Throwable e) {
                this.server.getLogger().logException(new RuntimeException("Error while unregistering listeners/tasks for plugin " + name, e));
            }

            try {
                this.commandMap.unregister(plugin);
            } catch (Throwable e) {
                this.server.getLogger().logException(new RuntimeException("Error while unregistering commands for plugin " + name, e));
            }
        }

        this.clearPluginReferences(accepted);

        for (Plugin plugin : accepted) {
            String name = plugin.getDescription().getName();

            try {
                this.server.getPlayerMetadata().invalidateAll(plugin);
                this.server.getEntityMetadata().invalidateAll(plugin);
                this.server.getLevelMetadata().invalidateAll(plugin);
            } catch (Throwable e) {
                this.server.getLogger().logException(new RuntimeException("Error while invalidating metadata for plugin " + name, e));
            }

            // 按实例移除，避免同名实例被误删 / match by instance so a same-named plugin is never evicted
            this.plugins.remove(name, plugin);

            // 委托加载器关闭 ClassLoader；插件已 disable，不会再触发 onDisable / already disabled, won't re-trigger onDisable
            try {
                plugin.getPluginLoader().unloadPlugin(plugin);
            } catch (Throwable e) {
                this.server.getLogger().logException(new RuntimeException("Error while closing ClassLoader for plugin " + name, e));
            }
        }

        this.handlerListCache.clear();

        return accepted.size();
    }

    /**
     * 一次遍历控制台、在线玩家与全部世界，清理这些插件的权限附件和方块 metadata。<br>
     * Sweeps the console, online players and every level once, clearing these plugins' permission attachments
     * and block metadata.
     */
    private void clearPluginReferences(List<Plugin> targets) {
        this.clearAttachments(this.server.getConsoleSender(), targets);
        for (Permissible permissible : this.server.getOnlinePlayers().values()) {
            this.clearAttachments(permissible, targets);
        }
        for (Level level : this.server.getLevels().values()) {
            // 命令方块（含矿车）经 ICommandBlock 实现 Permissible，可能持有插件附件 / command blocks are Permissible and may hold attachments
            for (BlockEntity blockEntity : level.getBlockEntities().values()) {
                if (blockEntity instanceof Permissible) {
                    this.clearAttachments((Permissible) blockEntity, targets);
                }
            }
            for (Entity entity : level.getEntities()) {
                if (entity instanceof Permissible) {
                    this.clearAttachments((Permissible) entity, targets);
                }
            }
            for (Plugin plugin : targets) {
                try {
                    level.getBlockMetadata().invalidateAll(plugin);
                } catch (Throwable e) {
                    this.server.getLogger().logException(new RuntimeException("Error while invalidating block metadata in level "
                            + level.getName() + " for plugin " + plugin.getDescription().getName(), e));
                }
            }
        }
    }

    /**
     * 逐个插件清理某个 Permissible 的附件，单点异常就地捕获，不影响其余对象。<br>
     * Clears one Permissible's attachments per plugin, catching failures in place so the remaining objects are still cleaned.
     */
    private void clearAttachments(Permissible permissible, List<Plugin> targets) {
        for (Plugin plugin : targets) {
            try {
                permissible.clearAttachments(plugin);
            } catch (Throwable e) {
                this.server.getLogger().logException(new RuntimeException("Error while clearing attachments of "
                        + permissible.getClass().getName() + " for plugin " + plugin.getDescription().getName(), e));
            }
        }
    }

    public void callEvent(Event event) {
        try {
            for (RegisteredListener registration : getEventListeners(event.getClass()).getRegisteredListeners()) {
                if (!registration.getPlugin().isEnabled()) {
                    continue;
                }

                try {
                    registration.callEvent(event);
                } catch (Exception e) {
                    log.error(this.server.getLanguage().translateString("nukkit.plugin.eventError", event.getEventName(), registration.getPlugin().getDescription().getFullName(), e.getMessage(), registration.getListener().getClass().getName()), e);
                }
            }
        } catch (IllegalAccessException e) {
            log.error("An error has occurred while calling the event {}", event, e);
        }
    }

    public void registerEvents(Listener listener, Plugin plugin) {
        if (!plugin.isEnabled()) {
            throw new PluginException("Plugin attempted to register " + listener.getClass().getName() + " while not enabled");
        }

        Set<Method> methods;
        try {
            Method[] publicMethods = listener.getClass().getMethods();
            Method[] privateMethods = listener.getClass().getDeclaredMethods();
            methods = new HashSet<>(publicMethods.length + privateMethods.length, 1.0f);
            Collections.addAll(methods, publicMethods);
            Collections.addAll(methods, privateMethods);
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().error("Plugin " + plugin.getDescription().getFullName() + " has failed to register events for " + listener.getClass() + " because " + e.getMessage() + " does not exist.");
            return;
        }

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Class<?> checkClass;

            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().error(plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }

            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);

            for (Class<?> clazz = eventClass; Event.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
                if (clazz.getAnnotation(Deprecated.class) != null) {
                    if (this.server.deprecatedVerbose) {
                        this.server.getLogger().warning(this.server.getLanguage().translateString("nukkit.plugin.deprecatedEvent", plugin.getName(), clazz.getName(), listener.getClass().getName() + "." + method.getName() + "()"));
                    }
                    break;
                }
            }
            this.registerEvent(eventClass, listener, eh.priority(), new MethodEventExecutor(method), plugin, eh.ignoreCancelled());
        }
    }

    public void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin) throws PluginException {
        this.registerEvent(event, listener, priority, executor, plugin, false);
    }

    public void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin, boolean ignoreCancelled) throws PluginException {
        if (!plugin.isEnabled()) {
            throw new PluginException("Plugin attempted to register " + event + " while not enabled");
        }

        try {
            this.getEventListeners(event).register(new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled));
        } catch (IllegalAccessException e) {
            log.error("An error occurred while registering the event listener event:{}, listener:{} for plugin:{} version:{}",
                    event, listener, plugin.getDescription().getName(), plugin.getDescription().getVersion(), e);
        }
    }

    private HandlerList getEventListeners(Class<? extends Event> type) throws IllegalAccessException {
        HandlerList cached = handlerListCache.get(type);
        if (cached != null) {
            return cached;
        }
        try {
            Method method = getRegistrationClass(type).getDeclaredMethod("getHandlers");
            method.setAccessible(true);
            HandlerList handlerList = (HandlerList) method.invoke(null);
            handlerListCache.put(type, handlerList);
            return handlerList;
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("getHandlers method in " + type.getName() + " was not static!");
        } catch (Exception e) {
            throw new IllegalAccessException(Utils.getExceptionMessage(e));
        }
    }

    private Class<? extends Event> getRegistrationClass(Class<? extends Event> clazz) throws IllegalAccessException {
        try {
            clazz.getDeclaredMethod("getHandlers");
            return clazz;
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null
                    && clazz.getSuperclass() != Event.class
                    && Event.class.isAssignableFrom(clazz.getSuperclass())) {
                return getRegistrationClass(clazz.getSuperclass().asSubclass(Event.class));
            } else {
                throw new IllegalAccessException("Unable to find handler list for event " + clazz.getName() + ". Static getHandlers method required!");
            }
        }
    }
}
