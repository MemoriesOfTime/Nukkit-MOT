package cn.nukkit.dispenser;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDispenser;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityArmorStand;
import cn.nukkit.inventory.EntityArmorInventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.Collection;

public class ArmorDispenseBehavior extends DefaultDispenseBehavior {

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        Block target = block.getSide(face);

        for (Entity entity : block.getLevel().getNearbyEntities(new SimpleAxisAlignedBB(
                target.x,
                target.y,
                target.z,
                target.x + 1,
                target.y + 1,
                target.z + 1
        ))) {
            if (entity instanceof EntityArmorStand armorStand) {
                if (tryEquipArmorStand(armorStand, item)) {
                    return null;
                }
            } else if (entity instanceof Player player) {
                if (tryEquipPlayer(player, item)) {
                    return null;
                }
            }
        }

        return super.dispense(block, face, item);
    }

    private boolean tryEquipArmorStand(EntityArmorStand armorStand, Item item) {
        if (!armorStand.isAlive()) {
            return false;
        }
        int slot = getArmorSlot(item);
        EntityArmorInventory inventory = armorStand.getInventory();
        if (!inventory.getItemFast(slot).isNull()) {
            return false;
        }
        Item itemToAdd = item.clone();
        itemToAdd.setCount(1);
        inventory.setItem(slot, itemToAdd);
        Collection<Player> viewers = armorStand.getViewers().values();
        inventory.sendContents(viewers.toArray(Player.EMPTY_ARRAY));
        playEquipSound(armorStand, item);
        return true;
    }

    private boolean tryEquipPlayer(Player player, Item item) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        Item current;
        Item itemToAdd = item.clone();
        itemToAdd.setCount(1);
        if (item.canBePutInHelmetSlot()) {
            current = inventory.getHelmetFast();
            if (!current.isNull()) {
                return false;
            }
            if (!inventory.setHelmet(itemToAdd)) {
                return false;
            }
        } else if (item.isChestplate()) {
            current = inventory.getChestplateFast();
            if (!current.isNull()) {
                return false;
            }
            if (!inventory.setChestplate(itemToAdd)) {
                return false;
            }
        } else if (item.isLeggings()) {
            current = inventory.getLeggingsFast();
            if (!current.isNull()) {
                return false;
            }
            if (!inventory.setLeggings(itemToAdd)) {
                return false;
            }
        } else if (item.isBoots()) {
            current = inventory.getBootsFast();
            if (!current.isNull()) {
                return false;
            }
            if (!inventory.setBoots(itemToAdd)) {
                return false;
            }
        } else {
            return false;
        }
        playEquipSound(player, item);
        return true;
    }

    private static int getArmorSlot(Item armorItem) {
        if (armorItem.canBePutInHelmetSlot()) {
            return EntityArmorInventory.SLOT_HEAD;
        } else if (armorItem.isChestplate()) {
            return EntityArmorInventory.SLOT_CHEST;
        } else if (armorItem.isLeggings()) {
            return EntityArmorInventory.SLOT_LEGS;
        } else {
            return EntityArmorInventory.SLOT_FEET;
        }
    }

    private static void playEquipSound(Entity entity, Item item) {
        int sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_GENERIC;
        if (item instanceof ItemArmor armor) {
            switch (armor.getTier()) {
                case ItemArmor.TIER_CHAIN -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_CHAIN;
                case ItemArmor.TIER_DIAMOND -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_DIAMOND;
                case ItemArmor.TIER_GOLD -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_GOLD;
                case ItemArmor.TIER_IRON -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_IRON;
                case ItemArmor.TIER_LEATHER -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_LEATHER;
                case ItemArmor.TIER_NETHERITE -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_NETHERITE;
                case ItemArmor.TIER_COPPER -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_COPPER;
                default -> sound = LevelSoundEventPacket.SOUND_ARMOR_EQUIP_GENERIC;
            }
        }
        entity.getLevel().addLevelSoundEvent(entity, sound);
    }
}
