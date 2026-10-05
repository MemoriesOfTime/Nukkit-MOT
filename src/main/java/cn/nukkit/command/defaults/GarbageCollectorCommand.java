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
        // 超时跳过的世界：其统计不计入（任务在线程恢复后仍会执行），输出须明示而非静默计 0
        // Levels skipped on timeout: their stats are excluded (the queued task still runs
        // once the thread recovers); say so instead of silently reporting zeros
        java.util.List<String> skippedLevels = new java.util.ArrayList<>();
        // 主线程等待总预算：串行等每个卡死世界 5s 会拖过 Watchdog 阈值，单世界冻结放大成整服被关
        // Serial 5s waits per wedged level would cross the Watchdog threshold and turn one frozen level into a whole-server kill
        final long waitDeadline = System.currentTimeMillis() + 30_000L;
        boolean primaryCaller = sender.getServer().isPrimaryThread();

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
            if (Thread.currentThread() == level.getLevelThread()) {
                collection.run();
            } else if (primaryCaller) {
                if (!level.isParallelTickEnabled()) {
                    // 非并行目标在主线程内联（master 语义），与主线程 doTick 串行
                    collection.run();
                } else {
                    CompletableFuture<Void> future = level.scheduleSyncTaskAndWait(collection);
                    long budget = waitDeadline - System.currentTimeMillis();
                    try {
                        if (budget <= 0) {
                            sender.getServer().getLogger().error("Level thread for '" + level.getName()
                                    + "' not waited for: GC wait budget exhausted; skipping GC for this level");
                            skippedLevels.add(level.getName() + " (budget exhausted)");
                        } else {
                            future.get(Math.min(5_000L, budget), TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException e) {
                        // 中断（通常为关服）：不内联跨 owner 执行，放弃本轮并跳过剩余世界
                        Thread.currentThread().interrupt();
                        sender.getServer().getLogger().warning("Interrupted while waiting for level GC; skipping GC for '"
                                + level.getName() + "' and remaining levels");
                        skippedLevels.add(level.getName() + " (interrupted)");
                        break;
                    } catch (ExecutionException e) {
                        sender.getServer().getLogger().logException(e.getCause());
                    } catch (TimeoutException e) {
                        // GC 为维护性操作：超时放弃该世界本轮，排队任务在线程恢复后自行执行
                        sender.getServer().getLogger().error("Level thread for '" + level.getName()
                                + "' did not run garbage collection within 5s; skipping GC for this level");
                        skippedLevels.add(level.getName());
                    }
                }
            } else {
                // 其他世界线程上只投递：内联会与属主/主线程接管并发，等待会互等死锁
                // On another level's thread queue only: inlining races the owner, waiting deadlocks mutual waits
                level.scheduleSyncTask(collection);
                skippedLevels.add(level.getName() + " (queued)");
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
        if (!skippedLevels.isEmpty()) {
            sender.sendMessage(TextFormat.GOLD + "Skipped (level thread busy, GC runs there later): " + TextFormat.RED
                    + String.join(", ", skippedLevels));
        }
        return true;
    }
}
