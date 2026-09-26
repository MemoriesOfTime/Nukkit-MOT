package cn.nukkit.command.defaults;

import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.utils.TextFormat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created on 2015/11/11 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class GarbageCollectorCommand extends VanillaCommand {

    public GarbageCollectorCommand(String name) {
        super(name, "%nukkit.command.gc.description", "%nukkit.command.gc.usage");
        this.setPermission("nukkit.command.gc");
        this.commandParameters.clear();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        int chunksCollected = 0;
        int entitiesCollected = 0;
        int tilesCollected = 0;
        long memory = Runtime.getRuntime().freeMemory();

        for (Level level : sender.getServer().getLevels().values()) {
            // 并行世界的区块卸载须在其世界线程执行，等待结果以保持统计准确
            int[] stats = new int[3];
            AtomicBoolean executed = new AtomicBoolean(false);
            Runnable collection = () -> {
                if (!executed.compareAndSet(false, true)) {
                    return;
                }
                int chunksCount = level.getChunks().size();
                int entitiesCount = level.getEntities().length;
                int tilesCount = level.getBlockEntities().size();
                level.doChunkGarbageCollection();
                level.unloadChunks(true);
                stats[0] = chunksCount - level.getChunks().size();
                stats[1] = entitiesCount - level.getEntities().length;
                stats[2] = tilesCount - level.getBlockEntities().size();
            };
            if (level.isParallelTickEnabled()) {
                CompletableFuture<Void> future = level.scheduleSyncTaskAndWait(collection);
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // 中断（通常为关服）：不内联跨 owner 执行，放弃本轮并跳过剩余世界
                    Thread.currentThread().interrupt();
                    sender.getServer().getLogger().warning("Interrupted while waiting for level GC; skipping GC for '"
                            + level.getName() + "' and remaining levels");
                    break;
                } catch (ExecutionException e) {
                    sender.getServer().getLogger().logException(e.getCause());
                } catch (TimeoutException e) {
                    // GC 为维护性操作：超时放弃该世界本轮，排队任务在线程恢复后自行执行
                    sender.getServer().getLogger().error("Level thread for '" + level.getName()
                            + "' did not run garbage collection within 5s; skipping GC for this level");
                }
            } else {
                collection.run();
            }
            chunksCollected += stats[0];
            entitiesCollected += stats[1];
            tilesCollected += stats[2];
        }

        System.gc();

        long freedMemory = Runtime.getRuntime().freeMemory() - memory;

        sender.sendMessage(TextFormat.GREEN + "---- " + TextFormat.WHITE + "Garbage collection result" + TextFormat.GREEN + " ----");
        sender.sendMessage(TextFormat.GOLD + "Chunks: " + TextFormat.RED + chunksCollected);
        sender.sendMessage(TextFormat.GOLD + "Entities: " + TextFormat.RED + entitiesCollected);
        sender.sendMessage(TextFormat.GOLD + "Block Entities: " + TextFormat.RED + tilesCollected);
        sender.sendMessage(TextFormat.GOLD + "Memory freed: " + TextFormat.RED + NukkitMath.round(freedMemory / 1024d / 1024d, 2) + " MB");
        return true;
    }
}
