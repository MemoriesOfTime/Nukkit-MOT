package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.potion.Potion;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemBow extends ItemTool {

    public ItemBow() {
        this(0, 1);
    }

    public ItemBow(Integer meta) {
        this(meta, 1);
    }

    public ItemBow(Integer meta, int count) {
        this(BOW, meta, count, "Bow");
    }

    public ItemBow(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_BOW;
    }

    @Override
    public int getEnchantAbility() {
        return 1;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return player.isCreative() || (this.getArrow(player.getInventory()) != null || this.getArrow(player.getOffhandInventory()) != null);
    }

    @Override
    public boolean canRelease() {
        return true;
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        Item itemArrow = Item.get(Item.ARROW, 0, 1);

        Inventory inventory = player.getOffhandInventory();

        if ((itemArrow = this.getArrow(inventory)) == null &&
                (itemArrow = this.getArrow(inventory = player.getInventory())) == null) {
            if (player.isCreative()) {
                itemArrow = Item.get(Item.ARROW, 0, 1);
            } else {
                player.getOffhandInventory().sendContents(player);
                inventory.sendContents(player);
                return false;
            }
        }

        double damage = 2;
        Enchantment bowDamage = this.getEnchantment(Enchantment.ID_BOW_POWER);
        if (bowDamage != null && bowDamage.getLevel() > 0) {
            damage += (double) bowDamage.getLevel() * 0.5 + 0.5;
        }

        Enchantment flameEnchant = this.getEnchantment(Enchantment.ID_BOW_FLAME);
        boolean flame = flameEnchant != null && flameEnchant.getLevel() > 0;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector3 dir = Vector3.directionFromRotation(player.pitch, player.yaw)
                .add(0.0075 * random.nextGaussian(), 0.0075 * random.nextGaussian(), 0.0075 * random.nextGaussian());
        CompoundTag nbt = Entity.getDefaultNBT(player.getEyePosition(), dir.multiply(1.2), (float) dir.yRotFromDirection(), (float) dir.xRotFromDirection())
                .putShort("Fire", flame ? 45 * 60 : 0)
                .putDouble("damage", damage)
                .putByte("auxValue", itemArrow.getDamage())
                .putCompound("item", new CompoundTag()
                        .putInt("id", itemArrow.getId())
                        .putInt("Damage", itemArrow.getDamage())
                        .putInt("Count", 1));

        if (itemArrow.hasCompoundTag()) {
            nbt.getCompound("item").putCompound("tag", itemArrow.getNamedTag());
        }

        if (itemArrow.getDamage() != ItemArrow.NORMAL_ARROW) {
            Potion potion = Potion.getPotion(itemArrow.getDamage() - ItemArrow.TIPPED_ARROW);
            if (potion != null && potion.getEffect() != null) {
                ListTag<CompoundTag> mobEffects = new ListTag<>("mobEffects");
                mobEffects.add(potion.getEffect().save());
                nbt.putList(mobEffects);
            }
        }

        double p = (double) ticksUsed / 20;

        double f = Math.min((p * p + p * 2) / 3, 1) * 2.8;
        EntityShootBowEvent entityShootBowEvent = new EntityShootBowEvent(player, this, new EntityArrow(player.chunk, nbt, player, f == 2), f);

        if (f < 0.1 || ticksUsed < 3) {
            entityShootBowEvent.setCancelled();
        }

        Server.getInstance().getPluginManager().callEvent(entityShootBowEvent);
        if (entityShootBowEvent.isCancelled()) {
            entityShootBowEvent.getProjectile().close();
            player.getInventory().sendContents(player);
            player.getOffhandInventory().sendContents(player);
        } else {
            entityShootBowEvent.getProjectile().setMotion(entityShootBowEvent.getProjectile().getMotion().multiply(entityShootBowEvent.getForce()));
            Enchantment infinityEnchant = this.getEnchantment(Enchantment.ID_BOW_INFINITY);
            boolean infinity = infinityEnchant != null && infinityEnchant.getLevel() > 0;
            EntityProjectile projectile;
            if (infinity && (projectile = entityShootBowEvent.getProjectile()) instanceof EntityArrow) {
                ((EntityArrow) projectile).setPickupMode(EntityArrow.PICKUP_CREATIVE);
            }
            if (!player.isCreative()) {
                if (!infinity || itemArrow.getDamage() != ItemArrow.NORMAL_ARROW) {
                    inventory.removeItem(itemArrow);
                }

                if (!this.isUnbreakable()) {
                    Enchantment durability = this.getEnchantment(Enchantment.ID_DURABILITY);
                    if (!(durability != null && durability.getLevel() > 0 && (100 / (durability.getLevel() + 1)) <= Utils.random.nextInt(100))) {
                        this.setDamage(this.getDamage() + 2);
                        if (this.getDamage() >= getMaxDurability()) {
                            this.count--;
                        }
                        player.getInventory().setItemInHand(this);
                    }
                }
            }

            if (entityShootBowEvent.getProjectile() != null) {
                EntityProjectile proj = entityShootBowEvent.getProjectile();
                ProjectileLaunchEvent projectev = new ProjectileLaunchEvent(proj);
                Server.getInstance().getPluginManager().callEvent(projectev);
                if (projectev.isCancelled()) {
                    proj.close();
                } else {
                    proj.spawnToAll();
                    player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_BOW);
                }
            }
        }

        return true;
    }

    protected Item getArrow(Inventory inventory) {
        for (Item item : inventory.getContents().values()) {
            //忽略物品的特殊值和nbt数据
            if (item.getId() == ItemID.ARROW && item.getCount() > 0) {
                Item clone = item.clone();
                clone.setCount(1);
                return clone;
            }
        }
        return null;
    }
}