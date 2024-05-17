package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemShovelIron;
import cn.nukkit.item.ItemSwordIron;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EntityZombie extends EntityWalkingMob implements EntitySmite {

    public static final int NETWORK_ID = 32;

    private Item tool;

    public EntityZombie(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public float getHeight() {
        return 1.95f;
    }

    @Override
    public double getSpeed() {
        return this.isBaby() ? 1.6 : 1.1;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(20);

        super.initEntity();

        this.setDamage(new int[]{0, 2, 3, 4});

        if (this.namedTag.contains("Armor") && this.namedTag.get("Armor") instanceof ListTag) {
            ListTag<CompoundTag> listTag = this.namedTag.getList("Armor", CompoundTag.class);
            Item[] loadedArmor = new Item[4];
            int count = 0;
            for (CompoundTag item : listTag.getAll()) {
                int slot = item.getByte("Slot");
                if (slot < 0 || slot > 3) {
                    this.server.getLogger().error("Failed to load zombie armor: Invalid slot: " + slot);
                    break;
                }
                if (slot < count) {
                    this.server.getLogger().error("Failed to load zombie armor: Duplicated slot: " + slot);
                    break;
                }
                loadedArmor[slot] = NBTIO.getItemHelper(item);
                count++;
            }
            this.armor = loadedArmor;
        } else {
            this.armor = getRandomArmor();
        }

        this.addArmorExtraHealth();

        if (this.namedTag.contains("Item")) {
            this.tool = NBTIO.getItemHelper(this.namedTag.getCompound("Item"));
            if (tool instanceof ItemSwordIron) {
                this.setDamage(new int[]{0, 4, 6, 8});
            } else if (tool instanceof ItemShovelIron) {
                this.setDamage(new int[]{0, 3, 4, 5});
            }
        } else {
            this.setRandomTool();
        }
    }

    @Override
    public void attackEntity(Entity target) {
        if (this.attackDelay > 23 && target.distanceSquared(this) <= 1) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

            if (target instanceof Player) {
                float points = 0;
                for (Item i : ((Player) target).getInventory().getArmorContents()) {
                    points += this.getArmorPoints(i.getId());
                }

                damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                        (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
            }
            target.attack(new EntityDamageByEntityEvent(this, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
            this.playAttack();
        }
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate;

        if (getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        hasUpdate = super.entityBaseTick(tickDiff);

        if (!this.closed && level.shouldMobBurn(this)) {
            if (this.armor[0] == null) {
                this.setOnFire(100);
            } else if (this.armor[0].getId() == 0) {
                this.setOnFire(100);
            }
        }

        return hasUpdate;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            for (int i = 0; i < Utils.rand(0, 2); i++) {
                drops.add(Item.get(Item.ROTTEN_FLESH, 0, 1));
            }

            if (this.tool != null) {
                if (Utils.rand(1, 3) == 1) {
                    drops.add(tool);
                }
            }

            if (this.armor != null && armor.length == 4 && Utils.rand(1, 3) == 1) {
                drops.add(armor[Utils.rand(0, 3)]);
            }

            if (Utils.rand(1, 3) == 1) {
                switch (Utils.rand(1, 3)) {
                    case 1:
                        drops.add(Item.get(Item.IRON_INGOT, 0, Utils.rand(0, 1)));
                        break;
                    case 2:
                        drops.add(Item.get(Item.CARROT, 0, Utils.rand(0, 1)));
                        break;
                    case 3:
                        drops.add(Item.get(Item.POTATO, 0, Utils.rand(0, 1)));
                        break;
                }
            }
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 12 : 5;
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);

        if (this.armor[0].getId() != 0 || this.armor[1].getId() != 0 || this.armor[2].getId() != 0 || this.armor[3].getId() != 0) {
            MobArmorEquipmentPacket pk = new MobArmorEquipmentPacket();
            pk.eid = this.getId();
            pk.slots = this.armor;

            player.dataPacket(pk);
        }

        if (this.tool != null) {
            MobEquipmentPacket pk2 = new MobEquipmentPacket();
            pk2.eid = this.getId();
            pk2.hotbarSlot = 0;
            pk2.item = this.tool;
            player.dataPacket(pk2);
        }
    }

    private void setRandomTool() {
        if (Utils.rand(1, 10) == 5) {
            if (Utils.rand(1, 3) == 1) {
                this.tool = Item.get(Item.IRON_SWORD, Utils.rand(200, 246), 1);
                this.setDamage(new int[]{0, 4, 6, 8});
            } else {
                this.tool = Item.get(Item.IRON_SHOVEL, Utils.rand(200, 246), 1);
                this.setDamage(new int[]{0, 3, 4, 5});
            }
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        super.attack(ev);

        if (!ev.isCancelled() && ev.getCause() == EntityDamageEvent.DamageCause.DROWNING && !(this instanceof EntityZombieVillager)) {
            CreatureSpawnEvent cse = new CreatureSpawnEvent(EntityDrowned.NETWORK_ID, this, CreatureSpawnEvent.SpawnReason.DROWNED);
            level.getServer().getPluginManager().callEvent(cse);

            if (!cse.isCancelled()) {
                CompoundTag nbt = Entity.getDefaultNBT(this).putBoolean("HandItemSet", true);
                Entity ent = Entity.createEntity("Drowned", this, nbt);
                if (ent != null) {
                    ent.setHealth(this.getHealth());
                    this.close();
                    ent.spawnToAll();
                }
            }
        }

        return true;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.saveTool();
        this.saveArmor();
    }

    private void saveTool() {
        if (tool != null) {
            this.namedTag.put("Item", NBTIO.putItemHelper(tool));
        }
    }

    private void saveArmor() {
        if (this.armor != null && this.armor.length == 4) {
            ListTag<CompoundTag> listTag = new ListTag<>("Armor");
            for (int slot = 0; slot < 4; ++slot) {
                listTag.add(NBTIO.putItemHelper(this.armor[slot], slot));
            }
            this.namedTag.putList(listTag);
        }
    }

    /**
     * Get held tool
     *
     * @return the tool this zombie has in hand or null
     */
    public Item getTool() {
        return this.tool;
    }
}
