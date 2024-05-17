package cn.nukkit.utils;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLava;
import cn.nukkit.entity.mob.EntityPhantom;
import cn.nukkit.entity.passive.EntityStrider;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;

/**
 * Base class of the default mob spawners
 */
public abstract class AbstractEntitySpawner implements EntitySpawner {

    protected SpawnerTask spawnTask;

    public AbstractEntitySpawner(SpawnerTask spawnTask) {
        this.spawnTask = spawnTask;
    }

    private static boolean isTooNearOfPlayer(Position pos) {
        for (Player p : pos.getLevel().getPlayers().values()) {
            if (p.distanceSquared(pos) < 196) { // 14 blocks
                return true;
            }
        }
        return false;
    }

    @Override
    public void spawn() {
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (isSpawningAllowed(player)) {
                spawnTo(player);
            }
        }
    }

    /**
     * Attempt to spawn a mob to a player
     *
     * @param player player
     */
    private void spawnTo(Player player) {
        Level level = player.getLevel();
        Position pos = new Position(player.getFloorX(), player.getFloorY(), player.getFloorZ(), level);

        if (SpawnerTask.entitySpawnAllowed(level, this.getEntityNetworkId(), player)) {
            if (getEntityNetworkId() == EntityPhantom.NETWORK_ID) {
                if (!level.isInSpawnRadius(pos)) { // Do not spawn mobs in the world spawn area
                    pos.x = pos.x + Utils.rand(-2, 2);
                    pos.y = pos.y + Utils.rand(20, 34);
                    pos.z = pos.z + Utils.rand(-2, 2);
                    spawn(player, pos, level);
                }
            } else {
                pos.x += SpawnerTask.getRandomSafeXZCoord(Utils.rand(48, 52), Utils.rand(24, 28), Utils.rand(4, 8));
                pos.z += SpawnerTask.getRandomSafeXZCoord(Utils.rand(48, 52), Utils.rand(24, 28), Utils.rand(4, 8));

                if (!level.isChunkLoaded((int) pos.x >> 4, (int) pos.z >> 4) || !level.isChunkGenerated((int) pos.x >> 4, (int) pos.z >> 4)) {
                    return;
                }

                if (level.isInSpawnRadius(pos)) {
                    return;
                }

                pos.y = SpawnerTask.getSafeYCoord(level, pos);
                if (pos.y < level.getMinBlockY() + 1 || pos.y > level.getMaxBlockY() || level.getDimension() == 1 && pos.y > 125.0) {
                    return;
                }

                if (AbstractEntitySpawner.isTooNearOfPlayer(pos)) {
                    return;
                }

                Block block = level.getBlock(pos, false);
                if (this.getEntityNetworkId() == EntityStrider.NETWORK_ID) {
                    if (!(block instanceof BlockLava)) {
                        return;
                    }
                } else {
                    if (block.getId() == Block.BROWN_MUSHROOM_BLOCK || block.getId() == Block.RED_MUSHROOM_BLOCK) { // Mushrooms aren't transparent but shouldn't have mobs spawned on them
                        return;
                    }

                    if (block.isTransparent() && block.getId() != Block.SNOW_LAYER) { // Snow layer is an exception
                        if ((block.getId() != Block.WATER && block.getId() != Block.STILL_WATER) || !this.isWaterMob()) { // Water mobs can spawn in water
                            return;
                        }
                    }
                }

                try {
                    spawn(player, pos, level);
                } catch (Exception e) {
                    Server.getInstance().getLogger().error("Error while spawning entity", e);
                }
            }
        }
    }

    /**
     * Checkif mob spawning is allowed in the world the player is in
     *
     * @param player player
     * @return mob spawning allowed
     */
    private boolean isSpawningAllowed(Player player) {
        if (player.isSpectator()) {
            return false;
        }
        if (!player.getLevel().isMobSpawningAllowed() || Utils.rand(1, 4) == 1) {
            return false;
        }
        if (Server.getInstance().getDifficulty() == 0) {
            return !Utils.monstersList.contains(this.getEntityNetworkId());
        }
        return true;
    }
}
