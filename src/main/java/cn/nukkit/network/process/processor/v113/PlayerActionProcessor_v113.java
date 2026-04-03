package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockFire;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.event.player.PlayerToggleSneakEvent;
import cn.nukkit.event.player.PlayerToggleSprintEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArrow;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.RespawnPacket;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * v113 (1.1) 专用的 PlayerActionPacket 处理器。
 * 在v113协议中，动作ID与高版本不同：
 * - ACTION_RELEASE_ITEM = 5 (高版本是 ACTION_START_SLEEPING)
 * - ACTION_STOP_SLEEPING = 6
 * - ACTION_RESPAWN = 7
 *
 * @author LT_Name
 */
public class PlayerActionProcessor_v113 extends DataPacketProcessor<PlayerActionPacket> {

    public static final PlayerActionProcessor_v113 INSTANCE = new PlayerActionProcessor_v113();

    // v113协议中的动作ID常量
    private static final int ACTION_RELEASE_ITEM_V113 = 5;
    private static final int ACTION_STOP_SLEEPING_V113 = 6;
    private static final int ACTION_RESPAWN_V113 = 7;

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull PlayerActionPacket pk) {
        Player player = playerHandle.player;
        if (!player.spawned || (!player.isAlive() && pk.action != PlayerActionPacket.ACTION_RESPAWN && pk.action != PlayerActionPacket.ACTION_DIMENSION_CHANGE_SUCCESS)) {
            return;
        }

        pk.entityId = player.getId();
        Vector3 pos = new Vector3(pk.x, pk.y, pk.z);
        BlockFace face = BlockFace.fromIndex(pk.face);

        switch (pk.action) {
            case PlayerActionPacket.ACTION_START_BREAK:
                if (player.lastBreak != Long.MAX_VALUE || pos.distanceSquared(player) > 10000) {
                    break;
                }
                Block target = player.level.getBlock(pos);
                PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(player, player.getInventory().getItemInHand(), target, face, target.getId() == 0 ? PlayerInteractEvent.Action.LEFT_CLICK_AIR : PlayerInteractEvent.Action.LEFT_CLICK_BLOCK);
                player.getServer().getPluginManager().callEvent(playerInteractEvent);
                if (playerInteractEvent.isCancelled()) {
                    player.getInventory().sendHeldItem(player);
                    break;
                }
                Block block = target.getSide(face);
                if (block.getId() == BlockID.FIRE) {
                    player.level.setBlock(block, new BlockAir(), true);
                    break;
                }
                if (!player.isCreative()) {
                    double breakTime = Math.ceil(target.getBreakTime(player.getInventory().getItemInHand(), player) * 20);
                    if (breakTime > 0) {
                        LevelEventPacket levelEventPk = new LevelEventPacket();
                        levelEventPk.evid = LevelEventPacket.EVENT_BLOCK_START_BREAK;
                        levelEventPk.x = (float) pos.x;
                        levelEventPk.y = (float) pos.y;
                        levelEventPk.z = (float) pos.z;
                        levelEventPk.data = (int) (65535 / breakTime);
                        player.getLevel().addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, levelEventPk);
                    }
                }
                player.lastBreak = System.currentTimeMillis();
                break;

            case PlayerActionPacket.ACTION_ABORT_BREAK:
                player.lastBreak = Long.MAX_VALUE;
                // fallthrough
            case PlayerActionPacket.ACTION_STOP_BREAK:
                LevelEventPacket stopBreakPk = new LevelEventPacket();
                stopBreakPk.evid = LevelEventPacket.EVENT_BLOCK_STOP_BREAK;
                stopBreakPk.x = (float) pos.x;
                stopBreakPk.y = (float) pos.y;
                stopBreakPk.z = (float) pos.z;
                stopBreakPk.data = 0;
                player.getLevel().addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, stopBreakPk);
                break;

            case ACTION_RELEASE_ITEM_V113:
                if (playerHandle.getStartAction() > -1 && player.getDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION)) {
                    if (player.getInventory().getItemInHand().getId() == Item.BOW) {
                        Item bow = player.getInventory().getItemInHand();
                        ItemArrow itemArrow = new ItemArrow();
                        if (player.isSurvival() && !player.getInventory().contains(itemArrow)) {
                            player.getInventory().sendContents(player);
                            break;
                        }

                        double damage = 2;
                        boolean flame = false;

                        if (bow.hasEnchantments()) {
                            Enchantment bowDamage = bow.getEnchantment(Enchantment.ID_BOW_POWER);
                            if (bowDamage != null && bowDamage.getLevel() > 0) {
                                damage += 0.25 * (bowDamage.getLevel() + 1);
                            }

                            Enchantment flameEnchant = bow.getEnchantment(Enchantment.ID_BOW_FLAME);
                            flame = flameEnchant != null && flameEnchant.getLevel() > 0;
                        }

                        CompoundTag nbt = new CompoundTag()
                                .putList(new ListTag<DoubleTag>("Pos")
                                        .add(new DoubleTag("", player.x))
                                        .add(new DoubleTag("", player.y + player.getEyeHeight()))
                                        .add(new DoubleTag("", player.z)))
                                .putList(new ListTag<DoubleTag>("Motion")
                                        .add(new DoubleTag("", -Math.sin(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", -Math.sin(player.pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", Math.cos(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI))))
                                .putList(new ListTag<FloatTag>("Rotation")
                                        .add(new FloatTag("", (player.yaw > 180 ? 360 : 0) - (float) player.yaw))
                                        .add(new FloatTag("", (float) -player.pitch)))
                                .putShort("Fire", player.isOnFire() || flame ? 45 * 60 : 0)
                                .putDouble("damage", damage);

                        int diff = (player.getServer().getTick() - playerHandle.getStartAction());
                        double p = (double) diff / 20;

                        double f = Math.min((p * p + p * 2) / 3, 1) * 2;
                        
                        Entity projectile = Entity.createEntity("Arrow", player.level.getChunk(player.getChunkX(), player.getChunkZ()), nbt, player, f == 2);
                        if (projectile == null) {
                            player.getInventory().sendContents(player);
                            break;
                        }
                        
                        EntityShootBowEvent entityShootBowEvent = new EntityShootBowEvent(player, bow, (cn.nukkit.entity.projectile.EntityProjectile) projectile, f);

                        if (f < 0.1 || diff < 5) {
                            entityShootBowEvent.setCancelled();
                        }

                        player.getServer().getPluginManager().callEvent(entityShootBowEvent);
                        if (entityShootBowEvent.isCancelled()) {
                            entityShootBowEvent.getProjectile().close();
                            player.getInventory().sendContents(player);
                        } else {
                            entityShootBowEvent.getProjectile().setMotion(entityShootBowEvent.getProjectile().getMotion().multiply(entityShootBowEvent.getForce()));
                            if (player.isSurvival()) {
                                Enchantment infinity;
                                if (!bow.hasEnchantments() || (infinity = bow.getEnchantment(Enchantment.ID_BOW_INFINITY)) == null || infinity.getLevel() <= 0) {
                                    player.getInventory().removeItem(itemArrow);
                                }

                                if (!bow.isUnbreakable()) {
                                    Enchantment durability = bow.getEnchantment(Enchantment.ID_DURABILITY);
                                    if (!(durability != null && durability.getLevel() > 0 && (100 / (durability.getLevel() + 1)) <= new Random().nextInt(100))) {
                                        bow.setDamage(bow.getDamage() + 1);
                                        if (bow.getDamage() >= 385) {
                                            player.getInventory().setItemInHand(new Item(0));
                                        } else {
                                            player.getInventory().setItemInHand(bow);
                                        }
                                    }
                                }
                            }
                            if (entityShootBowEvent.getProjectile() instanceof EntityArrow) {
                                entityShootBowEvent.getProjectile().spawnToAll();
                                player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_BOW);
                            } else {
                                entityShootBowEvent.getProjectile().spawnToAll();
                            }
                        }
                    }
                }

                player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
                playerHandle.setStartAction(-1);
                break;

            case PlayerActionPacket.ACTION_STOP_SLEEPING:
                player.stopSleep();
                break;

            case PlayerActionPacket.ACTION_RESPAWN:
                if (!player.spawned || player.isAlive() || !player.isOnline()) {
                    break;
                }

                if (player.getServer().isHardcore()) {
                    player.setBanned(true);
                    break;
                }

                player.craftingType = Player.CRAFTING_SMALL;

                PlayerRespawnEvent playerRespawnEvent = new PlayerRespawnEvent(player, player.getSpawn());
                player.getServer().getPluginManager().callEvent(playerRespawnEvent);

                Position respawnPos = playerRespawnEvent.getRespawnPosition();

                player.teleport(respawnPos, null);

                RespawnPacket respawnPacket = new RespawnPacket();
                respawnPacket.x = (float) respawnPos.x;
                respawnPacket.y = (float) respawnPos.y;
                respawnPacket.z = (float) respawnPos.z;
                player.dataPacket(respawnPacket);

                player.setSprinting(false, true);
                player.setSneaking(false);

                player.extinguish();
                player.setDataProperty(new cn.nukkit.entity.data.ShortEntityData(Player.DATA_AIR, 400), false);
                player.deadTicks = 0;
                player.noDamageTicks = 60;

                player.removeAllEffects();
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setLevel(20, 20);

                player.sendData(player);

                player.setMovementSpeed(Player.DEFAULT_SPEED);

                player.getAdventureSettings().update();
                player.getInventory().sendContents(player);
                player.getInventory().sendArmorContents(player);

                player.spawnToAll();
                player.scheduleUpdate();
                break;

            case PlayerActionPacket.ACTION_JUMP:
                break;

            case PlayerActionPacket.ACTION_START_SPRINT:
                PlayerToggleSprintEvent startSprintEvent = new PlayerToggleSprintEvent(player, true);
                player.getServer().getPluginManager().callEvent(startSprintEvent);
                if (!startSprintEvent.isCancelled()) {
                    player.setSprinting(true);
                }
                break;

            case PlayerActionPacket.ACTION_STOP_SPRINT:
                PlayerToggleSprintEvent stopSprintEvent = new PlayerToggleSprintEvent(player, false);
                player.getServer().getPluginManager().callEvent(stopSprintEvent);
                if (!stopSprintEvent.isCancelled()) {
                    player.setSprinting(false);
                }
                break;

            case PlayerActionPacket.ACTION_START_SNEAK:
                PlayerToggleSneakEvent startSneakEvent = new PlayerToggleSneakEvent(player, true);
                player.getServer().getPluginManager().callEvent(startSneakEvent);
                if (!startSneakEvent.isCancelled()) {
                    player.setSneaking(true);
                }
                break;

            case PlayerActionPacket.ACTION_STOP_SNEAK:
                PlayerToggleSneakEvent stopSneakEvent = new PlayerToggleSneakEvent(player, false);
                player.getServer().getPluginManager().callEvent(stopSneakEvent);
                if (!stopSneakEvent.isCancelled()) {
                    player.setSneaking(false);
                }
                break;

            case PlayerActionPacket.ACTION_CREATIVE_PLAYER_DESTROY_BLOCK:
                if (!player.isCreative()) {
                    break;
                }
                // In creative mode, we don't need to check for break time
                Block creativeTarget = player.level.getBlock(pos);
                Block creativeBlock = creativeTarget.getSide(face);
                if (creativeBlock.getId() == BlockID.FIRE) {
                    player.level.setBlock(creativeBlock, new BlockAir(), true);
                }
                break;

            case PlayerActionPacket.ACTION_DIMENSION_CHANGE_SUCCESS:
                // Client acknowledges dimension change
                break;

            case PlayerActionPacket.ACTION_START_GLIDE:
                player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_GLIDING, true);
                break;

            case PlayerActionPacket.ACTION_STOP_GLIDE:
                player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_GLIDING, false);
                break;

            case PlayerActionPacket.ACTION_CONTINUE_BREAK:
                break;

            case PlayerActionPacket.ACTION_START_SWIMMING:
                player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_SWIMMING, true);
                break;

            case PlayerActionPacket.ACTION_STOP_SWIMMING:
                player.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_SWIMMING, false);
                break;
        }
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(PlayerActionPacket.NETWORK_ID);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return PlayerActionPacket.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
