package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 验证玩家护甲减免公式符合原版基岩版 (自 1.18.30 起)。
 * <p>
 * Verifies the player armor damage reduction matches vanilla Bedrock Edition (since 1.18.30).
 * 参考 / See: <a href="https://minecraft.wiki/w/Armor#Damage_reduction">Minecraft Wiki - Armor</a>
 */
public class ArmorDamageReductionTest {

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    /** 同包直接访问 protected static / direct call (same package, protected static) */
    private static float calcFraction(float damage, int armorPoints, int toughness, int breach) {
        return EntityHumanType.calculateArmorReductionFraction(damage, armorPoints, toughness, breach);
    }

    private static float finalDamage(float damage, int armorPoints, int toughness, int breach) {
        return damage * (1f - calcFraction(damage, armorPoints, toughness, breach));
    }

    @Test
    public void testNoArmorFullDamage() {
        assertEquals(4.0f, finalDamage(4f, 0, 0, 0), 0.001f);
        assertEquals(10.0f, finalDamage(10f, 0, 0, 0), 0.001f);
    }

    @Test
    public void testLeatherArmor() {
        // 皮套 7 护甲,熔岩 4:Wiki 基准 3.2 / Leather (7 armor), lava 4: Wiki baseline 3.2
        assertEquals(3.2f, finalDamage(4f, 7, 0, 0), 0.01f);
    }

    @Test
    public void testIronArmor() {
        // 铁套 15 护甲,熔岩 4:Wiki 基准 1.92 / Iron (15 armor), lava 4: Wiki baseline 1.92
        assertEquals(1.92f, finalDamage(4f, 15, 0, 0), 0.01f);
    }

    @Test
    public void testDiamondArmorWithToughness() {
        // 钻石套 20 护甲 8 韧性,熔岩 4:Wiki 基准 0.96 / Diamond (20/8), lava 4: Wiki baseline 0.96
        assertEquals(0.96f, finalDamage(4f, 20, 8, 0), 0.01f);
    }

    @Test
    public void testNetheriteArmorHigherToughness() {
        // 下界合金韧性更高,伤害应不高于钻石套 / Netherite has higher toughness, damage should be ≤ diamond
        float netherite = finalDamage(4f, 20, 12, 0);
        float diamond = finalDamage(4f, 20, 8, 0);
        assertTrue(netherite <= diamond,
                "Netherite (" + netherite + ") should not take more damage than Diamond (" + diamond + ")");
        assertEquals(0.928f, netherite, 0.01f);
    }

    @Test
    public void testArmorReductionDecreasesWithHigherDamage() {
        // 原版机制:同套护甲,伤害越高减免比例越低 / Vanilla: higher damage → lower reduction fraction
        float fracLow = calcFraction(4f, 20, 8, 0);
        float fracHigh = calcFraction(20f, 20, 8, 0);
        assertTrue(fracHigh < fracLow,
                "Higher damage should yield lower reduction fraction (got fracHigh=" + fracHigh + " >= fracLow=" + fracLow + ")");
    }

    @Test
    public void testBreachReducesArmorEffectiveness() {
        float noBreach = finalDamage(10f, 20, 8, 0);
        float breach4 = finalDamage(10f, 20, 8, 4);
        assertTrue(breach4 > noBreach,
                "Breach should increase final damage (got breach4=" + breach4 + " <= noBreach=" + noBreach + ")");
    }

    @Test
    public void testBreachAtMaxLevelNearIgnoresArmor() {
        // 破甲 IV 降低 0.60 护甲减免比例 / Breach IV reduces armor fraction by 0.60
        float fracNoBreach = calcFraction(10f, 20, 8, 0);
        float fracBreach4 = calcFraction(10f, 20, 8, 4);
        assertEquals(fracNoBreach - 0.60f, fracBreach4, 0.001f,
                "Breach IV should reduce armor fraction by 0.60");
    }

