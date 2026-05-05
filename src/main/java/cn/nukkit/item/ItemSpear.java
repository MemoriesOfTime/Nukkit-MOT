package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Utils;

import java.util.EnumMap;
import java.util.Map;

/**
 * Base implementation for the 1.21.130 spear family.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public abstract class ItemSpear extends StringItemToolBase {

    private static final double STAB_DISTANCE = 5.0;
    private static final double MINIMUM_TARGET_DOT = 0.82;
    private static final int MINIMUM_LUNGE_FOOD = 6;
    private static final int BASE_LUNGE_EXHAUSTION = 4;

    protected ItemSpear(String namespaceId, String name) {
        super(namespaceId, name);
    }

    @Override
    public boolean isSpear() {
        return true;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        int sound = this.getUseSound();
        if (sound > 0) {
            player.getLevel().addLevelSoundEvent(player, sound);
        }
        return true;
    }

    @Override
    public boolean canRelease() {
        return true;
    }

    @Override
    public boolean onUse(Player player, int ticksUsed) {
        return this.onRelease(player, ticksUsed);
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        if (this.getDamage() >= this.getMaxDurability()) {
            return true;
        }

        boolean hit = this.stab(player);
        this.applyLunge(player);

        int sound = hit ? this.getHitSound() : this.getMissSound();
        if (sound > 0) {
            player.getLevel().addLevelSoundEvent(player, sound);
        }

        this.damageSpear(player);
        return true;
    }

    @Override
    public boolean useOn(Entity entity) {
        if (this.isUnbreakable() || this.noDamageOnAttack() || this.isDurabilitySavedByUnbreaking()) {
            return true;
        }

        this.meta++;
        return true;
    }

    private boolean stab(Player player) {
        EntityLiving target = this.findStabTarget(player);
        if (target == null) {
            return false;
        }

        float damage = this.getJabDamage(player, target);
        Map<EntityDamageEvent.DamageModifier, Float> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, damage);

        Enchantment[] enchantments = this.getEnchantments();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers, 0.3f, enchantments);
        if (!this.prepareStabAttack(player, target, event)) {
            return false;
        }
        if (!target.attack(event)) {
            return false;
        }

        for (Enchantment enchantment : enchantments) {
            enchantment.doPostAttack(player, target);
        }
        return true;
    }

    boolean prepareStabAttack(Player player, EntityLiving target, EntityDamageByEntityEvent event) {
        if (target instanceof Player targetPlayer) {
            if ((targetPlayer.gamemode & 0x01) > 0) {
                return false;
            }
            if (!player.getServer().pvpEnabled) {
                return false;
            }
        }

        if (player.isSpectator()) {
            event.setCancelled();
        }
        if (target instanceof Player && !player.getLevel().getGameRules().getBoolean(GameRule.PVP)) {
            event.setCancelled();
        }
        return true;
    }

    private EntityLiving findStabTarget(Player player) {
        Vector3 eye = player.add(0, player.getEyeHeight(), 0);
        Vector3 direction = player.getDirectionVector().normalize();
        AxisAlignedBB searchBox = player.getBoundingBox().grow(STAB_DISTANCE, 2.0, STAB_DISTANCE);

        EntityLiving bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Entity entity : player.getLevel().getNearbyEntities(searchBox, player)) {
            if (!(entity instanceof EntityLiving living) || !living.isAlive()) {
                continue;
            }

            Vector3 targetPos = living.add(0, living.getHeight() * 0.5, 0);
            double distance = eye.distance(targetPos);
            if (distance > STAB_DISTANCE) {
                continue;
            }

            Vector3 toTarget = targetPos.subtract(eye).normalize();
            double dot = direction.dot(toTarget);
            if (dot < MINIMUM_TARGET_DOT) {
                continue;
            }
            if (!this.hasClearStabLine(player, eye, targetPos, distance)) {
                continue;
            }

            double score = dot - distance / STAB_DISTANCE * 0.1;
            if (score > bestScore) {
                bestScore = score;
                bestTarget = living;
            }
        }

        return bestTarget;
    }

    private boolean hasClearStabLine(Player player, Vector3 eye, Vector3 targetPos, double distance) {
        Vector3 toTarget = targetPos.subtract(eye);
        if (toTarget.lengthSquared() <= 0) {
            return true;
        }

        Vector3 direction = toTarget.normalize();
        for (double travelled = 0; travelled <= distance; travelled += 0.25) {
            Vector3 checkPos = eye.add(direction.multiply(travelled));
            Block block = player.getLevel().getBlock(checkPos);
            if (!block.canPassThrough()) {
                return false;
            }
        }

        return true;
    }

    private float getJabDamage(Player player, Entity target) {
        float damage = this.getAttackDamage();
        for (Enchantment enchantment : this.getEnchantments()) {
            damage += enchantment.getDamageBonus(target, player);
        }
        damage += this.getEnchantmentLevel(Enchantment.ID_LUNGE) * 1.5f;
        return damage;
    }

    private void applyLunge(Player player) {
        int lungeLevel = this.getEnchantmentLevel(Enchantment.ID_LUNGE);
        if (lungeLevel <= 0 || player.isGliding() || player.isSwimming() || player.isInsideOfWater()) {
            return;
        }
        if ((player.isSurvival() || player.isAdventure()) && player.getFoodData().getLevel() < MINIMUM_LUNGE_FOOD) {
            return;
        }

        Vector3 direction = player.getDirectionVector();
        direction.y = 0;
        if (direction.lengthSquared() <= 0) {
            return;
        }

        direction = direction.normalize().multiply(0.5 + lungeLevel * 0.4);
        player.setMotion(player.getMotion().add(direction));
        player.getLevel().addSound(player, Sound.ITEM_SPEAR_LUNGE);
        if (player.isSurvival() || player.isAdventure()) {
            player.getFoodData().updateFoodExpLevel(BASE_LUNGE_EXHAUSTION * lungeLevel);
        }
    }

    private void damageSpear(Player player) {
        if (player.isCreative()) {
            return;
        }

        this.useOn((Entity) null);
        if (this.getDamage() >= this.getMaxDurability()) {
            player.getLevel().addSoundToViewers(player, Sound.RANDOM_BREAK);
            player.getLevel().addParticle(new ItemBreakParticle(player, this));
            player.getInventory().setItemInHand(Item.get(Item.AIR));
        } else if (this.isSameHeldItem(player.getInventory().getItemInHandFast())) {
            player.getInventory().setItemInHand(this);
        }
    }

    private boolean isSameHeldItem(Item heldItem) {
        return heldItem instanceof StringItem stringItem && stringItem.getNamespaceId().equals(this.getNamespaceId());
    }

    private boolean isDurabilitySavedByUnbreaking() {
        if (!this.hasEnchantments()) {
            return false;
        }

        Enchantment durability = this.getEnchantment(Enchantment.ID_DURABILITY);
        return durability != null && durability.getLevel() > 0
                && (100 / (durability.getLevel() + 1)) <= Utils.random.nextInt(100);
    }

    private int getHitSound() {
        return this.getTier() == TIER_WOODEN
                ? LevelSoundEventPacket.SOUND_WOODEN_SPEAR_ATTACK_HIT
                : LevelSoundEventPacket.SOUND_SPEAR_ATTACK_HIT;
    }

    private int getMissSound() {
        return this.getTier() == TIER_WOODEN
                ? LevelSoundEventPacket.SOUND_WOODEN_SPEAR_ATTACK_MISS
                : LevelSoundEventPacket.SOUND_SPEAR_ATTACK_MISS;
    }

    private int getUseSound() {
        return this.getTier() == TIER_WOODEN
                ? LevelSoundEventPacket.SOUND_WOODEN_SPEAR_USE
                : LevelSoundEventPacket.SOUND_SPEAR_USE;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_130_28;
    }
}
