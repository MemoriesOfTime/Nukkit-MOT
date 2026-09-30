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
    /**
     * Vanilla {@code kinetic_weapon.damage_conditions.min_relative_speed}, the same for every
     * spear: how fast, in blocks per second, the spear has to close in on its target to hurt it.
     */
    private static final double CHARGE_MIN_RELATIVE_SPEED = 4.6;
    /** Ticks of network jitter allowed on both edges of the charge window. */
    private static final int CHARGE_JITTER_TICKS = 3;

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

        // Vanilla minecraft:kinetic_weapon: the charge strikes only after the spear's warm-up
        // (delay), until its damage window runs out, and only a target the spear closes in on
        // fast enough. A charge that hits nothing costs no durability, like a jab or a sword swing.
        boolean hit = isChargeInWindow(ticksUsed, this.getChargeDelayTicks(), this.getChargeDamageTicks())
                && this.chargeStab(player);
        this.applyLunge(player);

        int sound = hit ? this.getHitSound() : this.getMissSound();
        if (sound > 0) {
            player.getLevel().addLevelSoundEvent(player, sound);
        }

        if (hit) {
            this.damageSpear(player);
        }
        return true;
    }

    /**
     * Jab through the attack button ({@code USE_ITEM_ACTION_USE_AS_ATTACK}, 1.21.110+ clients).
     * A jab that hits wears the spear down like any melee weapon hit; a miss costs nothing.
     *
     * @return whether the jab hit a target
     */
    public boolean onJab(Player player) {
        if (this.getDamage() >= this.getMaxDurability()) {
            return false;
        }
        if (!this.stab(player)) {
            return false;
        }
        this.damageSpear(player);
        return true;
    }

    /**
     * Swing cool down of the jab in ticks, from the vanilla {@code minecraft:cooldown} of each
     * spear (0.65 s wooden … 1.15 s netherite, Mojang/bedrock-samples).
     */
    public int getJabCooldownTicks() {
        return switch (this.getTier()) {
            case TIER_WOODEN -> 13;
            case TIER_STONE -> 15;
            case TIER_COPPER -> 17;
            case TIER_DIAMOND -> 21;
            case TIER_NETHERITE -> 23;
            default -> 19;
        };
    }

    /**
     * Warm-up of the charge in ticks: vanilla {@code kinetic_weapon.delay} of each spear
     * (15 wooden … 8 netherite, Mojang/bedrock-samples).
     */
    public int getChargeDelayTicks() {
        return switch (this.getTier()) {
            case TIER_WOODEN -> 15;
            case TIER_STONE, TIER_GOLD -> 14;
            case TIER_COPPER -> 13;
            case TIER_DIAMOND -> 10;
            case TIER_NETHERITE -> 8;
            default -> 12;
        };
    }

    /**
     * How long after the warm-up the charge still hurts, in ticks: vanilla
     * {@code kinetic_weapon.damage_conditions.max_duration} (300 wooden … 175 netherite).
     */
    public int getChargeDamageTicks() {
        return switch (this.getTier()) {
            case TIER_WOODEN -> 300;
            case TIER_STONE, TIER_GOLD -> 275;
            case TIER_COPPER -> 250;
            case TIER_DIAMOND -> 200;
            case TIER_NETHERITE -> 175;
            default -> 225;
        };
    }

    static boolean isChargeInWindow(int ticksUsed, int delayTicks, int damageTicks) {
        return ticksUsed >= delayTicks - CHARGE_JITTER_TICKS
                && ticksUsed <= delayTicks + damageTicks + CHARGE_JITTER_TICKS;
    }

    /**
     * Closing speed in blocks per second along the attacker's look: the attacker's own speed
     * towards where he looks minus the target's speed in the same direction (vanilla
     * {@code min_relative_speed} compares this value).
     */
    static double relativeChargeSpeed(Vector3 attackerVelocity, Vector3 targetVelocity, Vector3 look) {
        return (attackerVelocity.dot(look) - targetVelocity.dot(look)) * 20.0;
    }

    static boolean isChargeFastEnough(double relativeSpeed) {
        return relativeSpeed >= CHARGE_MIN_RELATIVE_SPEED;
    }

    @Override
    public boolean useOn(Entity entity) {
        if (this.isUnbreakable() || this.noDamageOnAttack() || this.isDurabilitySavedByUnbreaking()) {
            return true;
        }

        this.meta++;
        return true;
    }

    boolean stab(Player player) {
        EntityLiving target = this.findStabTarget(player);
        return target != null && this.strike(player, target);
    }

    /**
     * The charge strike: the same target search and damage event as the jab, but only against a
     * target the spear closes in on at least {@value #CHARGE_MIN_RELATIVE_SPEED} blocks per second.
     */
    boolean chargeStab(Player player) {
        EntityLiving target = this.findStabTarget(player);
        if (target == null) {
            return false;
        }
        Vector3 targetVelocity = target instanceof Player targetPlayer
                ? targetPlayer.getRecentVelocity()
                : new Vector3(target.motionX, target.motionY, target.motionZ);
        double closing = relativeChargeSpeed(player.getRecentVelocity(), targetVelocity,
                player.getDirectionVector().normalize());
        return isChargeFastEnough(closing) && this.strike(player, target);
    }

    private boolean strike(Player player, EntityLiving target) {
        float damage = this.getJabDamage(player, target);
        Map<EntityDamageEvent.DamageModifier, Float> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, damage);

        Enchantment[] enchantments = this.getEnchantments();

        float knockBack = 0.3f;
        Enchantment knockBackEnchantment = this.getEnchantment(Enchantment.ID_KNOCKBACK);
        if (knockBackEnchantment != null) {
            knockBack += knockBackEnchantment.getLevel() * 0.1f;
        }

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers, knockBack, enchantments);
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

    void damageSpear(Player player) {
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
