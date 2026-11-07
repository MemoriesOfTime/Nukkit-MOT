package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EntityZombie#attackEntity} 的伤害行为：冷却与距离门槛、护甲减伤、事件字段、事件隔离。
 * <p>
 * 只断言玩家/实体可观察到的结果。不断言内部辅助方法的调用次数——把两次护甲点查询合并、或缓存
 * 护甲点数都不应让这些用例变红；反过来，护甲算错或冷却没重置必须变红。
 * <p>
 * Behavioural contract of a zombie melee attack. No assertion counts internal helper invocations;
 * allocation budgets live in {@code EntityZombieAttackBenchmark}, which is not part of the test suite.
 */
class EntityZombieAttackBehaviorTest {

    private static final Item[] IRON_ARMOR = {
            new Item(Item.IRON_HELMET),
            new Item(Item.IRON_CHESTPLATE),
            new Item(Item.IRON_LEGGINGS),
            new Item(Item.IRON_BOOTS)};

    private static final Item[] NO_ARMOR = {
            new Item(Item.AIR), new Item(Item.AIR), new Item(Item.AIR), new Item(Item.AIR)};

    // ---------------------------------------------------------------- gate

    @Test
    void attacksOnlyWhenCooldownElapsedAndTargetInReach() {
        Attacker zombie = attacker();
        NonPlayerTarget inReach = target(0.5);                 // distanceSquared = 0.25

        zombie.attackEntity(inReach);
        assertEquals(1, inReach.attacks, "冷却结束且目标在 1 格内必须命中");

        zombie.cooldown(23);                                   // 冷却未结束
        zombie.attackEntity(inReach);
        assertEquals(1, inReach.attacks, "attackDelay 未超过 23 时不得攻击");

        zombie.cooldown(24);
        NonPlayerTarget outOfReach = target(2);                // distanceSquared = 4
        zombie.attackEntity(outOfReach);
        assertEquals(0, outOfReach.attacks, "目标超出 1 格时不得攻击");
        assertEquals(24, zombie.cooldown(), "未命中的攻击不得消耗冷却");
    }

    @Test
    void cooldownResetsAfterASuccessfulAttack() {
        Attacker zombie = attacker();
        NonPlayerTarget target = target(0.5);

        zombie.attackEntity(target);

        assertEquals(0, zombie.cooldown(), "命中后冷却必须归零");
    }

    // ---------------------------------------------------------------- damage

