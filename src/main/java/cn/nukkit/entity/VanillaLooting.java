package cn.nukkit.entity;

import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.StringItem;
import cn.nukkit.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Bedrock looting_enchant count bonuses on existing vanilla loot pools.
 * Add confirmed rare-chance increments without rerolling existing base drops.
 * Equipment, player inventory and custom entity drops are unchanged.
 */
final class VanillaLooting {
    private static final Map<Class<?>, Rule[]> RULES = rules();
    private static final Map<Class<?>, RareRule> RARE_RULES = Map.of(
            EntityZombie.class, new RareRule(1.0 / 6, 0.01, new Item[]{
                    Item.get(Item.IRON_INGOT), Item.get(Item.CARROT), Item.get(Item.POTATO)}),
            EntityRabbit.class, new RareRule(10.0 / 102, 0.03, new Item[]{Item.get(Item.RABBIT_FOOT)}),
            EntityWitherSkeleton.class, new RareRule(1.0 / 40, 0.02, new Item[]{Item.get(Item.SKULL, 1)}));

    private VanillaLooting() {
    }

    static Item[] apply(EntityLiving entity, Item weapon, Item[] drops) {
        return apply(entity, weapon, drops, () -> ThreadLocalRandom.current().nextFloat());
    }

    static Item[] apply(EntityLiving entity, Item weapon, Item[] drops, DoubleSupplier random) {
        int looting = weapon == null ? 0 : weapon.getEnchantmentLevel(Enchantment.ID_LOOTING);
        Rule[] rules = RULES.get(entity.getClass());
        if (looting <= 0 || looting > Short.MAX_VALUE || rules == null
                || entity instanceof BaseEntity base && base.isBaby()) {
            return drops;
        }
        List<Item> result = new ArrayList<>(Arrays.asList(drops));
        for (Rule rule : rules) {
            int index = -1;
            int count = 0;
            for (int i = 0; i < drops.length; i++) {
                if (rule.matches(drops[i])) {
                    if (index < 0) {
                        index = i;
                    }
                    count += drops[i].getCount();
                }
            }
            if (index < 0 && !rule.allowMissing) {
                continue;
            }
            int bonus = Math.round(looting * (rule.min + (rule.max - rule.min) * (float) random.getAsDouble()));
            if (rule.limit > 0) {
                bonus = Math.min(bonus, Math.max(0, rule.limit - count));
            }
            if (bonus <= 0) {
                continue;
            }
            Item prototype = index >= 0 ? result.get(index) : rule.prototype;
            int maxStack = prototype.getMaxStackSize();
            if (maxStack <= 0) {
                continue; // Invalid plugin-provided item must never create an unbounded loop.
            }
            if (index >= 0) {
                Item stack = prototype.clone();
                int added = Math.min(bonus, Math.max(0, maxStack - stack.getCount()));
                stack.setCount(stack.getCount() + added);
                result.set(index, stack);
                bonus -= added;
            }
            // Enchantment levels are persisted shorts. Even custom high levels produce
            // finitely many legal stacks; do not silently cap a server-issued enchant.
            while (bonus > 0) {
                Item stack = prototype.clone();
                int added = Math.min(bonus, maxStack);
                stack.setCount(added);
                result.add(stack);
                bonus -= added;
            }
        }
        RareRule rare = RARE_RULES.get(entity.getClass());
        if (rare != null && entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damage
                && !damage.isCancelled() && damage.getDamager() instanceof Player) {
            rare.addBonusIfMissing(looting, drops, result, random);
        }
        return result.toArray(Item.EMPTY_ARRAY);
    }

