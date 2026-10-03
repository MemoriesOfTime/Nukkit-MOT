package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.Level;

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

        // 并行世界的保存投递其世界线程，并等待全部完成后再广播成功（master 语义：全部写盘完成
        // 才广播，依赖该消息的备份脚本不会拷贝到写一半的世界文件）；玩家自己世界的任务内联执行
        // Parallel-world saves are hopped to their level threads and awaited before the
        // success broadcast (master semantics: broadcast only after everything is written);
        // the sender's own level runs its task inline via scheduleSyncTaskAndWait
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (Player player : sender.getServer().getOnlinePlayers().values()) {
            Level playerLevel = player.getLevel();
            if (playerLevel != null && playerLevel.isParallelTickEnabled()) {
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
            if (level.isParallelTickEnabled()) {
                saves.add(level.scheduleSyncTaskAndWait(() -> level.save(true)));
            } else {
                level.save(true);
            }
        }

        try {
            CompletableFuture.allOf(saves.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            sender.getServer().getLogger().error("Timed out or failed waiting for parallel-world saves during /save-all; some worlds may still be writing", e);
        }

        broadcastCommandMessage(sender, new TranslationContainer("commands.save.success"));
        return true;
    }
}
