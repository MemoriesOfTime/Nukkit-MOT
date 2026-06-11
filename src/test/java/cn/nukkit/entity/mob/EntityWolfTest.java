package cn.nukkit.entity.mob;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityWolfTest {

    @Test
    public void testWolfArmorAbsorbsPositiveDamageModifiers() {
        Map<EntityDamageEvent.DamageModifier, Float> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, 6f);
        modifiers.put(EntityDamageEvent.DamageModifier.STRENGTH, 3f);

        EntityDamageEvent event = new EntityDamageEvent(Mockito.mock(Entity.class),
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers);

        int armorDamage = EntityWolf.applyWolfArmorAbsorption(event);

        assertEquals(9f, -event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 0.0001f);
        assertEquals(0f, event.getFinalDamage(), 0.0001f);
        assertEquals(9, armorDamage);
    }

    @Test
    public void testWolfArmorAbsorptionKeepsExistingArmorModifierBalanced() {
        Map<EntityDamageEvent.DamageModifier, Float> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, 8f);
        modifiers.put(EntityDamageEvent.DamageModifier.STRENGTH, 2f);
        modifiers.put(EntityDamageEvent.DamageModifier.ARMOR, -3f);

        EntityDamageEvent event = new EntityDamageEvent(Mockito.mock(Entity.class),
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers);

        int armorDamage = EntityWolf.applyWolfArmorAbsorption(event);

        assertEquals(-10f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 0.0001f);
        assertEquals(0f, event.getFinalDamage(), 0.0001f);
        assertEquals(7, armorDamage);
    }
}
