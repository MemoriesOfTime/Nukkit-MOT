package cn.nukkit.entity;

import cn.nukkit.utils.ServerException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attribute
 *
 * @author Box, MagicDroidX(code), PeratX @ Nukkit Project
 * @since Nukkit 1.0 | Nukkit API 1.0.0
 */
public class Attribute implements Cloneable {

    public static final int ABSORPTION = 0;
    public static final int SATURATION = 1;
    public static final int EXHAUSTION = 2;
    public static final int KNOCKBACK_RESISTANCE = 3;
    public static final int MAX_HEALTH = 4;
    public static final int MOVEMENT_SPEED = 5;
    public static final int FOLLOW_RANGE = 6;
    public static final int FOOD = 7, MAX_HUNGER = FOOD;
    public static final int ATTACK_DAMAGE = 8;
    public static final int EXPERIENCE_LEVEL = 9;
    public static final int EXPERIENCE = 10;
    public static final int UNDERWATER_MOVEMENT = 11;
    public static final int LUCK = 12;
    public static final int FALL_DAMAGE = 13;
    public static final int HORSE_JUMP_STRENGTH = 14;
    public static final int ZOMBIE_SPAWN_REINFORCEMENTS = 15;
    public static final int LAVA_MOVEMENT = 16;

    protected static Map<Integer, Attribute> attributes = new HashMap<>();

    protected float minValue;
    protected float maxValue;
    protected float defaultValue;
    protected float currentValue;
    protected String name;
    protected boolean shouldSend;
    private int id;
    
    // 修改器系统
    private final Map<UUID, AttributeModifier> modifiers = new ConcurrentHashMap<>();

    // 默认来源的UUID，用于setMovementSpeed等原有API
    public static final UUID DEFAULT_SOURCE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Attribute(int id, String name, float minValue, float maxValue, float defaultValue, boolean shouldSend) {
        this.id = id;
        this.name = name;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
        this.shouldSend = shouldSend;
        this.currentValue = this.defaultValue;
    }

    public static void init() {
        addAttribute(ABSORPTION, "minecraft:absorption", 0.00f, 340282346638528859811704183484516925440.00f, 0.00f);
        addAttribute(SATURATION, "minecraft:player.saturation", 0.00f, 20.00f, 5.00f);
        addAttribute(EXHAUSTION, "minecraft:player.exhaustion", 0.00f, 5.00f, 0.00f, false);
        addAttribute(KNOCKBACK_RESISTANCE, "minecraft:knockback_resistance", 0.00f, 1.00f, 0.00f);
        addAttribute(MAX_HEALTH, "minecraft:health", 0.00f, 20.00f, 20.00f);
        addAttribute(MOVEMENT_SPEED, "minecraft:movement", 0.00f, 340282346638528859811704183484516925440.00f, 0.10f);
        addAttribute(FOLLOW_RANGE, "minecraft:follow_range", 0.00f, 2048.00f, 16.00f, false);
        addAttribute(MAX_HUNGER, "minecraft:player.hunger", 0.00f, 20.00f, 20.00f);
        addAttribute(ATTACK_DAMAGE, "minecraft:attack_damage", 0.00f, 340282346638528859811704183484516925440.00f, 1.00f, false);
        addAttribute(EXPERIENCE_LEVEL, "minecraft:player.level", 0.00f, 24791.00f, 0.00f);
        addAttribute(EXPERIENCE, "minecraft:player.experience", 0.00f, 1.00f, 0.00f);
        addAttribute(UNDERWATER_MOVEMENT, "minecraft:underwater_movement", 0.0f, 340282346638528859811704183484516925440.0f, 0.02f);
        addAttribute(LUCK, "minecraft:luck", -1024.0f, 1024.0f, 0.0f);
        addAttribute(FALL_DAMAGE, "minecraft:fall_damage", 0.0f, 340282346638528859811704183484516925440.0f, 1.0f);
        addAttribute(HORSE_JUMP_STRENGTH, "minecraft:horse.jump_strength", 0.0f, 2.0f, 0.7f);
        addAttribute(ZOMBIE_SPAWN_REINFORCEMENTS, "minecraft:zombie.spawn_reinforcements", 0.0f, 1.0f, 0.0f);
        addAttribute(LAVA_MOVEMENT, "minecraft:lava_movement", 0.00f, 340282346638528859811704183484516925440.00f, 0.02f);
    }

