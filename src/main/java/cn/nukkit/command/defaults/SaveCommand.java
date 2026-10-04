package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.InternalPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created on 2015/11/13 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class SaveCommand extends VanillaCommand {

    public SaveCommand(String name) {
        super(name, "%nukkit.command.save.description", "%commands.save.usage");
        this.setPermission("nukkit.command.save.perform");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                CommandParameter.newEnum("mode", true, new CommandEnum("SaveMode", "on", "off", "hold", "resume"))
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "on":
                    sender.getServer().setAutoSave(true);
                    Command.broadcastCommandMessage(sender, new TranslationContainer("commands.save.enabled"));
                    return true;
                case "off":
                    sender.getServer().setAutoSave(false);
                    Command.broadcastCommandMessage(sender, new TranslationContainer("commands.save.disabled"));
                    return true;
                case "hold":
                    sender.getServer().holdWorldSave = true;
                    Command.broadcastCommandMessage(sender, new TranslationContainer("commands.save.hold-on"));
                    return true;
                case "resume":
                    sender.getServer().holdWorldSave = false;
                    Command.broadcastCommandMessage(sender, new TranslationContainer("commands.save.hold-off"));
                    return true;
                default:
                    sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
                    return false;
            }
        }

        broadcastCommandMessage(sender, new TranslationContainer("commands.save.start"));

        // 统一经 scheduleSyncTaskAndWait（自有线程/主线程内联，其余排队）：绝不在别的世界线程内联目标世界（与主线程接管并发）；
        // 世界线程调用者不阻塞等待其他 future（互等死锁），完成后在主线程异步广播
        // All through scheduleSyncTaskAndWait: never inline a foreign level (races the primary takeover); a level-thread
        // caller never blocks on other futures (deadlock) - the completion broadcast is asynchronous
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (Player player : sender.getServer().getOnlinePlayers().values()) {
            Level playerLevel = player.getLevel();
            if (playerLevel != null && playerLevel.isParallelTickEnabled() && !player.isSpawnInitCompleted()) {
                // 登录序列未收尾：与包路由同守卫，存档转主线程与登录串行（不计入完成广播）
                // Login not finalized: same guard as packet routing - save on the primary thread instead
                sender.getServer().getScheduler().scheduleTask(InternalPlugin.INSTANCE, () -> {
                    if (player.isOnline()) {
                        player.save();
                    }
                });
            } else if (playerLevel != null) {
                saves.add(playerLevel.scheduleSyncTaskAndWait(() -> {
                    if (player.isOnline() && player.getLevel() == playerLevel) {
                        player.save();
                    }
                }));
            } else {
                player.save();
            }
        }

        for (Level level : sender.getServer().getLevels().values()) {
            saves.add(level.scheduleSyncTaskAndWait(() -> level.save(true)));
        }

        Runnable broadcastSuccess = () ->
                broadcastCommandMessage(sender, new TranslationContainer("commands.save.success"));
        Runnable broadcastPartial = () -> {
            sender.getServer().getLogger().error("Timed out or failed waiting for parallel-world saves during /save-all; some worlds may still be writing");
            broadcastCommandMessage(sender, "Save incomplete: some worlds may still be writing (timed out or failed)");
        };

        if (sender.getServer().isLevelThread()) {
            // orTimeout 兜底：世界线程卡死时 allOf 永不完成，广播会被静默吞掉
            // orTimeout backstop: a wedged level thread would leave allOf (and the broadcast) pending forever
            CompletableFuture.allOf(saves.toArray(new CompletableFuture[0]))
                    .orTimeout(60, TimeUnit.SECONDS)
                    .whenComplete((result, error) -> sender.getServer().getScheduler()
                            .scheduleTask(InternalPlugin.INSTANCE, error == null ? broadcastSuccess : broadcastPartial));
            return true;
        }

        try {
            CompletableFuture.allOf(saves.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            broadcastSuccess.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            broadcastPartial.run();
        } catch (Exception e) {
            broadcastPartial.run();
        }
        return true;
    }
}
