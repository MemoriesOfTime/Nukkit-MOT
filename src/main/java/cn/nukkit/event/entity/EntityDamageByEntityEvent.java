package cn.nukkit.event.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.potion.Effect;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EntityDamageByEntityEvent extends EntityDamageEvent {
    @Getter
    private final Entity damager;

    @Setter
    @Getter
    private float knockBack;

    @Setter
    @Getter
    private float knockBackModifier;

    private Enchantment[] enchantments;

    public EntityDamageByEntityEvent(Entity damager, Entity entity, DamageCause cause, float damage) {
        this(damager, entity, cause, damage, 0.3f);
    }

    public EntityDamageByEntityEvent(Entity damager, Entity entity, DamageCause cause, Map<DamageModifier, Float> modifiers) {
        this(damager, entity, cause, modifiers, 0.3f);
    }

    public EntityDamageByEntityEvent(Entity damager, Entity entity, DamageCause cause, float damage, float knockBack) {
        this(damager, entity, cause, damage, knockBack, 0f);
    }

    public EntityDamageByEntityEvent(Entity damager, Entity entity, DamageCause cause, float damage, float knockBack, float knockBackModifier) {
        super(entity, cause, damage);
        this.damager = damager;
        this.knockBack = knockBack;
        this.knockBackModifier = knockBackModifier;
        this.addAttackerModifiers(damager);
    }

    public EntityDamageByEntityEvent(Entity damager, Entity entity, DamageCause cause, Map<DamageModifier, Float> modifiers, float knockBack) {
        this(damager, entity, cause, modifiers, knockBack, Enchantment.EMPTY_ARRAY);
    }

    public EntityDamageByEntityEvent(Entity damager, Entity entity, DamageCause cause, Map<DamageModifier, Float> modifiers, float knockBack, Enchantment[] enchantments) {
        this(entity, damager, cause, modifiers, knockBack, enchantments, 0);
    }

    public EntityDamageByEntityEvent(Entity damager, Entity entity, DamageCause cause, Map<DamageModifier, Float> modifiers, float knockBack, Enchantment[] enchantments, float knockBackModifier) {
        super(entity, cause, modifiers);
        this.damager = damager;
        this.knockBack = knockBack;
        this.enchantments = enchantments;
        this.knockBackModifier = knockBackModifier;
        this.addAttackerModifiers(damager);
    }

    protected void addAttackerModifiers(Entity damager) {
        if (damager.hasEffect(Effect.STRENGTH)) {
            this.setDamage((float) (this.getDamage(DamageModifier.BASE) * 0.3 * (damager.getEffect(Effect.STRENGTH).getAmplifier() + 1)), DamageModifier.STRENGTH);
        }

        if (damager.hasEffect(Effect.WEAKNESS)) {
            this.setDamage(-(float) (this.getDamage(DamageModifier.BASE) * 0.2 * (damager.getEffect(Effect.WEAKNESS).getAmplifier() + 1)), DamageModifier.WEAKNESS);
        }
    }

    public Enchantment[] getWeaponEnchantments() {
        return enchantments;
    }
}
