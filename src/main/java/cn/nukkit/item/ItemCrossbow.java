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
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.potion.Potion;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

public class ItemCrossbow extends ItemBow {

    private static final float ARROW_POWER = 3.15f;
    private static final float MULTISHOT_ANGLE_DELTA = 10;

    private int loadTick = 0; //TODO Improve this

    public ItemCrossbow() {
        this(0, 1);
    }

    public ItemCrossbow(Integer meta) {
        this(meta, 1);
    }

    public ItemCrossbow(Integer meta, int count) {
        super(CROSSBOW, meta, count, "Crossbow");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_CROSSBOW;
    }

    @Override
    public boolean noDamageOnAttack() {
        return true;
    }

    @Override
    public boolean noDamageOnBreak() {
        return true;
    }

    @Override
    public boolean onUse(Player player, int ticksUsed) {
        int needTickUsed = 20;
        Enchantment enchantment = this.getEnchantment(Enchantment.ID_CROSSBOW_QUICK_CHARGE);
        if (enchantment != null) {
            needTickUsed -= enchantment.getLevel() * 5; //0.25s
        }

        if (ticksUsed < needTickUsed) {
            return true;
        }

        Item chargedItem;

        Inventory inventory = player.getOffhandInventory();

        if ((chargedItem = this.getFireworksOrArrow(inventory)) == null &&
                (chargedItem = this.getArrow(inventory = player.getInventory())) == null) {
            if (player.isCreative()) {
                chargedItem = Item.get(Item.ARROW, 0, 1);
            } else {
                player.getOffhandInventory().sendContents(player);
                inventory.sendContents(player);
                return true;
            }
        }

        if (!this.isLoaded()) {
            if (!player.isCreative()) {
                if (!this.isUnbreakable()) {
                    Enchantment durability = this.getEnchantment(Enchantment.ID_DURABILITY);
                    if (!(durability != null && durability.getLevel() > 0 && (100 / (durability.getLevel() + 1)) <= Utils.random.nextInt(100))) {
                        this.setDamage(this.getDamage() + (chargedItem.getId() == ItemID.FIREWORKS ? 3 : 1));
                        if (this.getDamage() >= DURABILITY_CROSSBOW) {
                            this.count--;
                        }
                        player.getInventory().setItemInHand(this);
                    }
                }

                inventory.removeItem(chargedItem);
            }

            boolean multishot = getEnchantmentLevel(Enchantment.ID_CROSSBOW_MULTISHOT) > 0;
            if (multishot) {
                chargedItem.setCount(3);
            }

            this.loadArrow(player, chargedItem);

            EntityEventPacket packet = new EntityEventPacket();
            packet.event = EntityEventPacket.CHARGED_CROSSBOW;
            packet.eid = player.getId();
            player.dataPacket(packet);
        }

        return true;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return !this.launchArrow(player);
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        return true;
    }

    public void loadArrow(Player player, Item arrow) {
        if (arrow == null) {
            return;
        }
        this.setChargedItem(arrow) ;
        this.loadTick = Server.getInstance().getTick();
        player.getInventory().setItemInHand(this);
    }

    public void useArrow(Player player) {
        this.setCompoundTag(this.getNamedTag().putBoolean("Charged", false).remove("chargedItem"));
        player.getInventory().setItemInHand(this);
    }

    public boolean isLoaded() {
        return !this.getChargedItem().isNull();
    }

