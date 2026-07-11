package cn.nukkit.block.util;

import cn.nukkit.block.*;
import cn.nukkit.level.Level;
import cn.nukkit.level.persistence.PersistentDataContainer;
import cn.nukkit.level.persistence.PersistentDataType;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;

/**
 * 持久跟踪玩家手动切换的门、栅栏门和活板门，避免红石更新错误地对齐其状态。
 * <p>
 * Persistently tracks doors, fence gates and trapdoors toggled manually by players so redstone
 * updates do not forcibly realign their state.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public final class RedstoneToggleHelper {

    private static final String MANUAL_OVERRIDE_KEY = "nukkit-mot:redstone_manual_override";

    private RedstoneToggleHelper() {
    }

    /**
     * 检查机制方块的直接供电；紧邻的已充能红石粉可驱动机制，但不改变普通方块的弱充能规则。
     * <p>Checks direct mechanism power; adjacent powered wire drives mechanisms without changing ordinary weak-power rules.
     */
    public static boolean isPowered(Level level, Vector3 position) {
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = level.getBlock(position.getSideVec(face));
            if (neighbor instanceof BlockRedstoneWire && neighbor.getDamage() > 0) {
                return true;
            }
        }
        return level.isBlockPowered(position);
    }

    public static boolean isManualOverride(Level level, int x, int y, int z) {
        if (level == null) {
            return false;
        }
        Boolean override = getPersistentDataContainer(level, x, y, z).get(MANUAL_OVERRIDE_KEY, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(override);
    }

    public static void setManualOverride(Level level, int x, int y, int z, boolean value) {
        if (level == null) {
            return;
        }
        PersistentDataContainer container = getPersistentDataContainer(level, x, y, z);
        if (value) {
            container.set(MANUAL_OVERRIDE_KEY, PersistentDataType.BOOLEAN, true);
        } else {
            container.remove(MANUAL_OVERRIDE_KEY);
        }
    }

    private static PersistentDataContainer getPersistentDataContainer(Level level, int x, int y, int z) {
        return level.getPersistentDataContainer(new Vector3(x, y, z));
    }

    /**
     * 方块变更回调：受管方块被替换时清理 override，仅为状态更新及铜系变体转换保留。
     * 不依赖 onBreak()，从而覆盖凋零、爆炸、插件 setBlock 等所有替换路径。
     * <p>
     * Block-change callback: clears the override when a managed block is replaced, preserving it only
     * for state updates and copper variant transitions.
     */
    public static void onBlockChanged(Block previous, Block current) {
        if (previous == null || current == null) {
            return;
        }
        if (isManagedBlock(previous) && !shouldPreserveManualOverride(previous, current)) {
            setManualOverride(previous.getLevel(), previous.getFloorX(), previous.getFloorY(), previous.getFloorZ(), false);
        }
    }

    private static boolean shouldPreserveManualOverride(Block previous, Block current) {
        if (previous.getId() == current.getId()) {
            return true;
        }
        if (previous.getDamage() != current.getDamage()) {
            return false;
        }
        return previous instanceof BlockDoorCopper && current instanceof BlockDoorCopper
                || previous instanceof BlockTrapdoorCopper && current instanceof BlockTrapdoorCopper;
    }

    private static boolean isManagedBlock(Block block) {
        return block instanceof BlockDoor
                || block instanceof BlockFenceGate
                || block instanceof BlockTrapdoor;
    }
}
