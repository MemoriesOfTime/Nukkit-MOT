package cn.nukkit.item.enchantment;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.customitem.CustomItem;
import cn.nukkit.item.enchantment.bow.EnchantmentBowFlame;
import cn.nukkit.item.enchantment.bow.EnchantmentBowInfinity;
import cn.nukkit.item.enchantment.bow.EnchantmentBowKnockback;
import cn.nukkit.item.enchantment.bow.EnchantmentBowPower;
import cn.nukkit.item.enchantment.crossbow.EnchantmentCrossbowMultishot;
import cn.nukkit.item.enchantment.crossbow.EnchantmentCrossbowPiercing;
import cn.nukkit.item.enchantment.crossbow.EnchantmentCrossbowQuickCharge;
import cn.nukkit.item.enchantment.damage.EnchantmentDamageAll;
import cn.nukkit.item.enchantment.damage.EnchantmentDamageArthropods;
import cn.nukkit.item.enchantment.damage.EnchantmentDamageSmite;
import cn.nukkit.item.enchantment.loot.EnchantmentLootDigging;
import cn.nukkit.item.enchantment.loot.EnchantmentLootFishing;
import cn.nukkit.item.enchantment.loot.EnchantmentLootWeapon;
import cn.nukkit.item.enchantment.protection.*;
import cn.nukkit.item.enchantment.trident.EnchantmentTridentChanneling;
import cn.nukkit.item.enchantment.trident.EnchantmentTridentImpaling;
import cn.nukkit.item.enchantment.trident.EnchantmentTridentLoyalty;
import cn.nukkit.item.enchantment.trident.EnchantmentTridentRiptide;
import cn.nukkit.utils.Identifier;
import cn.nukkit.utils.OK;
import cn.nukkit.utils.Utils;
import com.nimbusds.jose.shaded.ow2asm.ClassWriter;
import com.nimbusds.jose.shaded.ow2asm.Label;
import com.nimbusds.jose.shaded.ow2asm.MethodVisitor;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import lombok.var;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nukkit.utils.Utils.dynamic;
import static com.nimbusds.jose.shaded.ow2asm.Opcodes.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class Enchantment implements Cloneable {

    public static final Enchantment[] EMPTY_ARRAY = new Enchantment[0];

    public static final int CUSTOM_ENCHANTMENT_ID = dynamic(256);

    protected static Enchantment[] enchantments;

    protected static Map<Identifier, Enchantment> customEnchantments = new Object2ObjectLinkedOpenHashMap<>();

    public static final int ID_PROTECTION_ALL = 0;
    public static final String NAME_PROTECTION_ALL = "protection";

    public static final int ID_PROTECTION_FIRE = 1;
    public static final String NAME_PROTECTION_FIRE = "fire_protection";

    public static final int ID_PROTECTION_FALL = 2;
    public static final String NAME_PROTECTION_FALL = "feather_falling";

    public static final int ID_PROTECTION_EXPLOSION = 3;
    public static final String NAME_PROTECTION_EXPLOSION = "blast_protection";

    public static final int ID_PROTECTION_PROJECTILE = 4;
    public static final String NAME_PROTECTION_PROJECTILE = "projectile_protection";

    public static final int ID_THORNS = 5;
    public static final String NAME_THORNS = "thorns";

    public static final int ID_WATER_BREATHING = 6;
    public static final String NAME_WATER_BREATHING = "respiration";

    public static final int ID_WATER_WALKER = 7;
    public static final String NAME_WATER_WALKER = "depth_strider";

    public static final int ID_WATER_WORKER = 8;
    public static final String NAME_WATER_WORKER = "aqua_affinity";

    public static final int ID_DAMAGE_ALL = 9;
    public static final String NAME_DAMAGE_ALL = "sharpness";

    public static final int ID_DAMAGE_SMITE = 10;
    public static final String NAME_DAMAGE_SMITE = "smite";

    public static final int ID_DAMAGE_ARTHROPODS = 11;
    public static final String NAME_DAMAGE_ARTHROPODS = "bane_of_arthropods";

    public static final int ID_KNOCKBACK = 12;
    public static final String NAME_KNOCKBACK = "knockback";

    public static final int ID_FIRE_ASPECT = 13;
    public static final String NAME_FIRE_ASPECT = "fire_aspect";

    public static final int ID_LOOTING = 14;
    public static final String NAME_LOOTING = "looting";

    public static final int ID_EFFICIENCY = 15;
    public static final String NAME_EFFICIENCY = "efficiency";

    public static final int ID_SILK_TOUCH = 16;
    public static final String NAME_SILK_TOUCH = "silk_touch";

    public static final int ID_DURABILITY = 17;
    public static final String NAME_DURABILITY = "unbreaking";

    public static final int ID_FORTUNE_DIGGING = 18;
    public static final String NAME_FORTUNE_DIGGING = "fortune";

    public static final int ID_BOW_POWER = 19;
    public static final String NAME_BOW_POWER = "power";

    public static final int ID_BOW_KNOCKBACK = 20;
    public static final String NAME_BOW_KNOCKBACK = "punch";

    public static final int ID_BOW_FLAME = 21;
    public static final String NAME_BOW_FLAME = "flame";

    public static final int ID_BOW_INFINITY = 22;
    public static final String NAME_BOW_INFINITY = "infinity";

    public static final int ID_FORTUNE_FISHING = 23;
    public static final String NAME_FORTUNE_FISHING = "luck_of_the_sea";

    public static final int ID_LURE = 24;
    public static final String NAME_LURE = "lure";

    public static final int ID_FROST_WALKER = 25;
    public static final String NAME_FROST_WALKER = "frost_walker";

    public static final int ID_MENDING = 26;
    public static final String NAME_MENDING = "mending";

    public static final int ID_BINDING_CURSE = 27;
    public static final String NAME_BINDING_CURSE = "binding";

    public static final int ID_VANISHING_CURSE = 28;
    public static final String NAME_VANISHING_CURSE = "vanishing";

    public static final int ID_TRIDENT_IMPALING = 29;
    public static final String NAME_TRIDENT_IMPALING = "impaling";

    public static final int ID_TRIDENT_RIPTIDE = 30;
    public static final String NAME_TRIDENT_RIPTIDE = "riptide";

    public static final int ID_TRIDENT_LOYALTY = 31;
    public static final String NAME_TRIDENT_LOYALTY = "loyalty";

    public static final int ID_TRIDENT_CHANNELING = 32;
    public static final String NAME_TRIDENT_CHANNELING = "channeling";

    public static final int ID_CROSSBOW_MULTISHOT = 33;
    public static final String NAME_CROSSBOW_MULTISHOT = "multishot";

    public static final int ID_CROSSBOW_PIERCING = 34;
    public static final String NAME_CROSSBOW_PIERCING = "piercing";

    public static final int ID_CROSSBOW_QUICK_CHARGE = 35;
    public static final String NAME_CROSSBOW_QUICK_CHARGE = "quick_charge";

    public static final int ID_SOUL_SPEED = 36;
    public static final String NAME_SOUL_SPEED = "soul_speed";

    public static final int ID_SWIFT_SNEAK = 37;
    public static final String NAME_SWIFT_SNEAK = "swift_sneak";

    public static void init() {
        enchantments = new Enchantment[256];

        enchantments[ID_PROTECTION_ALL] = new EnchantmentProtectionAll();
        enchantments[ID_PROTECTION_FIRE] = new EnchantmentProtectionFire();
        enchantments[ID_PROTECTION_FALL] = new EnchantmentProtectionFall();
        enchantments[ID_PROTECTION_EXPLOSION] = new EnchantmentProtectionExplosion();
        enchantments[ID_PROTECTION_PROJECTILE] = new EnchantmentProtectionProjectile();
        enchantments[ID_THORNS] = new EnchantmentThorns();
        enchantments[ID_WATER_BREATHING] = new EnchantmentWaterBreath();
        enchantments[ID_WATER_WORKER] = new EnchantmentWaterWorker();
        enchantments[ID_WATER_WALKER] = new EnchantmentWaterWalker();
        enchantments[ID_DAMAGE_ALL] = new EnchantmentDamageAll();
        enchantments[ID_DAMAGE_SMITE] = new EnchantmentDamageSmite();
        enchantments[ID_DAMAGE_ARTHROPODS] = new EnchantmentDamageArthropods();
        enchantments[ID_KNOCKBACK] = new EnchantmentKnockback();
        enchantments[ID_FIRE_ASPECT] = new EnchantmentFireAspect();
        enchantments[ID_LOOTING] = new EnchantmentLootWeapon();
        enchantments[ID_EFFICIENCY] = new EnchantmentEfficiency();
        enchantments[ID_SILK_TOUCH] = new EnchantmentSilkTouch();
        enchantments[ID_DURABILITY] = new EnchantmentDurability();
        enchantments[ID_FORTUNE_DIGGING] = new EnchantmentLootDigging();
        enchantments[ID_BOW_POWER] = new EnchantmentBowPower();
        enchantments[ID_BOW_KNOCKBACK] = new EnchantmentBowKnockback();
        enchantments[ID_BOW_FLAME] = new EnchantmentBowFlame();
        enchantments[ID_BOW_INFINITY] = new EnchantmentBowInfinity();
        enchantments[ID_FORTUNE_FISHING] = new EnchantmentLootFishing();
        enchantments[ID_LURE] = new EnchantmentLure();
        enchantments[ID_FROST_WALKER] = new EnchantmentFrostWalker();
        enchantments[ID_MENDING] = new EnchantmentMending();
        enchantments[ID_BINDING_CURSE] = new EnchantmentBindingCurse();
        enchantments[ID_VANISHING_CURSE] = new EnchantmentVanishingCurse();
        enchantments[ID_TRIDENT_IMPALING] = new EnchantmentTridentImpaling();
        enchantments[ID_TRIDENT_LOYALTY] = new EnchantmentTridentLoyalty();
        enchantments[ID_TRIDENT_RIPTIDE] = new EnchantmentTridentRiptide();
        enchantments[ID_TRIDENT_CHANNELING] = new EnchantmentTridentChanneling();
        enchantments[ID_CROSSBOW_MULTISHOT] = new EnchantmentCrossbowMultishot();
        enchantments[ID_CROSSBOW_PIERCING] = new EnchantmentCrossbowPiercing();
        enchantments[ID_CROSSBOW_QUICK_CHARGE] = new EnchantmentCrossbowQuickCharge();
        enchantments[ID_SOUL_SPEED] = new EnchantmentSoulSpeed();
        enchantments[ID_SWIFT_SNEAK] = new EnchantmentSwiftSneak();

        //custom
        customEnchantments.put(new Identifier("minecraft", NAME_PROTECTION_ALL), enchantments[0]);
        customEnchantments.put(new Identifier("minecraft", NAME_PROTECTION_FIRE), enchantments[1]);
        customEnchantments.put(new Identifier("minecraft", NAME_PROTECTION_FALL), enchantments[2]);
        customEnchantments.put(new Identifier("minecraft", NAME_PROTECTION_EXPLOSION), enchantments[3]);
        customEnchantments.put(new Identifier("minecraft", NAME_PROTECTION_PROJECTILE), enchantments[4]);
        customEnchantments.put(new Identifier("minecraft", NAME_THORNS), enchantments[5]);
        customEnchantments.put(new Identifier("minecraft", NAME_WATER_BREATHING), enchantments[6]);
        customEnchantments.put(new Identifier("minecraft", NAME_WATER_WORKER), enchantments[7]);
        customEnchantments.put(new Identifier("minecraft", NAME_WATER_WALKER), enchantments[8]);
        customEnchantments.put(new Identifier("minecraft", NAME_DAMAGE_ALL), enchantments[9]);
        customEnchantments.put(new Identifier("minecraft", NAME_DAMAGE_SMITE), enchantments[10]);
        customEnchantments.put(new Identifier("minecraft", NAME_DAMAGE_ARTHROPODS), enchantments[11]);
        customEnchantments.put(new Identifier("minecraft", NAME_KNOCKBACK), enchantments[12]);
        customEnchantments.put(new Identifier("minecraft", NAME_FIRE_ASPECT), enchantments[13]);
        customEnchantments.put(new Identifier("minecraft", NAME_LOOTING), enchantments[14]);
        customEnchantments.put(new Identifier("minecraft", NAME_EFFICIENCY), enchantments[15]);
        customEnchantments.put(new Identifier("minecraft", NAME_SILK_TOUCH), enchantments[16]);
        customEnchantments.put(new Identifier("minecraft", NAME_DURABILITY), enchantments[17]);
        customEnchantments.put(new Identifier("minecraft", NAME_FORTUNE_DIGGING), enchantments[18]);
        customEnchantments.put(new Identifier("minecraft", NAME_BOW_POWER), enchantments[19]);
        customEnchantments.put(new Identifier("minecraft", NAME_BOW_KNOCKBACK), enchantments[20]);
        customEnchantments.put(new Identifier("minecraft", NAME_BOW_FLAME), enchantments[21]);
        customEnchantments.put(new Identifier("minecraft", NAME_BOW_INFINITY), enchantments[22]);
        customEnchantments.put(new Identifier("minecraft", NAME_FORTUNE_FISHING), enchantments[23]);
        customEnchantments.put(new Identifier("minecraft", NAME_LURE), enchantments[24]);
        customEnchantments.put(new Identifier("minecraft", NAME_FROST_WALKER), enchantments[25]);
        customEnchantments.put(new Identifier("minecraft", NAME_MENDING), enchantments[26]);
        customEnchantments.put(new Identifier("minecraft", NAME_BINDING_CURSE), enchantments[27]);
        customEnchantments.put(new Identifier("minecraft", NAME_VANISHING_CURSE), enchantments[28]);
        customEnchantments.put(new Identifier("minecraft", NAME_TRIDENT_IMPALING), enchantments[29]);
        customEnchantments.put(new Identifier("minecraft", NAME_TRIDENT_RIPTIDE), enchantments[30]);
        customEnchantments.put(new Identifier("minecraft", NAME_TRIDENT_LOYALTY), enchantments[31]);
        customEnchantments.put(new Identifier("minecraft", NAME_TRIDENT_CHANNELING), enchantments[32]);
        customEnchantments.put(new Identifier("minecraft", NAME_CROSSBOW_MULTISHOT), enchantments[33]);
        customEnchantments.put(new Identifier("minecraft", NAME_CROSSBOW_PIERCING), enchantments[34]);
        customEnchantments.put(new Identifier("minecraft", NAME_CROSSBOW_QUICK_CHARGE), enchantments[35]);
        customEnchantments.put(new Identifier("minecraft", NAME_SOUL_SPEED), enchantments[36]);
        customEnchantments.put(new Identifier("minecraft", NAME_SWIFT_SNEAK), enchantments[37]);
    }

    public static OK<?> register(Enchantment enchantment, boolean registerItem) {
        Objects.requireNonNull(enchantment);
        Objects.requireNonNull(enchantment.getIdentifier());
        if (customEnchantments.containsKey(enchantment.getIdentifier())) {
            return new OK<>(false, "This identifier already exists,register custom enchantment failed!");
        }
        if (enchantment.getIdentifier().getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return new OK<>(false, "Please do not use the reserved namespace `minecraft` !");
        }
        customEnchantments.put(enchantment.getIdentifier(), enchantment);
        if (registerItem) {
            return registerCustomEnchantBook(enchantment);
        }
        return OK.TRUE;
    }

    private static int BOOK_NUMBER = 1;

    private static OK<?> registerCustomEnchantBook(Enchantment enchantment) {
        var identifier = enchantment.getIdentifier();
        assert identifier != null;
        for (int i = 1; i <= enchantment.getMaxLevel(); i++) {
            var name = "§eEnchanted Book\n§7" + enchantment.getName() + " " + getLevelString(i);
            ClassWriter classWriter = new ClassWriter(0);
            MethodVisitor methodVisitor;
            String className = "CustomBookEnchanted" + BOOK_NUMBER;
            classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, "cn/nukkit/item/customitem/" + className, null, "cn/nukkit/item/customitem/ItemCustomBookEnchanted", null);
            classWriter.visitSource(className + ".java", null);
            {
                methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(6, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitLdcInsn(identifier.toString());
                methodVisitor.visitLdcInsn(name);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "cn/nukkit/item/customitem/ItemCustomBookEnchanted", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(7, label1);
                methodVisitor.visitInsn(RETURN);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLocalVariable("this", "Lcn/nukkit/item/customitem/" + className + ";", null, label0, label2, 0);
                methodVisitor.visitMaxs(3, 1);
                methodVisitor.visitEnd();
            }
            classWriter.visitEnd();
            BOOK_NUMBER++;
            try {
                Class<? extends CustomItem> clazz = (Class<? extends CustomItem>) loadClass(Thread.currentThread().getContextClassLoader(), "cn.nukkit.item.customitem." + className, classWriter.toByteArray());
                Item.registerCustomItem(clazz).assertOK();
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                     IllegalAccessException | AssertionError e) {
                return new OK<>(false, e);
            }
        }
        return OK.TRUE;
    }

    private static WeakReference<Method> defineClassMethodRef = new WeakReference<>(null);

    @SuppressWarnings("DuplicatedCode")
    private static Class<?> loadClass(ClassLoader loader, String className, byte[] b) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> clazz;
        java.lang.reflect.Method method;
        if (defineClassMethodRef.get() == null) {
            var cls = Class.forName("java.lang.ClassLoader");
            method = cls.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            defineClassMethodRef = new WeakReference<>(method);
        } else {
            method = defineClassMethodRef.get();
        }
        Objects.requireNonNull(method).setAccessible(true);
        try {
            var args = new Object[]{className, b, 0, b.length};
            clazz = (Class<?>) method.invoke(loader, args);
        } finally {
            method.setAccessible(false);
        }
        return clazz;
    }

    public static String getLevelString(int level) {
        switch (level) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            case 6:
                return "VI";
            case 7:
                return "VII";
            case 8:
                return "VIII";
            case 9:
                return "IX";
            case 10:
                return "X";
            default:
                return "∞";
        }
    }

    public static Enchantment get(int id) {
        Enchantment enchantment = null;
        if (id >= 0 && id < enchantments.length) {
            enchantment = enchantments[id];
        }
        if (enchantment == null) {
            return new UnknownEnchantment(id);
        }
        return enchantment;
    }

    public static Enchantment getEnchantment(int id) {
        return get(id).clone();
    }

    public static Enchantment getEnchantment(String name) {
        if (Identifier.isValid(name)) {
            return customEnchantments.get(Identifier.tryParse(name));
        } else {
            return customEnchantments.get(new Identifier(Identifier.DEFAULT_NAMESPACE, name));
        }
    }

    public static Enchantment getEnchantment(@NotNull Identifier name) {
        return customEnchantments.getOrDefault(name, new UnknownEnchantment(name));
    }

    public static Enchantment[] getEnchantments() {
        return customEnchantments.values().toArray(EMPTY_ARRAY);
    }

    public static Collection<Enchantment> getRegisteredEnchantments() {
        return new ArrayList<>(customEnchantments.values());
    }

    public static Map<String, Integer> getEnchantmentName2IDMap() {
        return customEnchantments.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().getId()));
    }

    public final int id;
    private final Rarity rarity;
    public EnchantmentType type;

    protected int level = 1;

    protected final String name;

    @Nullable
    protected final Identifier identifier;

    protected Enchantment(int id, String name, Rarity rarity, EnchantmentType type) {
        this.identifier = null;
        this.id = id;
        this.rarity = rarity;
        this.type = type;

        this.name = name;
    }

    protected Enchantment(@NotNull Identifier identifier, String name, Rarity rarity, @NotNull EnchantmentType type) {
        this.identifier = identifier;
        this.id = CUSTOM_ENCHANTMENT_ID;
        this.rarity = rarity;
        this.type = type;
        this.name = name;
    }

    @Nullable
    public Identifier getIdentifier() {
        return identifier;
    }

    public int getLevel() {
        return level;
    }

    public Enchantment setLevel(int level) {
        return this.setLevel(level, true);
    }

    public Enchantment setLevel(int level, boolean safe) {
        if (!safe) {
            this.level = level;
            return this;
        }

        if (level > this.getMaxLevel()) {
            this.level = this.getMaxLevel();
        } else this.level = Math.max(level, this.getMinLevel());

        return this;
    }

    public int getId() {
        return id;
    }

    public Rarity getRarity() {
        return this.rarity;
    }

    public int getWeight() {
        return this.rarity.getWeight();
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return 1;
    }

    public int getMaxEnchantableLevel() {
        return getMaxLevel();
    }

    public int getMinEnchantAbility(int level) {
        return 1 + level * 10;
    }

    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 5;
    }

    public float getProtectionFactor(EntityDamageEvent event) {
        return 0;
    }

    public double getDamageBonus(Entity entity) {
        return 0;
    }

    public void doPostAttack(Entity attacker, Entity entity) {

    }

    public void doAttack(Entity attacker, Entity entity) {

    }

    public void doPostHurt(Entity attacker, Entity entity) {

    }

    public final boolean isCompatibleWith(Enchantment enchantment) {
        return this.checkCompatibility(enchantment) && enchantment.checkCompatibility(this);
    }

    protected boolean checkCompatibility(Enchantment enchantment) {
        return this != enchantment;
    }

    public String getName() {
        return "%enchantment." + this.name;
    }

    public boolean canEnchant(Item item) {
        return this.type.canEnchantItem(item);
    }

    public boolean isMajor() {
        return false;
    }

    public boolean isTreasure() {
        return false;
    }

    @Override
    protected Enchantment clone() {
        try {
            return (Enchantment) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public static final String[] words = {"the", "elder", "scrolls", "klaatu", "berata", "niktu", "xyzzy", "bless", "curse", "light", "darkness", "fire", "air", "earth", "water", "hot", "dry", "cold", "wet", "ignite", "snuff", "embiggen", "twist", "shorten", "stretch", "fiddle", "destroy", "imbue", "galvanize", "enchant", "free", "limited", "range", "of", "towards", "inside", "sphere", "cube", "self", "other", "ball", "mental", "physical", "grow", "shrink", "demon", "elemental", "spirit", "animal", "creature", "beast", "humanoid", "undead", "fresh", "stale"};

    public static String getRandomName() {
        HashSet<String> set = new HashSet<>();
        while (set.size() < Utils.random.nextInt(3, 6)) {
            set.add(Enchantment.words[Utils.random.nextInt(0, Enchantment.words.length)]);
        }

        String[] words = set.toArray(new String[0]);
        return String.join(" ", words);
    }

    private static class UnknownEnchantment extends Enchantment {

        protected UnknownEnchantment(int id) {
            super(id, "unknown", Rarity.VERY_RARE, EnchantmentType.ALL);
        }

        protected UnknownEnchantment(Identifier identifier) {
            super(identifier, "unknown", Rarity.VERY_RARE, EnchantmentType.ALL);
        }
    }

    public enum Rarity {
        COMMON(10),
        UNCOMMON(5),
        RARE(2),
        VERY_RARE(1);

        private final int weight;

        Rarity(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return this.weight;
        }

        public static Rarity fromWeight(int weight) {
            if (weight < 2) {
                return VERY_RARE;
            } else if (weight < 5) {
                return RARE;
            } else if (weight < 10) {
                return UNCOMMON;
            }
            return COMMON;
        }
    }
}