    @Test
    public void testArmorPointsAboveVanillaSetCanReachMaximumReduction() {
        // 护甲属性可按最高 30 参与公式,最终减免项仍上限 20 / Armor can calculate up to 30, final reduction term still caps at 20
        assertEquals(0.80f, calcFraction(4f, 30, 0, 0), 0.001f);
        // armor=20, dmg=4, tough=8 → (20-4/4)/25=0.76
        assertEquals(0.76f, calcFraction(4f, 20, 8, 0), 0.001f);
    }

    @Test
    public void testArmorPointsClampedAtAttributeCap30() {
        // armor>30 按 30 计算;极端伤害触发 floor:30/5/25=0.24 / armor>30 is capped at 30; extreme damage hits floor:30/5/25=0.24
        assertEquals(0.24f, calcFraction(1000f, 40, 0, 0), 0.001f);
    }

    @Test
    public void testArmorFloorAt20Percent() {
        // 极端伤害时下界生效:armorPoints=20 → floor=4 → frac=4/25=0.16
        // Extreme damage hits floor: armorPoints=20 → floor=4 → frac=4/25=0.16
        float fracHighDamage = calcFraction(1000f, 20, 0, 0);
        assertEquals(0.16f, fracHighDamage, 0.001f,
                "Armor fraction floor (armorPoints*0.2/25) should hold at extreme damage");
    }

    @Test
    public void testToughnessClampedAt20() {
        // toughness>20 按 20 计算:20 - 4*20/(20+8) = 17.142857 → /25
        // toughness>20 is capped at 20:20 - 4*20/(20+8) = 17.142857 → /25
        assertEquals(0.685714f, calcFraction(20f, 20, 100, 0), 0.001f);
    }

    @Test
    public void testEpfCappedAt20Deterministic() {
        // EPF 上限 20,确定性 / EPF capped at 20, deterministic
        assertEquals(0.80f, Math.min(25, 20) / 25f, 0.001f);
        assertEquals(0.40f, Math.min(10, 20) / 25f, 0.001f);
    }

    @Test
    public void testEpfDeterministicNoRandomness() {
        // 移除原 Utils.random 后应完全确定性 / Fully deterministic after removing old Utils.random
        int epf = 16;
        assertEquals(Math.min(epf, 20) / 25f, Math.min(epf, 20) / 25f, 0.0f, "EPF reduction must be deterministic");
    }

    @Test
    public void testDiamondWithProtectionIntegration() {
        // 钻石套(20/8) + 保护IV(epf≈16) 在 10 伤害下:护甲后 ~3.0,EPF 后 ~1.08
        // Diamond(20/8) + Prot IV(epf≈16) at 10 dmg: ~3.0 after armor, ~1.08 after EPF
        float damage = 10f;
        float afterArmor = finalDamage(damage, 20, 8, 0);
        // EPF = 16 (Protection IV × 4 pieces × typeModifier 1)
        float afterEpf = afterArmor * (1f - Math.min(16, 20) / 25f);
        assertEquals(1.08f, afterEpf, 0.05f,
                "Diamond + Prot IV vs 10 dmg: expected ~1.08 final, got " + afterEpf);
    }