    public boolean launchArrow(Player player) {
        Vector3 pos = player.getEyePosition();
        Item chargedItem = getChargedItem();
        if (!chargedItem.isNull() && Server.getInstance().getTick() - this.loadTick > 20) {
            int penetrationLevel = getEnchantmentLevel(Enchantment.ID_CROSSBOW_PIERCING);
            int count = Math.min(chargedItem.getCount(), 3);
            Vector3 aimDir = Vector3.directionFromRotation(player.pitch, player.yaw);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (chargedItem.getId() == ARROW) {
                CompoundTag itemTag = (CompoundTag) this.getNamedTagEntry("chargedItem");
                for (int i = 0; i < count; i++) {
                    float angleOffset = count == 1 ? 0 : i * MULTISHOT_ANGLE_DELTA - MULTISHOT_ANGLE_DELTA;
                    Vector3 dir = aimDir.yRot(angleOffset * NukkitMath.DEG_TO_RAD)
                            .add(0.0075 * random.nextGaussian(), 0.0075 * random.nextGaussian(), 0.0075 * random.nextGaussian());
                    CompoundTag nbt = Entity.getDefaultNBT(pos, dir.multiply(ARROW_POWER), (float) dir.yRotFromDirection(), (float) dir.xRotFromDirection())
                            .putByte("PierceLevel", penetrationLevel)
                            .putByte("auxValue", chargedItem.getDamage())
                            .putCompound("item", itemTag);

                    EntityArrow arrow = new EntityArrow(player.chunk, nbt, player, false);
                    arrow.piercing = penetrationLevel;
                    if (chargedItem.getDamage() != ItemArrow.NORMAL_ARROW) {
                        Potion potion = Potion.getPotion(chargedItem.getDamage() - ItemArrow.TIPPED_ARROW);
                        if (potion != null) {
                            ListTag<CompoundTag> mobEffects = new ListTag<>("mobEffects");
                            mobEffects.add(potion.getEffect().save());
                            nbt.putList(mobEffects);
                        }
                    }

                    EntityShootBowEvent entityShootBowEvent = new EntityShootBowEvent(player, this, arrow, 3.5);
                    Server.getInstance().getPluginManager().callEvent(entityShootBowEvent);
                    if (entityShootBowEvent.isCancelled()) {
                        entityShootBowEvent.getProjectile().close();
                        player.getInventory().sendContents(player);
                    } else {
                        entityShootBowEvent.getProjectile().setMotion(entityShootBowEvent.getProjectile().getMotion().multiply(entityShootBowEvent.getForce()));
                        if (entityShootBowEvent.getProjectile() != null) {
                            EntityProjectile proj = entityShootBowEvent.getProjectile();
                            ProjectileLaunchEvent projectev = new ProjectileLaunchEvent(proj);
                            Server.getInstance().getPluginManager().callEvent(projectev);
                            if (projectev.isCancelled()) {
                                proj.close();
                            } else {
                                proj.spawnToAll();
                                player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_CROSSBOW_SHOOT);
                                this.useArrow(player);
                            }
                        }
                    }
                }
            } else {
                for (int i = 0; i < count; i++) {
                    float angleOffset = count == 1 ? 0 : i * MULTISHOT_ANGLE_DELTA - MULTISHOT_ANGLE_DELTA;
                    Vector3 dir = aimDir.yRot(angleOffset * NukkitMath.DEG_TO_RAD);
                    ((ItemFirework) chargedItem).spawnFirework(player.level, pos, dir);
                }
                this.useArrow(player);
            }
            return true;
        }
        return false;
    }

    public Item getChargedItem() {
        CompoundTag nbt = getNamedTag();
        if (nbt == null) {
            return Item.AIR_ITEM;
        }

        Tag chargedItem = nbt.get("chargedItem");
        if (!(chargedItem instanceof CompoundTag)) {
            return Item.AIR_ITEM;
        }

        return NBTIO.getItemHelper((CompoundTag) chargedItem);
    }

    public void setChargedItem(Item item) {
        this.setNamedTag(this.getOrCreateNamedTag()
                .putBoolean("Charged", true)
                .putCompound("chargedItem", NBTIO.putItemHelper(item))
        );
    }

    public void clearChargedItem() {
        CompoundTag nbt = getNamedTag();
        if (nbt == null) {
            return;
        }

        if (nbt.removeAndGet("chargedItem") == null) {
            return;
        }
        nbt.putBoolean("Charged", false);

        setNamedTag(nbt);
    }

    protected Item getFireworksOrArrow(Inventory inventory) {
        Item arrow = null;
        for (Item item : inventory.getContents().values()) {
            //忽略物品的特殊值和nbt数据
            if (item.getCount() <= 0) {
                continue;
            }
            if (item.getId() == ItemID.FIREWORKS) {
                Item clone = item.clone();
                clone.setCount(1);
                return clone;
            } else if (arrow == null && item.getId() == ItemID.ARROW) {
                Item clone = item.clone();
                clone.setCount(1);
                arrow = clone;
            }
        }
        return arrow;
    }
}