    private static Map<Class<?>, Rule[]> rules() {
        Map<Class<?>, Rule[]> rules = new HashMap<>();
        Rule leather = common(Item.LEATHER);
        Rule flesh = common(Item.ROTTEN_FLESH);
        Rule bone = common(Item.BONE);
        Rule arrow = common(Item.ARROW);
        Rule gunpowder = common(Item.GUNPOWDER);
        Rule beef = cooked(Item.RAW_BEEF, Item.STEAK);
        Rule pork = cooked(Item.RAW_PORKCHOP, Item.COOKED_PORKCHOP);
        rules.put(EntityCow.class, new Rule[]{leather, beef});
        rules.put(EntityMooshroom.class, new Rule[]{leather, beef});
        rules.put(EntityPig.class, new Rule[]{pork});
        rules.put(EntityHoglin.class, new Rule[]{leather, pork});
        rules.put(EntityChicken.class, new Rule[]{common(Item.FEATHER), cooked(Item.RAW_CHICKEN, Item.COOKED_CHICKEN)});
        rules.put(EntitySheep.class, new Rule[]{cooked(Item.RAW_MUTTON, Item.COOKED_MUTTON)});
        rules.put(EntityRabbit.class, new Rule[]{common(Item.RABBIT_HIDE), cooked(Item.RAW_RABBIT, Item.COOKED_RABBIT)});
        rules.put(EntityHorse.class, new Rule[]{leather});
        rules.put(EntityDonkey.class, new Rule[]{leather});
        rules.put(EntityMule.class, new Rule[]{leather});
        rules.put(EntityLlama.class, new Rule[]{leather});
        rules.put(EntitySkeletonHorse.class, new Rule[]{bone});
        rules.put(EntityZombieHorse.class, new Rule[]{flesh});
        rules.put(EntityZombie.class, new Rule[]{flesh});
        rules.put(EntityHusk.class, new Rule[]{flesh});
        rules.put(EntityDrowned.class, new Rule[]{flesh});
        rules.put(EntityZombiePigman.class, new Rule[]{flesh, common(Item.GOLD_NUGGET)});
        rules.put(EntityCreeper.class, new Rule[]{gunpowder});
        rules.put(EntitySkeleton.class, new Rule[]{bone, arrow});
        rules.put(EntityStray.class, new Rule[]{bone, arrow, new Rule(Item.get(Item.ARROW, 18), -1, true, 0, 1, 1)});
        rules.put(EntityBogged.class, new Rule[]{bone, arrow, new Rule(Item.get(Item.ARROW, 26), -1, true, 0, 1, 1)});
        rules.put(EntitySpider.class, new Rule[]{common(Item.STRING), common(Item.SPIDER_EYE)});
        rules.put(EntityCaveSpider.class, new Rule[]{common(Item.STRING), common(Item.SPIDER_EYE)});
        rules.put(EntityBlaze.class, new Rule[]{common(Item.BLAZE_ROD)});
        rules.put(EntityBreeze.class, new Rule[]{new Rule(Item.fromString(Item.BREEZE_ROD), -1, true, 1, 2, 0)});
        rules.put(EntityEnderman.class, new Rule[]{common(Item.ENDER_PEARL)});
        rules.put(EntityGhast.class, new Rule[]{gunpowder, common(Item.GHAST_TEAR)});
        rules.put(EntityGuardian.class, new Rule[]{common(Item.PRISMARINE_SHARD)});
        rules.put(EntityElderGuardian.class, new Rule[]{common(Item.PRISMARINE_SHARD)});
        rules.put(EntityPhantom.class, new Rule[]{common(Item.PHANTOM_MEMBRANE)});
        rules.put(EntityPillager.class, new Rule[]{arrow});
        rules.put(EntityShulker.class, new Rule[]{common(Item.SHULKER_SHELL)});
        rules.put(EntityEvoker.class, new Rule[]{common(Item.EMERALD)});
        rules.put(EntityWitherSkeleton.class, new Rule[]{bone, common(Item.COAL)});
        rules.put(EntitySlime.class, new Rule[]{present(Item.SLIMEBALL)});
        rules.put(EntityMagmaCube.class, new Rule[]{present(Item.MAGMA_CREAM)});
        rules.put(EntitySquid.class, new Rule[]{new Rule(Item.get(Item.DYE, 0), -1, true, 0, 1, 0)});
        rules.put(EntityDolphin.class, new Rule[]{common(Item.RAW_FISH)});
        rules.put(EntityPolarBear.class, new Rule[]{common(Item.RAW_FISH), common(Item.RAW_SALMON)});
        rules.put(EntityStrider.class, new Rule[]{common(Item.STRING)});
        rules.put(EntityWitch.class, new Rule[]{present(Item.STICK), present(Item.BOTTLE), present(Item.GLOWSTONE_DUST),
                present(Item.GUNPOWDER), present(Item.REDSTONE), present(Item.SPIDER_EYE), present(Item.SUGAR)});
        return Map.copyOf(rules);
    }

    private static Rule common(int id) {
        return new Rule(Item.get(id), -1, true, 0, 1, 0);
    }

    private static Rule present(int id) {
        return new Rule(Item.get(id), -1, false, 0, 1, 0);
    }

    private static Rule cooked(int raw, int cooked) {
        // The existing drop selects the cooked variant before the bonus is added.
        return new Rule(Item.get(raw), cooked, false, 0, 1, 0);
    }

    private record RareRule(double baseChance, double perLevel, Item[] items) {
        void addBonusIfMissing(int looting, Item[] baseDrops, List<Item> result, DoubleSupplier random) {
            for (Item drop : baseDrops) {
                if (drop.getCount() > 0) {
                    for (Item target : items) {
                        if (drop.equals(target, true, true)) {
                            return; // Already won the original pool: never duplicate its rare item.
                        }
                    }
                }
            }
            // Preserve MOT's established base roll. On a failed base roll, this conditional
            // probability gives P(total) = baseChance + looting * perLevel (capped at one).
            double extra = Math.min(1 - baseChance, looting * perLevel);
            if (random.getAsDouble() < extra / (1 - baseChance)) {
                int selected = items.length == 1 ? 0 : (int) (random.getAsDouble() * items.length);
                result.add(items[selected].clone());
            }
        }
    }

    private record Rule(Item prototype, int cookedId, boolean allowMissing, float min, float max, int limit) {
        boolean matches(Item item) {
            if (item == null || item.hasCompoundTag()) {
                return false;
            }
            return ((prototype instanceof StringItem && item instanceof StringItem
                    && prototype.getNamespaceId().equals(item.getNamespaceId()))
                    || (!(prototype instanceof StringItem) && (item.getId() == prototype.getId() || item.getId() == cookedId)))
                    && item.getDamage() == prototype.getDamage();
        }
    }
}
