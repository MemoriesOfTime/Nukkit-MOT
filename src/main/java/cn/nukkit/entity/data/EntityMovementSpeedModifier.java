package cn.nukkit.entity.data;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class EntityMovementSpeedModifier {

    public static final String FREEZING = "minecraft:freezing";
    public static final String SOUL_SPEED_ENCHANTMENT = "minecraft:soul_speed_enchantment";
    public static final String SPRINTING = "minecraft:sprinting";
    public static final String SNEAKING = "minecraft:sneaking";
    public static final String CRAWLING = "minecraft:crawling";
    public static final String EFFECT_SPEED = "minecraft:speed_effect";
    public static final String EFFECT_SLOWNESS = "minecraft:slowness_effect";

    private final String identifier;
    private final float value;
    private final Operation operation;
    private final boolean send;

    public EntityMovementSpeedModifier(String identifier, float value, Operation operation) {
        this(identifier, value, operation, true);
    }

    public EntityMovementSpeedModifier(String identifier, float value, Operation operation, boolean send) {
        this.identifier = identifier;
        this.value = value;
        this.operation = operation;
        this.send = send;
    }

    public static EntityMovementSpeedModifier of(String identifier, float value, Operation operation) {
        return new EntityMovementSpeedModifier(identifier, value, operation);
    }

    public static EntityMovementSpeedModifier of(String identifier, float value, Operation operation, boolean send) {
        return new EntityMovementSpeedModifier(identifier, value, operation, send);
    }

    public enum Operation {
        ADD,
        MULTIPLY
    }
}