package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.GameVersion;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.Player;
import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.nukkit.event.entity.EntityDamageEvent.DamageCause.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VanillaLootingTest {
    // New API regression cases compile on the old public core; they are GREEN-only contracts.
    private static Item[] apply(EntityLiving entity, Item weapon, Item[] drops, java.util.function.DoubleSupplier random) {
        return assertDoesNotThrow(() -> {
            var type = Class.forName("cn.nukkit.entity.VanillaLooting");
            var method = type.getDeclaredMethod("apply", EntityLiving.class, Item.class, Item[].class, java.util.function.DoubleSupplier.class);
            method.setAccessible(true);
            return (Item[]) method.invoke(null, entity, weapon, drops, random);
        });
    }
    private static Item deathWeapon(EntityLiving entity) {
        return assertDoesNotThrow(() -> {
            var method = EntityLiving.class.getDeclaredMethod("getDeathLootingWeapon");
            method.setAccessible(true);
            return (Item) method.invoke(entity);
        });
    }
    private static Item[] weaponDrops(EntityLiving entity, Item weapon) {
        return assertDoesNotThrow(() -> (Item[]) EntityLiving.class.getMethod("getDrops", Item.class).invoke(entity, weapon));
    }

    private Level level;
    private FullChunk chunk;
    private Player player;
    private PlayerInventory inventory;
    private EntityCow cow;
    private boolean originalSafety;
    private final List<EntityDeathEvent> deaths = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockServer.init();
        MockServer.reset();
        originalSafety = MockServer.get().forcedSafetyEnchant;
        MockServer.get().forcedSafetyEnchant = false;
        deaths.clear();
        level = mock(Level.class);
        when(level.getServer()).thenReturn(MockServer.get());
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(0, 0)).thenReturn(Map.of());
        level.isBeingConverted = true;
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        chunk = mock(FullChunk.class);
        when(chunk.getProvider()).thenReturn(provider);
        cow = new EntityCow(chunk, nbt());
        player = mock(Player.class);
        player.level = level;
        when(player.getLevel()).thenReturn(level);
        when(player.getServer()).thenReturn(MockServer.get());
        when(player.getViewers()).thenReturn(Map.of());
        inventory = new PlayerInventory(player);
        when(player.getInventory()).thenReturn(inventory);
        cn.nukkit.plugin.PluginManager plugins = MockServer.get().getPluginManager();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityDeathEvent death) {
                deaths.add(death);
            }
            return null;
        }).when(plugins).callEvent(any());
    }

    @AfterEach
    void restoreServerEnchantPolicy() {
        MockServer.get().forcedSafetyEnchant = originalSafety;
    }

    @ParameterizedTest
    @CsvSource({"0.0,0", "0.2,1", "0.5,2", "0.9,3", "0.99999,3"})
    void cowBonusIsRoundedIndependentUniformRollAddedToBase(double sample, int bonus) {
        Item[] base = {Item.get(Item.LEATHER, 0, 2), Item.get(Item.RAW_BEEF, 0, 3)};
        AtomicInteger rolls = new AtomicInteger();
        Item[] result = apply(cow, weapon(3), base, () -> {
            rolls.incrementAndGet();
            return sample;
        });
        assertEquals(2 + bonus, count(result, Item.LEATHER));
        assertEquals(3 + bonus, count(result, Item.RAW_BEEF));
        assertEquals(2, rolls.get(), "independent leather and beef loot pools");
        assertEquals(2, base[0].getCount(), "original event drops stay immutable");
        assertEquals(3, base[1].getCount());
    }

    @Test
    void noLootingPreservesExactOriginalArrayAndDoesNotRoll() {
        Item[] base = {Item.get(Item.RAW_BEEF, 0, 2)};
        assertSame(base, apply(cow, weapon(0), base, () -> fail("no roll at level zero")));
        assertSame(base, apply(cow, null, base, () -> fail("no roll without weapon")));
    }

    @Test
    void multiEnchantWeaponOnlyUsesLootingAndLeavesEnchantsIntact() {
        Item weapon = weapon(3);
        weapon.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_DAMAGE_ALL).setLevel(5));
        Item[] result = apply(cow, weapon, new Item[]{Item.get(Item.RAW_BEEF, 0, 1)}, () -> 0.9);
        assertEquals(4, count(result, Item.RAW_BEEF));
        assertEquals(3, weapon.getEnchantmentLevel(Enchantment.ID_LOOTING));
        assertEquals(5, weapon.getEnchantmentLevel(Enchantment.ID_DAMAGE_ALL));
    }

    @Test
    void cookedMeatRetainsItsVariantAndWoolNeverMultiplies() {
        EntitySheep sheep = mock(EntitySheep.class);
        Item[] result = apply(sheep, weapon(3),
                new Item[]{Item.get(Item.WOOL, 14, 1), Item.get(Item.COOKED_MUTTON, 0, 2)}, () -> 0.9);
        assertEquals(5, count(result, Item.COOKED_MUTTON));
        assertEquals(0, count(result, Item.RAW_MUTTON));
        assertEquals(1, count(result, Item.WOOL));
        assertEquals(14, result[0].getDamage());
    }

    @Test
    void zombieGetsOneBonusEvenWhenBaseIsEmptyOrSplitIntoSeveralStacks() {
        EntityZombie zombie = mock(EntityZombie.class);
        Item sword = Item.get(Item.IRON_SWORD, 23, 1);
        Item[] result = apply(zombie, weapon(3), new Item[]{
                Item.get(Item.ROTTEN_FLESH, 0, 1), Item.get(Item.ROTTEN_FLESH, 0, 1), sword,
                Item.get(Item.IRON_INGOT, 0, 1), Item.get(Item.CARROT, 0, 1)}, () -> 0.9);
        assertEquals(5, count(result, Item.ROTTEN_FLESH));
        assertEquals(1, count(result, Item.IRON_SWORD));
        assertSame(sword, result[2], "equipment stack stays authoritative");
        assertEquals(1, count(result, Item.IRON_INGOT));
        assertEquals(1, count(result, Item.CARROT));
        assertEquals(3, count(apply(zombie, weapon(3), Item.EMPTY_ARRAY, () -> 0.9), Item.ROTTEN_FLESH));
    }

    @Test
    void commonHostileDropsGetBonusButBossesAndFixedRareItemsDoNot() {
        EntityCreeper creeper = mock(EntityCreeper.class);
        Item[] result = apply(creeper, weapon(3), new Item[]{
                Item.get(Item.GUNPOWDER, 0, 2), Item.get(Item.SKULL, 4, 1), Item.get(500, 0, 1)}, () -> 0.9);
        assertEquals(5, count(result, Item.GUNPOWDER));
        assertEquals(1, count(result, Item.SKULL));
        assertEquals(1, count(result, 500));
        Item[] star = {Item.get(Item.NETHER_STAR, 0, 1)};
        assertSame(star, apply(mock(EntityWither.class), weapon(3), star, () -> fail("boss must not roll")));
        Item[] evoker = apply(mock(EntityEvoker.class), weapon(3), new Item[]{
                Item.get(Item.EMERALD, 0, 1), Item.get(Item.TOTEM, 0, 1)}, () -> 0.9);
        assertEquals(4, count(evoker, Item.EMERALD));
        assertEquals(1, count(evoker, Item.TOTEM));
    }

    @Test
    void breezeUsesItsVanillaOneToTwoMultiplierRatherThanZeroToOne() {
        EntityBreeze breeze = mock(EntityBreeze.class);
        Item rod = Item.fromString(Item.BREEZE_ROD);
        rod.setCount(2);
        assertEquals(5, total(apply(breeze, weapon(3), new Item[]{rod}, () -> 0.0)));
        assertEquals(8, total(apply(breeze, weapon(3), new Item[]{rod}, () -> 0.99999)));
    }

    @Test
    void tippedArrowPoolIsCappedAtOneAndDoesNotInflateNormalArrows() {
        Item[] result = apply(mock(EntityStray.class), weapon(3), new Item[]{
                Item.get(Item.ARROW, 0, 2), Item.get(Item.ARROW, 18, 1)}, () -> 0.9);
        assertEquals(5, count(result, Item.ARROW, 0));
        assertEquals(1, count(result, Item.ARROW, 18));
    }

    @Test
    void witchBonusCannotInventUnselectedLootPoolsAndLargeSlimeCannotDropBalls() {
        Item[] result = apply(mock(EntityWitch.class), weapon(3),
                new Item[]{Item.get(Item.SUGAR, 0, 0)}, () -> 0.9);
        assertEquals(3, count(result, Item.SUGAR));
        assertEquals(0, count(result, Item.REDSTONE));
        assertEquals(0, total(apply(mock(EntitySlime.class), weapon(3), Item.EMPTY_ARRAY, () -> fail("size guard"))));
    }

    @Test
    void customEntitiesPlayerInventoryAndUnrelatedAnimalsUseLegacyDropsUnmodified() {
        Item[] custom = {Item.get(Item.DIAMOND, 0, 64), Item.get(Item.RAW_BEEF, 0, 1)};
        assertSame(custom, apply(mock(CustomCow.class), weapon(3), custom, () -> fail("custom drop")));
        assertSame(custom, apply(player, weapon(3), custom, () -> fail("player inventory")));
        assertSame(custom, apply(mock(EntityIronGolem.class), weapon(3), custom, () -> fail("golem drop")));
        CustomCow customCow = new CustomCow(chunk, nbt());
        assertEquals(7, count(weaponDrops(customCow, weapon(3)), Item.RAW_BEEF), "legacy getDrops override wins");
    }

    @Test
    void allBabiesPreserveLegacyDropsWithoutBonus() {
        cow.setBaby(true);
        Item[] drops = cow.getDrops();
        assertSame(drops, apply(cow, weapon(3), drops, () -> fail("baby must not roll")));
        assertEquals(0, total(weaponDrops(cow, weapon(3))));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 32767})
    void customEnchantmentLevelsAreRetainedAndSplitIntoLegalFiniteStacks(int looting) {
        Item[] result = apply(cow, weapon(looting), new Item[]{Item.get(Item.RAW_BEEF, 0, 3)}, () -> 0.999999);
        assertEquals(3 + looting, count(result, Item.RAW_BEEF));
        for (Item item : result) {
            assertTrue(item.getCount() > 0 && item.getCount() <= item.getMaxStackSize());
        }
        assertTrue(result.length <= 2 + (2 * looting / 64), "bounded serialized short level");
    }

    @Test
    void impossibleNonSerializedLevelsAndInvalidStackLimitsCannotCauseUnboundedWork() {
        Item invalidWeapon = mock(Item.class);
        when(invalidWeapon.getEnchantmentLevel(Enchantment.ID_LOOTING)).thenReturn(Integer.MAX_VALUE);
        Item[] base = {Item.get(Item.RAW_BEEF, 0, 1)};
        assertSame(base, apply(cow, invalidWeapon, base, () -> fail("invalid NBT level")));
        Item invalidDrop = mock(Item.class);
        when(invalidDrop.getId()).thenReturn(Item.RAW_BEEF);
        when(invalidDrop.getCount()).thenReturn(1);
        when(invalidDrop.getMaxStackSize()).thenReturn(0);
        Item[] result = apply(cow, weapon(3), new Item[]{invalidDrop}, () -> 0.9);
        assertSame(invalidDrop, result[0]);
        assertEquals(1, count(result, Item.RAW_BEEF));
    }

    @Test
    void deathUsesMeleeHitEnchantSnapshotEvenIfListenerChangedHeldWeapon() {
        inventory.setItemInHand(weapon(0));
        Item used = weapon(3);
        cow.setLastDamageCause(melee(used.getEnchantments()));
        assertEquals(3, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
        assertEquals(0, inventory.getItemInHand().getEnchantmentLevel(Enchantment.ID_LOOTING));
        cow.setLastDamageCause(melee(Enchantment.EMPTY_ARRAY));
        inventory.setItemInHand(weapon(3));
        assertEquals(0, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
        assertEquals(3, inventory.getItemInHand().getEnchantmentLevel(Enchantment.ID_LOOTING));
    }

    @Test
    void projectileUsesPlayerCurrentHandRatherThanProjectileEnchantments() {
        inventory.setItemInHand(weapon(3));
        cow.setLastDamageCause(new EntityDamageByChildEntityEvent(player, mock(EntityArrow.class), cow, PROJECTILE, 20));
        assertEquals(3, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
        inventory.setItemInHand(weapon(0));
        assertEquals(0, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
    }

    @Test
    void mobNaturalCancelledAndNonCombatDeathHaveNoLootingWeapon() {
        inventory.setItemInHand(weapon(3));
        cow.setLastDamageCause(new EntityDamageByEntityEvent(mock(EntityZombie.class), cow, ENTITY_ATTACK, 20));
        assertEquals(0, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
        cow.setLastDamageCause(new EntityDamageEvent(cow, FALL, 20));
        assertEquals(0, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
        EntityDamageByEntityEvent cancelled = melee(weapon(3).getEnchantments());
        cancelled.setCancelled();
        cow.setLastDamageCause(cancelled);
        assertEquals(0, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
        cow.setLastDamageCause(new EntityDamageByEntityEvent(player, cow, MAGIC, 20));
        assertEquals(0, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
        cow.setLastDamageCause(null);
        assertEquals(0, deathWeapon(cow).getEnchantmentLevel(Enchantment.ID_LOOTING));
    }

    @Test
    void realKillCreatesOneWeaponAwareEventAndPluginReplacementWinsBeforeWorldDrops() {
        cn.nukkit.plugin.PluginManager plugins = MockServer.get().getPluginManager();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof EntityDeathEvent death) {
                deaths.add(death);
                assertTrue(count(death.getDrops(), Item.RAW_BEEF) >= 1);
                death.setDrops(new Item[]{Item.get(Item.DIAMOND, 0, 7)});
            }
            return null;
        }).when(plugins).callEvent(any());
        cow.setLastDamageCause(melee(weapon(3).getEnchantments()));
        cow.kill();
        assertEquals(1, deaths.size());
        assertEquals(7, count(deaths.get(0).getDrops(), Item.DIAMOND));
        verify(level).dropItem(eq(cow), argThat(item -> item.getId() == Item.DIAMOND && item.getCount() == 7));
        verify(level, never()).dropItem(eq(cow), argThat(item -> item.getId() != Item.DIAMOND));
        cow.kill();
        assertEquals(1, deaths.size(), "second kill cannot apply bonus again");
    }

    @ParameterizedTest
    @MethodSource("commonTypes")
    void eligibleBuiltInClassUsesOnlyItsConfirmedCommonPool(Class<? extends EntityLiving> type, int itemId) {
        EntityLiving entity = mock(type);
        Item[] drops = apply(entity, weapon(3), new Item[]{Item.get(itemId, 0, 1)}, () -> 0.9);
        assertEquals(4, count(drops, itemId), type.getSimpleName());
    }

    static Stream<Arguments> commonTypes() {
        return Stream.of(
                Arguments.of(EntityCow.class, Item.LEATHER),
                Arguments.of(EntityMooshroom.class, Item.LEATHER),
                Arguments.of(EntityPig.class, Item.RAW_PORKCHOP),
                Arguments.of(EntityHoglin.class, Item.LEATHER),
                Arguments.of(EntityChicken.class, Item.FEATHER),
                Arguments.of(EntitySheep.class, Item.RAW_MUTTON),
                Arguments.of(EntityRabbit.class, Item.RABBIT_HIDE),
                Arguments.of(EntityHorse.class, Item.LEATHER),
                Arguments.of(EntityDonkey.class, Item.LEATHER),
                Arguments.of(EntityMule.class, Item.LEATHER),
                Arguments.of(EntityLlama.class, Item.LEATHER),
                Arguments.of(EntitySkeletonHorse.class, Item.BONE),
                Arguments.of(EntityZombieHorse.class, Item.ROTTEN_FLESH),
                Arguments.of(EntityZombie.class, Item.ROTTEN_FLESH),
                Arguments.of(EntityHusk.class, Item.ROTTEN_FLESH),
                Arguments.of(EntityDrowned.class, Item.ROTTEN_FLESH),
                Arguments.of(EntityZombiePigman.class, Item.GOLD_NUGGET),
                Arguments.of(EntityCreeper.class, Item.GUNPOWDER),
                Arguments.of(EntitySkeleton.class, Item.BONE),
                Arguments.of(EntityStray.class, Item.BONE),
                Arguments.of(EntityBogged.class, Item.BONE),
                Arguments.of(EntitySpider.class, Item.STRING),
                Arguments.of(EntityCaveSpider.class, Item.SPIDER_EYE),
                Arguments.of(EntityBlaze.class, Item.BLAZE_ROD),
                Arguments.of(EntityEnderman.class, Item.ENDER_PEARL),
                Arguments.of(EntityGhast.class, Item.GHAST_TEAR),
                Arguments.of(EntityGuardian.class, Item.PRISMARINE_SHARD),
                Arguments.of(EntityElderGuardian.class, Item.PRISMARINE_SHARD),
                Arguments.of(EntityPhantom.class, Item.PHANTOM_MEMBRANE),
                Arguments.of(EntityPillager.class, Item.ARROW),
                Arguments.of(EntityShulker.class, Item.SHULKER_SHELL),
                Arguments.of(EntityEvoker.class, Item.EMERALD),
                Arguments.of(EntityWitherSkeleton.class, Item.COAL),
                Arguments.of(EntitySlime.class, Item.SLIMEBALL),
                Arguments.of(EntityMagmaCube.class, Item.MAGMA_CREAM),
                Arguments.of(EntitySquid.class, Item.DYE),
                Arguments.of(EntityDolphin.class, Item.RAW_FISH),
                Arguments.of(EntityPolarBear.class, Item.RAW_SALMON),
                Arguments.of(EntityStrider.class, Item.STRING),
                Arguments.of(EntityWitch.class, Item.SUGAR));
    }

    @ParameterizedTest
    @ValueSource(ints = {589, 766, 786, 898, 1001, 2193})
    void ordinaryLootStacksRoundTripAcrossSupportedClientWindow(int protocol) {
        Item[] drops = apply(cow, weapon(100), new Item[]{Item.get(Item.RAW_BEEF, 0, 3)}, () -> 0.9);
        GameVersion version = GameVersion.byProtocol(protocol, false);
        for (Item item : drops) {
            BinaryStream encoded = new BinaryStream();
            encoded.putSlot(version, item);
            Item decoded = new BinaryStream(encoded.getBuffer()).getSlot(version);
            assertEquals(item.getId(), decoded.getId());
            assertEquals(item.getDamage(), decoded.getDamage());
            assertEquals(item.getCount(), decoded.getCount());
        }
    }

    @Test
    void realCowZeroAndThreeDropsStayInsideIndependentBasePlusBonusBounds() {
        for (int i = 0; i < 100; i++) {
            Item[] zero = weaponDrops(cow, weapon(0));
            assertTrue(count(zero, Item.LEATHER) >= 0 && count(zero, Item.LEATHER) <= 2);
            assertTrue(count(zero, Item.RAW_BEEF) >= 1 && count(zero, Item.RAW_BEEF) <= 3);
            Item[] three = weaponDrops(cow, weapon(3));
            assertTrue(count(three, Item.LEATHER) >= 0 && count(three, Item.LEATHER) <= 5);
            assertTrue(count(three, Item.RAW_BEEF) >= 1 && count(three, Item.RAW_BEEF) <= 6);
        }
    }

    @ParameterizedTest
    @CsvSource({"0.0359,true", "0.036,false"})
    void zombieRareChanceAddsExactlyOnePercentPerLevelWithoutRepeatingBaseRoll(double sample, boolean wins) {
        EntityZombie zombie = mock(EntityZombie.class);
        doReturn(new EntityDamageByEntityEvent(player, zombie, ENTITY_ATTACK, 20)).when(zombie).getLastDamageCause();
        AtomicInteger roll = new AtomicInteger();
        Item[] result = apply(zombie, weapon(3), Item.EMPTY_ARRAY,
                () -> roll.getAndIncrement() == 1 ? sample : 0.0);
        assertEquals(wins ? 1 : 0, count(result, Item.IRON_INGOT));
        assertEquals(0, count(result, Item.CARROT));
        assertEquals(0, count(result, Item.POTATO));
        assertEquals(wins ? 3 : 2, roll.get(), "one common roll, one conditional rare roll, optional selection");
    }

    @Test
    void successfulBaseRarePoolCannotRollAgainOrMultiplyRareCount() {
        EntityWitherSkeleton skeleton = mock(EntityWitherSkeleton.class);
        doReturn(new EntityDamageByEntityEvent(player, skeleton, ENTITY_ATTACK, 20)).when(skeleton).getLastDamageCause();
        AtomicInteger rolls = new AtomicInteger();
        Item[] result = apply(skeleton, weapon(3), new Item[]{Item.get(Item.SKULL, 1, 1)},
                () -> { rolls.incrementAndGet(); return 0.9; });
        assertEquals(1, count(result, Item.SKULL, 1));
        assertEquals(2, rolls.get(), "only bone and coal count pools roll");
    }

    @ParameterizedTest
    @CsvSource({"0.061,true", "0.062,false"})
    void witherSkullGetsTwoPercentPerLevelAsConditionalChance(double sample, boolean wins) {
        EntityWitherSkeleton skeleton = mock(EntityWitherSkeleton.class);
        doReturn(new EntityDamageByEntityEvent(player, skeleton, ENTITY_ATTACK, 20)).when(skeleton).getLastDamageCause();
        AtomicInteger roll = new AtomicInteger();
        Item[] result = apply(skeleton, weapon(3), Item.EMPTY_ARRAY,
                () -> roll.getAndIncrement() == 2 ? sample : 0.0);
        assertEquals(wins ? 1 : 0, count(result, Item.SKULL, 1));
    }

    @ParameterizedTest
    @CsvSource({"0.0997,true", "0.0998,false"})
    void rabbitFootGetsThreePercentPerLevelAsConditionalChance(double sample, boolean wins) {
        EntityRabbit rabbit = mock(EntityRabbit.class);
        doReturn(new EntityDamageByEntityEvent(player, rabbit, ENTITY_ATTACK, 20)).when(rabbit).getLastDamageCause();
        AtomicInteger roll = new AtomicInteger();
        Item[] result = apply(rabbit, weapon(3), new Item[]{Item.get(Item.RAW_RABBIT, 0, 0)},
                () -> roll.getAndIncrement() == 2 ? sample : 0.0);
        assertEquals(wins ? 1 : 0, count(result, Item.RABBIT_FOOT));
    }

    @Test
    void rareChanceRequiresActualPlayerKillAndNeverTouchesEquipment() {
        EntityZombie zombie = mock(EntityZombie.class);
        doReturn(new EntityDamageByEntityEvent(mock(EntitySkeleton.class), zombie, ENTITY_ATTACK, 20)).when(zombie).getLastDamageCause();
        AtomicInteger rolls = new AtomicInteger();
        Item gear = Item.get(Item.IRON_SWORD, 21, 1);
        Item[] result = apply(zombie, weapon(3), new Item[]{gear},
                () -> { rolls.incrementAndGet(); return 0.0; });
        assertSame(gear, result[0]);
        assertEquals(0, count(result, Item.IRON_INGOT));
        assertEquals(1, rolls.get(), "common count only; no rare chance for mob killer");
    }

    private void nativeKillCount(int acceptedLooting, int heldLooting, int expectedBeef) {
        inventory.setItemInHand(weapon(heldLooting));
        cow.setLastDamageCause(melee(weapon(acceptedLooting).getEnchantments()));
        java.util.concurrent.ThreadLocalRandom random = mock(java.util.concurrent.ThreadLocalRandom.class);
        when(random.nextFloat()).thenReturn(0.9f);
        // Native base loot uses nextInt(bound)+minimum; the default0 selects the lawful minimum.
        try (var rng = mockStatic(java.util.concurrent.ThreadLocalRandom.class)) {
            rng.when(java.util.concurrent.ThreadLocalRandom::current).thenReturn(random);
            cow.kill();
            assertEquals(1, deaths.size());
            assertEquals(expectedBeef, count(deaths.get(0).getDrops(), Item.RAW_BEEF),
                    "Actual native kill must apply the accepted weapon bonus before the death event");
            cow.kill();
            assertEquals(1, deaths.size(), "Repeated native kill cannot repeat the death drops");
        }
    }

    @Test void nativeKillLootingUsesAcceptedWeaponSnapshot() { nativeKillCount(3, 0, 4); }
    @Test void nativeKillWithoutLootingRetainsBaseCount() { nativeKillCount(0, 3, 1); }

    private EntityDamageByEntityEvent melee(Enchantment[] enchantments) {
        Map<EntityDamageEvent.DamageModifier, Float> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, 20f);
        return new EntityDamageByEntityEvent(player, cow, ENTITY_ATTACK, modifiers, 0f, enchantments);
    }

    private static Item weapon(int looting) {
        Item sword = Item.get(Item.DIAMOND_SWORD);
        if (looting > 0) {
            sword.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_LOOTING).setLevel(looting, false));
        }
        return sword;
    }

    private static int count(Item[] drops, int id) {
        int count = 0;
        for (Item item : drops) {
            if (item.getId() == id) {
                count += item.getCount();
            }
        }
        return count;
    }

    private static int count(Item[] drops, int id, int damage) {
        int count = 0;
        for (Item item : drops) {
            if (item.getId() == id && item.getDamage() == damage) {
                count += item.getCount();
            }
        }
        return count;
    }

    private static int total(Item[] drops) {
        int count = 0;
        for (Item item : drops) {
            count += item.getCount();
        }
        return count;
    }

    private static CompoundTag nbt() {
        return Entity.getDefaultNBT(new cn.nukkit.math.Vector3(0, 64, 0));
    }

    private static class CustomCow extends EntityCow {
        CustomCow(FullChunk chunk, CompoundTag tag) {
            super(chunk, tag);
        }

        @Override
        public Item[] getDrops() {
            return new Item[]{Item.get(Item.RAW_BEEF, 0, 7)};
        }
    }
}