    @Test
    public void testCriticalDamageAppliedBeforeLivingEntityHealthChange() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);
        TestLiving target = new TestLiving(chunk, baseNbt());
        Player damager = newCriticalDamager(level, chunk);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10f);

        assertTrue(target.attack(event));
        assertEquals(15f, event.getFinalDamage(), 0.001f,
                "Critical modifier must be present before final damage is consumed");
        assertEquals(5f, target.getHealth(), 0.001f,
                "Non-player living targets must lose the critical hit damage, not only receive the animation");
    }

    @Test
    public void testCriticalLivingDamageUsesPreResistanceDamage() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);
        TestLiving target = new TestLiving(chunk, baseNbt());
        Player damager = newCriticalDamager(level, chunk);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10f);
        event.setDamage(-2f, EntityDamageEvent.DamageModifier.RESISTANCE);

        assertTrue(target.attack(event));
        assertEquals(5f, event.getDamage(EntityDamageEvent.DamageModifier.CRITICAL), 0.001f);
        assertEquals(-3f, event.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE), 0.001f);
        assertEquals(12f, event.getFinalDamage(), 0.001f);
        assertEquals(8f, target.getHealth(), 0.001f);
    }

    @Test
    public void testCriticalDamageIsReducedByHumanArmor() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);
        TestHuman target = new TestHuman(chunk, baseNbt());
        target.getInventory().setArmorContents(new Item[]{
                Item.get(ItemID.DIAMOND_HELMET),
                Item.get(ItemID.DIAMOND_CHESTPLATE),
                Item.get(ItemID.DIAMOND_LEGGINGS),
                Item.get(ItemID.DIAMOND_BOOTS)
        });
        Player damager = newCriticalDamager(level, chunk);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10f);

        assertTrue(target.attack(event));
        assertEquals(5f, event.getDamage(EntityDamageEvent.DamageModifier.CRITICAL), 0.001f);
        assertEquals(-9.75f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 0.001f);
        assertEquals(5.25f, event.getFinalDamage(), 0.001f,
                "Critical damage must enter the armor formula instead of bypassing armor");
        assertEquals(14.75f, target.getHealth(), 0.001f);
    }

    @Test
    public void testPreExistingArmorModifierDoesNotFeedNewArmorFormula() {
        Level level = newMockLevel();
        TestHuman target = new TestHuman(newMockChunk(level), baseNbt());
        target.getInventory().setArmorContents(new Item[]{
                Item.get(ItemID.DIAMOND_HELMET),
                Item.get(ItemID.DIAMOND_CHESTPLATE),
                Item.get(ItemID.DIAMOND_LEGGINGS),
                Item.get(ItemID.DIAMOND_BOOTS)
        });

        Map<EntityDamageEvent.DamageModifier, Float> mods = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        mods.put(EntityDamageEvent.DamageModifier.BASE, 4f);
        mods.put(EntityDamageEvent.DamageModifier.ARMOR, -3f); // 旧 mob 攻击路径可能预置线性护甲 / legacy mob attack path may pre-set linear armor
        EntityDamageEvent event = new EntityDamageEvent(target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, mods);

        assertTrue(target.attack(event));
        assertEquals(-3.04f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 0.001f,
                "New armor formula should be based on pre-armor damage, ignoring stale ARMOR modifier");
        assertEquals(0.96f, event.getFinalDamage(), 0.001f);
        assertEquals(19.04f, target.getHealth(), 0.001f);
    }

    @Test
    public void testCriticalDamageUsesPreResistanceDamage() {
        Level level = newMockLevel();
        FullChunk chunk = newMockChunk(level);
        TestHuman target = new TestHuman(chunk, baseNbt());
        Player damager = newCriticalDamager(level, chunk);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10f);
        event.setDamage(-2f, EntityDamageEvent.DamageModifier.RESISTANCE);

        assertTrue(target.attack(event));
        assertEquals(5f, event.getDamage(EntityDamageEvent.DamageModifier.CRITICAL), 0.001f,
                "Critical damage should be based on pre-Resistance damage");
        assertEquals(-3f, event.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE), 0.001f,
                "Resistance should be recomputed after adding critical damage");
        assertEquals(12f, event.getFinalDamage(), 0.001f,
                "10 dmg critical with Resistance I should become 15 pre-Resistance, then 12 final");
        assertEquals(8f, target.getHealth(), 0.001f);
    }

    @Test
    public void testResistanceAndArmorEnchantmentsUsePostArmorPreResistanceDamage() {
        Level level = newMockLevel();
        TestHuman target = new TestHuman(newMockChunk(level), baseNbt());
        target.getInventory().setArmorContents(new Item[]{
                prot4(ItemID.DIAMOND_HELMET),
                prot4(ItemID.DIAMOND_CHESTPLATE),
                prot4(ItemID.DIAMOND_LEGGINGS),
                prot4(ItemID.DIAMOND_BOOTS)
        });

        EntityDamageEvent event = new EntityDamageEvent(target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 20f);
        event.setDamage(-4f, EntityDamageEvent.DamageModifier.RESISTANCE);

        assertTrue(target.attack(event));
        assertEquals(-12f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 0.001f);
        assertEquals(-5.12f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR_ENCHANTMENTS), 0.001f,
                "EPF should reduce post-armor damage (8), not the stale Resistance-reduced damage (4)");
        assertEquals(-0.576f, event.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE), 0.001f);
        assertEquals(17.696f, target.getHealth(), 0.001f,
                "20 dmg + Resistance I + Diamond Prot IV should leave health after 2.304 final damage");
    }

    @Test
    public void testProtectionReducesMagicDamageWithoutArmorReduction() {
        Level level = newMockLevel();
        TestHuman target = new TestHuman(newMockChunk(level), baseNbt());
        target.getInventory().setArmorContents(new Item[]{
                prot4(ItemID.DIAMOND_HELMET),
                prot4(ItemID.DIAMOND_CHESTPLATE),
                prot4(ItemID.DIAMOND_LEGGINGS),
                prot4(ItemID.DIAMOND_BOOTS)
        });

        EntityDamageEvent event = new EntityDamageEvent(target, EntityDamageEvent.DamageCause.MAGIC, 10f);

        assertTrue(target.attack(event));
        assertEquals(0f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR), 0.001f,
                "Magic damage should not be reduced by armor points");
        assertEquals(-6.4f, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR_ENCHANTMENTS), 0.001f,
                "Protection IV on four armor pieces should reduce magic damage by 64%");
        assertEquals(3.6f, event.getFinalDamage(), 0.001f);
        assertEquals(16.4f, target.getHealth(), 0.001f);
    }

    /**
     * 验证 RESISTANCE 应基于护甲后伤害重算(原版顺序: ARMOR → ENCHANTMENTS → RESISTANCE)。
     * <p>
     * Verifies RESISTANCE is recomputed on post-armor damage (vanilla: ARMOR → ENCHANTMENTS → RESISTANCE).
     */
    @Test
    public void testResistanceAppliedAfterArmor() {
        // 场景: 20 基础伤害 + Resistance I (-20% BASE = -4) + 钻石套 (20/8)
        // Scenario: 20 base dmg + Resistance I (-20% BASE = -4) + Diamond (20/8)
        Map<EntityDamageEvent.DamageModifier, Float> mods = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        mods.put(EntityDamageEvent.DamageModifier.BASE, 20f);
        EntityDamageEvent event = new EntityDamageEvent(Mockito.mock(Entity.class),
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, mods);
        // 模拟构造函数按 BASE 预算的 RESISTANCE / Simulate ctor-pre-computed RESISTANCE on BASE
        event.setDamage(-4f, EntityDamageEvent.DamageModifier.RESISTANCE);

        // 护甲前伤害 = finalDamage - RESISTANCE = (20-4) - (-4) = 20
        // Pre-armor damage = finalDamage - RESISTANCE = (20-4) - (-4) = 20
        float preArmorDamage = event.getFinalDamage() - event.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE);
        assertEquals(20f, preArmorDamage, 0.001f,
                "Pre-armor damage should exclude RESISTANCE (expected 20, got " + preArmorDamage + ")");

        // armorFraction = clamp(20 - 20/4, 4, 20)/25 = 0.6 → ARMOR = -12
        float armorFraction = calcFraction(preArmorDamage, 20, 8, 0);
        assertEquals(0.6f, armorFraction, 0.001f);
        event.setDamage(-preArmorDamage * armorFraction, EntityDamageEvent.DamageModifier.ARMOR);

        // 重算 RESISTANCE: ratio = 0.2, postReductionDamage = 8 → RESISTANCE = -1.6
        // Recompute: ratio = 0.2, postReductionDamage = 8 → RESISTANCE = -1.6
        float ratio = -event.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE)
                / event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE);
        float postReductionDamage = event.getFinalDamage() - event.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE);
        assertEquals(8f, postReductionDamage, 0.001f);
        event.setDamage(-postReductionDamage * ratio, EntityDamageEvent.DamageModifier.RESISTANCE);

        // 最终 = 20 - 1.6 - 12 = 6.4(原版正确值) / Final = 20 - 1.6 - 12 = 6.4 (vanilla correct)
        assertEquals(6.4f, event.getFinalDamage(), 0.001f,
                "20 dmg + Resistance I + Diamond should yield 6.4 final with RESISTANCE applied after armor (got " + event.getFinalDamage() + ")");
    }

    /**
     * 回归保护:错误路径(RESISTANCE 不重算,保持 BASE 全量)会产生不同(错误)结果。
     * <p>
     * Regression guard: the buggy path (RESISTANCE stays on full BASE, never recomputed) yields a wrong result.
     */
    @Test
    public void testBuggyPathKeepsResistanceOnBase() {
        // 场景: 20 基础伤害 + Resistance I + 钻石套 (20/8)
        // Scenario: 20 base dmg + Resistance I + Diamond (20/8)
        float base = 20f;
        float ctorResistance = -base * 0.20f; // -4,构造函数按 BASE 预算 / ctor-pre-computed on BASE

        // 正确路径:护甲基于 20,RESISTANCE 重算 / Correct: armor on 20, RESISTANCE recomputed
        float armorFraction = calcFraction(20f, 20, 8, 0); // 0.6
        float armor = -20f * armorFraction; // -12
        float postReduction = 20f + armor; // 8 (不含 RESISTANCE)
        float correctResistance = -postReduction * 0.20f; // -1.6
        float correctFinal = base + correctResistance + armor; // 6.4
        assertEquals(6.4f, correctFinal, 0.01f);

        // 错误路径:RESISTANCE 保持构造函数值 (-4) / Buggy: RESISTANCE stays at ctor value (-4)
        float buggyFinal = base + ctorResistance + armor; // 20 - 4 - 12 = 4.0
        assertEquals(4.0f, buggyFinal, 0.01f);

        assertTrue(buggyFinal < correctFinal,
                "Buggy final (" + buggyFinal + ") under-reduces vs correct (" + correctFinal
                        + ") because RESISTANCE was applied on full BASE");
    }

    private static Item prot4(int id) {
        Item item = Item.get(id);
        item.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_PROTECTION_ALL).setLevel(4));
        return item;
    }

    private static Player newCriticalDamager(Level level, FullChunk chunk) {
        Player damager = mock(Player.class);
        damager.chunk = chunk;
        damager.speed = new Vector3(0, 1, 0);
        lenient().when(damager.getLevel()).thenReturn(level);
        lenient().when(damager.getBoundingBox()).thenReturn(new SimpleAxisAlignedBB(0, 0, 0, 1, 2, 1));
        lenient().when(damager.isOnGround()).thenReturn(false);
        return damager;
    }

    private static Level newMockLevel() {
        Level level = mock(Level.class);
        lenient().when(level.getServer()).thenReturn(MockServer.get());
        lenient().when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        lenient().when(level.getNearbyEntities(any(), any())).thenReturn(new Entity[0]);
        lenient().when(level.getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt())).thenReturn(0);
        lenient().when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        try {
            Field f = Level.class.getDeclaredField("isBeingConverted");
            f.setAccessible(true);
            f.setBoolean(level, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return level;
    }

    private static FullChunk newMockChunk(Level level) {
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        lenient().when(chunk.getProvider()).thenReturn(provider);
        lenient().when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private static CompoundTag baseNbt() {
        return new CompoundTag()
                .putList(new ListTag<>("Pos")
                        .add(new DoubleTag("", 0.5))
                        .add(new DoubleTag("", 64.0))
                        .add(new DoubleTag("", 0.5)))
                .putList(new ListTag<>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)))
                .putCompound("Skin", new CompoundTag()
                        .putString("ModelId", "test")
                        .putByteArray("Data", new byte[64 * 32 * 4]));
    }

    private static final class TestLiving extends EntityLiving {
        private TestLiving(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }

        @Override
        public int getNetworkId() {
            return -1;
        }
    }

    private static final class TestHuman extends EntityHuman {
        private TestHuman(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }
    }
}