    public static Attribute addAttribute(int id, String name, float minValue, float maxValue, float defaultValue) {
        return addAttribute(id, name, minValue, maxValue, defaultValue, true);
    }

    public static Attribute addAttribute(int id, String name, float minValue, float maxValue, float defaultValue, boolean shouldSend) {
        if (minValue > maxValue || defaultValue > maxValue || defaultValue < minValue) {
            throw new IllegalArgumentException("Invalid ranges: min value: " + minValue + ", max value: " + maxValue + ", defaultValue: " + defaultValue);
        }

        return attributes.put(id, new Attribute(id, name, minValue, maxValue, defaultValue, shouldSend));
    }

    public static Attribute getAttribute(int id) {
        Attribute attribute = attributes.get(id);
        if (attribute != null) {
            return attribute.clone();
        }
        throw new ServerException("Attribute id: " + id + " not found");
    }

    /**
     * @param name name
     * @return null|Attribute
     */
    public static Attribute getAttributeByName(String name) {
        for (Attribute a : attributes.values()) {
            if (Objects.equals(a.name, name)) {
                return a.clone();
            }
        }
        return null;
    }
    
    // 修改器管理方法
    
    /**
     * 添加属性修改器
     * @param modifier 修改器
     * @return 当前属性实例
     */
    public Attribute addModifier(AttributeModifier modifier) {
        if (modifier == null) {
            throw new IllegalArgumentException("Modifier cannot be null");
        }
        this.modifiers.put(modifier.getUuid(), modifier);
        return this;
    }
    
    /**
     * 移除属性修改器
     * @param uuid 修改器UUID
     * @return 当前属性实例
     */
    public Attribute removeModifier(UUID uuid) {
        if (this.modifiers.remove(uuid) != null) {
        }
        return this;
    }
    
    /**
     * 移除属性修改器
     * @param modifier 修改器
     * @return 当前属性实例
     */
    public Attribute removeModifier(AttributeModifier modifier) {
        return removeModifier(modifier.getUuid());
    }
    
    /**
     * 根据名称移除属性修改器
     * @param name 修改器名称
     * @return 当前属性实例
     */
    public Attribute removeModifier(String name) {
        if (name == null || name.isEmpty()) {
            return this;
        }
        
        boolean removed = false;
        Iterator<Map.Entry<UUID, AttributeModifier>> iterator = this.modifiers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AttributeModifier> entry = iterator.next();
            if (name.equals(entry.getValue().getName())) {
                iterator.remove();
                removed = true;
            }
        }
        