    @Test
    void nonPlayerTargetTakesBaseDamageWithoutArmorModifier() {
        Attacker zombie = attacker();
        NonPlayerTarget target = target(0.5);

        zombie.attackEntity(target);
        EntityDamageByEntityEvent event = target.captured;

        assertSame(zombie, event.getDamager());
        assertSame(target, event.getEntity());
        assertEquals(EntityDamageEvent.DamageCause.ENTITY_ATTACK, event.getCause());
        assertEquals(3f, event.getOriginalDamage(), 1e-6f, "基础伤害来自 getDamage()");
        assertEquals(3f, event.getFinalDamage(), 1e-6f);
        assertFalse(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR), "非玩家目标没有护甲修正");
        assertEquals(0.3f, event.getKnockBack(), 1e-6f);
        assertEquals(0, event.getWeaponEnchantments().length, "构造器未携带武器附魔");
    }

    @Test
    void ironArmorReducesZombieDamageByOnePoint() {
        Attacker zombie = attacker();
        ArmoredPlayer target = armoredPlayer(IRON_ARMOR);      // 2 + 6 + 5 + 2 = 15 点

        zombie.attackEntity(target);
        EntityDamageByEntityEvent event = target.captured;

        assertTrue(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR), "玩家目标必须带护甲修正");
        assertEquals(-1f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 1e-6f,
                "15 点护甲下 -floor(3 * 15 * 0.04) = -1");
        assertEquals(-1f, event.getOriginalDamage(EntityDamageEvent.DamageModifier.ARMOR), 1e-6f,
                "原始护甲修正不得被后续修改覆盖");
        assertEquals(2f, event.getFinalDamage(), 1e-6f);
        assertEquals(3f, event.getOriginalDamage(), 1e-6f, "原始伤害不受护甲影响");
    }

    @Test
    void playerWithoutArmorTakesFullDamage() {
        Attacker zombie = attacker();
        ArmoredPlayer target = armoredPlayer(NO_ARMOR);

        zombie.attackEntity(target);
        EntityDamageByEntityEvent event = target.captured;

        assertTrue(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR));
        assertEquals(0f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 1e-6f);
        assertEquals(3f, event.getFinalDamage(), 1e-6f);
    }

    @Test
    void effectFreeFixtureDoesNotAddStrengthWeaknessOrResistance() {
        Attacker zombie = attacker();
        ArmoredPlayer target = armoredPlayer(IRON_ARMOR);

        zombie.attackEntity(target);
        EntityDamageByEntityEvent event = target.captured;

        assertFalse(event.isApplicable(EntityDamageEvent.DamageModifier.STRENGTH));
        assertFalse(event.isApplicable(EntityDamageEvent.DamageModifier.WEAKNESS));
        assertFalse(event.isApplicable(EntityDamageEvent.DamageModifier.RESISTANCE));
    }

    // ---------------------------------------------------------------- event isolation

    @Test
    void eachAttackProducesAnIndependentEvent() {
        Attacker zombie = attacker();
        NonPlayerTarget target = target(0.5);

        zombie.attackEntity(target);
        EntityDamageByEntityEvent retained = target.captured;
        retained.setDamage(17f);

        zombie.cooldown(24);
        zombie.attackEntity(target);
        EntityDamageByEntityEvent next = target.captured;

        assertNotSame(retained, next, "每次攻击都必须构造新事件");
        assertEquals(17f, retained.getDamage(), 1e-6f, "插件对旧事件的修改不得被回写");
        assertEquals(3f, retained.getOriginalDamage(), 1e-6f);
        assertEquals(3f, next.getDamage(), 1e-6f);
        assertEquals(3f, next.getOriginalDamage(), 1e-6f);
    }

    // ---------------------------------------------------------------- fixture

    private static <T> T allocate(Class<T> type) {
        return new ObjenesisStd().newInstance(type);
    }

    static Attacker attacker() {
        Attacker zombie = allocate(Attacker.class);
        zombie.x = 0;
        zombie.y = 64;
        zombie.z = 0;
        zombie.cooldown(24);
        return zombie;
    }

    static NonPlayerTarget target(double x) {
        NonPlayerTarget target = allocate(NonPlayerTarget.class);
        target.x = x;
        target.y = 64;
        target.z = 0;
        return target;
    }

    static ArmoredPlayer armoredPlayer(Item[] armor) {
        ArmoredPlayer player = allocate(ArmoredPlayer.class);
        FixedInventory inventory = allocate(FixedInventory.class);
        inventory.armor = armor;
        player.fixedInventory = inventory;
        player.x = 0.5;
        player.y = 64;
        player.z = 0;
        return player;
    }

    /** 真实的 {@code EntityZombie.attackEntity}；只固定伤害值与动画以消除随机性。 */
    static final class Attacker extends EntityZombie {
        Attacker() {
            super(null, null);
        }

        void cooldown(int value) {
            attackDelay = value;
        }

        int cooldown() {
            return attackDelay;
        }

        @Override
        public boolean hasEffect(int effectId) {
            return false;                       // 攻击者无力量/虚弱效果
        }

        @Override
        public int getDamage() {
            return 3;
        }

        @Override
        protected void playAttack() {
            // 动画走网络层，行为测试不需要
        }
    }

    /** 捕获攻击事件，替代真实伤害结算。 */
    static final class NonPlayerTarget extends EntityZombie {
        int attacks;
        EntityDamageByEntityEvent captured;

        NonPlayerTarget() {
            super(null, null);
        }

        @Override
        public boolean hasEffect(int effectId) {
            return false;                       // fixture 不带任何药水效果
        }

        @Override
        public boolean attack(EntityDamageEvent event) {
            attacks++;
            captured = (EntityDamageByEntityEvent) event;
            return true;
        }
    }

    static final class ArmoredPlayer extends Player {
        FixedInventory fixedInventory;
        int attacks;
        EntityDamageByEntityEvent captured;

        ArmoredPlayer() {
            super(null, 0L, null);
        }

        @Override
        public PlayerInventory getInventory() {
            return fixedInventory;
        }

        @Override
        public boolean hasEffect(int effectId) {
            return false;                       // fixture 不带任何药水效果
        }

        @Override
        public boolean attack(EntityDamageEvent event) {
            attacks++;
            captured = (EntityDamageByEntityEvent) event;
            return true;
        }
    }

    static final class FixedInventory extends PlayerInventory {
        Item[] armor;

        FixedInventory() {
            super(null);
        }

        @Override
        public Item[] getArmorContents() {
            return armor;
        }
    }
}
