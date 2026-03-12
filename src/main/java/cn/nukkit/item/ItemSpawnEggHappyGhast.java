package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockMobSpawner;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawner;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityHappyGhast;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.concurrent.ThreadLocalRandom;

public class ItemSpawnEggHappyGhast extends StringItemBase {

    public ItemSpawnEggHappyGhast() {
        super(ItemNamespaceId.HAPPY_GHAST_SPAWN_EGG, "Happy Ghast Spawn Egg");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_90;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Level level, Player player, Block block, Block target, BlockFace face, double fx, double fy, double fz) {
        if (player.isAdventure()) {
            return false;
        }

        if (!Server.getInstance().spawnEggsEnabled) {
            player.sendMessage("§cSpawn eggs are disabled on this server");
            return false;
        }

        if (target instanceof BlockMobSpawner) {
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity instanceof BlockEntitySpawner) {
                if (((BlockEntitySpawner) blockEntity).getSpawnEntityType() != this.getDamage()) {
                    ((BlockEntitySpawner) blockEntity).setSpawnEntityType(EntityHappyGhast.NETWORK_ID);

                    if (!player.isCreative()) {
                        player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
                    }
                }
            } else {
                if (blockEntity != null) {
                    blockEntity.close();
                }

                CompoundTag nbt = new CompoundTag()
                        .putString("id", BlockEntity.MOB_SPAWNER)
                        .putInt("EntityId", EntityHappyGhast.NETWORK_ID)
                        .putInt("x", (int) target.x)
                        .putInt("y", (int) target.y)
                        .putInt("z", (int) target.z);
                BlockEntity.createBlockEntity(BlockEntity.MOB_SPAWNER, level.getChunk(target.getChunkX(), target.getChunkZ()), nbt);

                if (!player.isCreative()) {
                    player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
                }
            }

            return true;
        }

        FullChunk chunk = level.getChunk((int) block.getX() >> 4, (int) block.getZ() >> 4);

        if (chunk == null) {
            return false;
        }

        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", block.getX() + 0.5))
                        .add(new DoubleTag("", target.getBoundingBox() == null ? block.getY() : target.getBoundingBox().getMaxY() + 0.0001f))
                        .add(new DoubleTag("", block.getZ() + 0.5)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", ThreadLocalRandom.current().nextFloat() * 360))
                        .add(new FloatTag("", 0)));

        if (this.hasCustomName()) {
            nbt.putString("CustomName", this.getCustomName());
        }

        CreatureSpawnEvent ev = new CreatureSpawnEvent(this.meta, block, nbt, CreatureSpawnEvent.SpawnReason.SPAWN_EGG, player);
        level.getServer().getPluginManager().callEvent(ev);

        if (ev.isCancelled()) {
            return false;
        }

        Entity entity = Entity.createEntity("HappyGhast", chunk, nbt);

        if (entity != null) {
            if (!player.isCreative()) {
                player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            }

            entity.spawnToAll();

            return true;
        }
        return false;
    }
}