        if (removed) {
        }
        return this;
    }
    
    /**
     * 根据名称获取修改器
     * @param name 修改器名称
     * @return 修改器，如果不存在则返回null
     */
    public AttributeModifier getModifier(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        
        for (AttributeModifier modifier : this.modifiers.values()) {
            if (name.equals(modifier.getName())) {
                return modifier;
            }
        }
        return null;
    }
    
    /**
     * 根据UUID获取修改器
     * @param uuid 修改器UUID
     * @return 修改器，如果不存在则返回null
     */
    public AttributeModifier getModifier(UUID uuid) {
        return this.modifiers.get(uuid);
    }
    
    /**
     * 检查是否存在指定名称的修改器
     * @param name 修改器名称
     * @return 是否存在
     */
    public boolean hasModifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        for (AttributeModifier modifier : this.modifiers.values()) {
            if (name.equals(modifier.getName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否存在指定UUID的修改器
     * @param uuid 修改器UUID
     * @return 是否存在
     */
    public boolean hasModifier(UUID uuid) {
        return this.modifiers.containsKey(uuid);
    }
    
    /**
     * 获取所有修改器
     * @return 修改器集合
     */
    public Collection<AttributeModifier> getModifiers() {
        return new ArrayList<>(this.modifiers.values());
    }
    
    /**
     * 清除所有修改器
     * @return 当前属性实例
     */
    public Attribute clearModifiers() {
        if (!this.modifiers.isEmpty()) {
            this.modifiers.clear();
        }
        return this;
    }
    
    /**
     * 计算应用修改器后的值
     * @return 修改后的值
     */
    private float getModifiedValue() {
        if (this.modifiers.isEmpty()) {
            return this.currentValue;
        }
        
        // 按操作类型分组
        List<AttributeModifier> additions = new ArrayList<>();
        List<AttributeModifier> baseMultipliers = new ArrayList<>();
        List<AttributeModifier> totalMultipliers = new ArrayList<>();
        List<AttributeModifier> caps = new ArrayList<>();
        
        for (AttributeModifier modifier : this.modifiers.values()) {
            switch (modifier.getOperation()) {
                case ADDITION:
                    additions.add(modifier);
                    break;
                case MULTIPLY_BASE:
                    baseMultipliers.add(modifier);
                    break;
                case MULTIPLY_TOTAL:
                    totalMultipliers.add(modifier);
                    break;
                case CAP:
                    caps.add(modifier);
                    break;
            }
        }
        
        // 计算最终值
        double result = this.currentValue;
        
        // 1. 应用加法修改器
        for (AttributeModifier modifier : additions) {
            result += modifier.getAmount();
        }
        
        // 2. 应用基础乘法修改器
        if (!baseMultipliers.isEmpty()) {
            double multiplier = 1.0;
            for (AttributeModifier modifier : baseMultipliers) {
                multiplier += modifier.getAmount();
            }
            result *= multiplier;
        }
        
        // 3. 应用总乘法修改器
        for (AttributeModifier modifier : totalMultipliers) {
            result *= (1.0 + modifier.getAmount());
        }
        
        // 4. 应用上限修改器
        for (AttributeModifier modifier : caps) {
            result = Math.min(result, modifier.getAmount());
        }
        // 确保结果在有效范围内
        return (float) Math.min(Math.max(result, this.minValue), this.maxValue);
    }

    // 原有方法保持兼容性
    
    public float getMinValue() {
        return this.minValue;
    }

    public Attribute setMinValue(float minValue) {
        if (minValue > this.maxValue) {
            throw new IllegalArgumentException("Value " + minValue + " is bigger than the maxValue!");
        }
        this.minValue = minValue;
        return this;
    }

    public float getMaxValue() {
        return this.maxValue;
    }

    public Attribute setMaxValue(float maxValue) {
        if (maxValue < this.minValue) {
            throw new IllegalArgumentException("Value " + maxValue + " is bigger than the minValue!");
        }
        this.maxValue = maxValue;
        return this;
    }

    public float getDefaultValue() {
        return this.defaultValue;
    }

    public Attribute setDefaultValue(float defaultValue) {
        if (defaultValue > this.maxValue || defaultValue < this.minValue) {
            throw new IllegalArgumentException("Value " + defaultValue + " exceeds the range!");
        }
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * 获取当前值（包含修改器效果）
     * @return 当前值
     */
    public float getValue() {
        // 如果有修改器，返回修改后的值；否则返回原始值
        return this.modifiers.isEmpty() ? this.currentValue : getModifiedValue();
    }

    public Attribute setValue(float value) {
        return setValue(value, true);
    }

    public Attribute setValue(float value, boolean fit) {
        if (value > this.maxValue || value < this.minValue) {
            if (!fit) {
                throw new IllegalArgumentException("Value " + value + " exceeds the range!");
            }
            value = Math.min(Math.max(value, this.minValue), this.maxValue);
        }
        this.currentValue = value;
        return this;
    }
    
    /**
     * 设置默认来源的值（兼容原有API）
     * @param value 值
     * @return 当前属性实例
     */
    public Attribute setDefaultSourceValue(float value) {
        // 移除旧的默认来源修改器
        removeModifier(DEFAULT_SOURCE_UUID);
        
        // 如果值不等于默认值，添加修改器
        if (value != this.defaultValue) {
            AttributeModifier defaultModifier = new AttributeModifier(
                DEFAULT_SOURCE_UUID,
                "default_source",
                value - this.defaultValue,
                AttributeModifier.Operation.ADDITION
            );
            addModifier(defaultModifier);
        }
        
        return this;
    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

    public boolean isSyncable() {
        return this.shouldSend;
    }

    @Override
    public Attribute clone() {
        try {
            Attribute cloned = (Attribute) super.clone();
            // 深拷贝修改器映射
            cloned.modifiers.clear();
            cloned.modifiers.putAll(this.modifiers);
            return cloned;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
