package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.*;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.player.PlayerBucketEmptyEvent;
import cn.nukkit.event.player.PlayerBucketFillEvent;
import cn.nukkit.event.player.PlayerItemConsumeEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.ExplodeParticle;
import cn.nukkit.level.sound.FizzSound;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockFace.Plane;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.UpdateBlockPacket;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemBucket extends Item {

    public static final int EMPTY_BUCKET = 0;
    public static final int MILK_BUCKET = 1;
    public static final int COD_BUCKET = 2;
    public static final int SALMON_BUCKET = 3;
    public static final int TROPICAL_FISH_BUCKET = 4;
    public static final int PUFFERFISH_BUCKET = 5;
    public static final int WATER_BUCKET = 8;
    public static final int LAVA_BUCKET = 10;
    public static final int POWDER_SNOW_BUCKET = 11;
    public static final int AXOLOTL_BUCKET = 12;
    public static final int TADPOLE_BUCKET = 13;
    public static final int UNDEFINED_BUCKET = 14;

    public ItemBucket() {
        this(0, 1);
    }

    public ItemBucket(Integer meta) {
        this(meta, 1);
    }

    public ItemBucket(Integer meta, int count) {
        super(BUCKET, meta, count, getName(meta));
    }

    protected static String getName(int meta) {
        return switch (meta) {
            case MILK_BUCKET -> "Milk";
            case COD_BUCKET -> "Bucket of Cod";
            case SALMON_BUCKET -> "Bucket of Salmon";
            case TROPICAL_FISH_BUCKET -> "Bucket of Tropical Fish";
            case PUFFERFISH_BUCKET -> "Bucket of Pufferfish";
            case WATER_BUCKET -> "Water Bucket";
            case LAVA_BUCKET -> "Lava Bucket";
            case POWDER_SNOW_BUCKET -> "Powder Snow Bucket";
            case AXOLOTL_BUCKET -> "Bucket of Axolotl";
            case TADPOLE_BUCKET -> "Bucket of Tadpoles";
            default -> "Bucket";
        };
    }

    public static int getDamageByTarget(int target) {
        return switch (target) {
            case COD_BUCKET, SALMON_BUCKET, TROPICAL_FISH_BUCKET, PUFFERFISH_BUCKET,
                 WATER_BUCKET, AXOLOTL_BUCKET, TADPOLE_BUCKET -> BlockID.WATER;
            case POWDER_SNOW_BUCKET -> BlockID.POWDER_SNOW;
            case LAVA_BUCKET -> BlockID.LAVA;
            default -> 0;
        };
    }

    @Override
    public int getMaxStackSize() {
        return this.meta == EMPTY_BUCKET ? 16 : 1;
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

        Block targetBlock = Block.get(getDamageByTarget(this.meta));

        if (targetBlock instanceof BlockAir) {
            if (!(target instanceof BlockLiquid) || target.getDamage() != 0) {
                target = target.getLevelBlockAtLayer(1);
            }
            if (!(target instanceof BlockLiquid) || target.getDamage() != 0) {
                target = block;
            }
            if (!(target instanceof BlockLiquid) || target.getDamage() != 0) {
                target = block.getLevelBlockAtLayer(1);
            }
            if (target instanceof BlockLiquid && target.getDamage() == 0) {
                Item result = Item.get(BUCKET, getDamageByTarget(target.getId()), 1);
                PlayerBucketFillEvent ev;
                player.getServer().getPluginManager().callEvent(ev = new PlayerBucketFillEvent(player, block, face, this, result));
                if (!ev.isCancelled()) {
                    player.getLevel().setBlock(target, target.layer, Block.get(BlockID.AIR), true, true);

                    // When water is removed ensure any adjacent still water is
                    // replaced with water that can flow.
                    for (BlockFace side : Plane.HORIZONTAL) {
                        Block b = target.getSideAtLayer(0, side);
                        if (b.getId() == STILL_WATER) {
                            level.setBlock(b, Block.get(BlockID.WATER));
                        }
                    }

                    if (player.isSurvival()) {
                        if (this.getCount() - 1 <= 0) {
                            player.getInventory().setItemInHand(ev.getItem());
                        } else {
                            Item clone = this.clone();
                            clone.setCount(this.getCount() - 1);
                            player.getInventory().setItemInHand(clone);
                            if (player.getInventory().canAddItem(ev.getItem())) {
                                player.getInventory().addItem(ev.getItem());
                            } else {
                                player.dropItem(ev.getItem());
                            }
                        }
                    }

                    if (target instanceof BlockLava) {
                        level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_BUCKET_FILL_LAVA);
                    } else {
                        level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_BUCKET_FILL_WATER);
                    }

                    return true;
                } else {
                    player.setNeedSendInventory(true);
                }
            }
        } else if (targetBlock instanceof BlockLiquid) {
            Item result = Item.get(BUCKET, 0, 1);
            boolean usesWaterlogging = ((BlockLiquid) targetBlock).usesWaterLogging();
            Block placementBlock;
            if (usesWaterlogging) {
                if (block.getId() == BlockID.BAMBOO) {
                    placementBlock = block;
                } else if (target.getWaterloggingType() != Block.WaterloggingType.NO_WATERLOGGING) {
                    placementBlock = target.getLevelBlockAtLayer(1);
                } else if (block.getWaterloggingType() != Block.WaterloggingType.NO_WATERLOGGING) {
                    placementBlock = block.getLevelBlockAtLayer(1);
                } else {
                    placementBlock = block;
                }
            } else {
                placementBlock = block;
            }

            PlayerBucketEmptyEvent ev = new PlayerBucketEmptyEvent(player, block, face, this, result, true);
            boolean canBeFlowedInto = placementBlock.canBeFlowedInto() || placementBlock.getId() == BlockID.BAMBOO;
            if (usesWaterlogging) {
                ev.setCancelled(placementBlock.getWaterloggingType() == Block.WaterloggingType.NO_WATERLOGGING && !canBeFlowedInto);
            } else {
                ev.setCancelled(!canBeFlowedInto);
            }

            if (!block.canBeFlowedInto()) {
                ev.setCancelled(true);
            }

            boolean nether = false;
            if (player.getLevel().getDimension() == Level.DIMENSION_NETHER && this.getDamage() != 10) {
                ev.setCancelled(true);
                nether = true;
            }

            player.getServer().getPluginManager().callEvent(ev);

            if (!ev.isCancelled()) {
                player.getLevel().setBlock(placementBlock, placementBlock.layer, targetBlock, true, true);
                if (player.isSurvival()) {
                    if (this.getCount() - 1 <= 0) {
                        player.getInventory().setItemInHand(ev.getItem());
                    } else {
                        Item clone = this.clone();
                        clone.setCount(this.getCount() - 1);
                        player.getInventory().setItemInHand(clone);
                        if (player.getInventory().canAddItem(ev.getItem())) {
                            player.getInventory().addItem(ev.getItem());
                        } else {
                            player.dropItem(ev.getItem());
                        }
                    }
                }

                if (this.getDamage() == 10) {
                    level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_BUCKET_EMPTY_LAVA);
                } else {
                    level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_BUCKET_EMPTY_WATER);
                }

                if (Server.getInstance().mobsFromBlocks && ev.isMobSpawningAllowed()) {
                    String entityName = switch (this.getDamage()) {
                        case COD_BUCKET -> "Cod";
                        case SALMON_BUCKET -> "Salmon";
                        case TROPICAL_FISH_BUCKET -> "TropicalFish";
                        case PUFFERFISH_BUCKET -> "Pufferfish";
                        case AXOLOTL_BUCKET -> "Axolotl";
                        case TADPOLE_BUCKET -> "Tadpole";
                        default -> null;
                    };

                    if (entityName != null) {
                        Entity entity = Entity.createEntity(entityName, block.add(0.5, 0, 0.5));
                        if (entity != null) entity.spawnToAll();
                    }
                }

                return true;
            } else checkNether(player, block, target, nether);
        } else {
            if (block.getId() != 0 && !(block instanceof BlockLiquid) && !(block instanceof BlockSnowLayer)) {
                return false;
            }

            if (target instanceof BlockSnowLayer) {
                block.setY(block.getY() - 1);
            }

            Item result = Item.get(BUCKET, 0, 1);

            PlayerBucketEmptyEvent ev = new PlayerBucketEmptyEvent(player, block, face, this, result, true);

            boolean nether = false;
            if (player.getLevel().getDimension() == Level.DIMENSION_NETHER && this.getDamage() != 10) {
                ev.setCancelled(true);
                nether = true;
            }

            player.getServer().getPluginManager().callEvent(ev);

            if (!ev.isCancelled()) {
                player.getLevel().setBlock(block, 0, targetBlock, true, true);
                if (player.isSurvival()) {
                    if (this.getCount() - 1 <= 0) {
                        player.getInventory().setItemInHand(ev.getItem());
                    } else {
                        Item clone = this.clone();
                        clone.setCount(this.getCount() - 1);
                        player.getInventory().setItemInHand(clone);
                        if (player.getInventory().canAddItem(ev.getItem())) {
                            player.getInventory().addItem(ev.getItem());
                        } else {
                            player.dropItem(ev.getItem());
                        }
                    }
                }

                if (this.getDamage() == 10) {
                    level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_BUCKET_EMPTY_LAVA);
                } else {
                    level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_BUCKET_EMPTY_WATER);
                }

                return true;
            } else {
                checkNether(player, block, target, nether);
            }
        }

        return false;
    }

    private void checkNether(Player player, Block block, Block target, boolean nether) {
        if (nether) {
            if (!player.isCreative()) {
                this.setDamage(0); // Empty bucket
                player.getInventory().setItemInHand(this);
            }
            player.getLevel().addSound(new FizzSound(target, 2.6F + (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 0.8F));
            player.getLevel().addParticle(new ExplodeParticle(target.add(0.5, 1, 0.5)));
        } else {
            player.getLevel().sendBlocks(new Player[]{player}, new Block[]{block.getLevelBlockAtLayer(1)}, UpdateBlockPacket.FLAG_ALL_PRIORITY, 1); //TODO: maybe not here
            player.setNeedSendInventory(true);
        }
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return this.getDamage() == MILK_BUCKET;
    }

    @Override
    public boolean onUse(Player player, int ticksUsed) {
        if (player.isSpectator() || this.getDamage() != MILK_BUCKET) {
            return false;
        }

        PlayerItemConsumeEvent consumeEvent = new PlayerItemConsumeEvent(player, this);

        player.getServer().getPluginManager().callEvent(consumeEvent);
        if (consumeEvent.isCancelled()) {
            player.setNeedSendInventory(true);
            return false;
        }

        if (!player.isCreative()) {
            player.getInventory().setItemInHand(Item.get(Item.BUCKET));
        }

        player.removeAllEffects(EntityPotionEffectEvent.Cause.MILK);
        return true;
    }

    @Override
    public boolean canRelease() {
        return this.getDamage() == MILK_BUCKET;
    }
}
