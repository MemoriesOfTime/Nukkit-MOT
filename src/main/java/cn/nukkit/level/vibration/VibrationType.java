package cn.nukkit.level.vibration;

import java.util.Locale;
import java.util.Set;

/**
 * Sculk-detectable vibration types, each with a frequency (1-15) per JE VibrationSystem.
 */
public enum VibrationType {
    STEP(1),
    SWIM(1),
    FLAP(1),
    PROJECTILE_LAND(2),
    HIT_GROUND(2),
    SPLASH(2),
    ITEM_INTERACT_FINISH(3),
    PROJECTILE_SHOOT(3),
    INSTRUMENT_PLAY(3),
    ENTITY_ACTION(4),
    ELYTRA_GLIDE(4),
    UNEQUIP(4),
    ENTITY_DISMOUNT(5),
    EQUIP(5),
    ENTITY_INTERACT(6),
    SHEAR(6),
    ENTITY_MOUNT(6),
    ENTITY_DAMAGE(7),
    DRINK(8),
    EAT(8),
    CONTAINER_CLOSE(9),
    BLOCK_CLOSE(9),
    BLOCK_DEACTIVATE(9),
    BLOCK_DETACH(9),
    CONTAINER_OPEN(10),
    BLOCK_OPEN(10),
    BLOCK_ACTIVATE(10),
    BLOCK_ATTACH(10),
    PRIME_FUSE(10),
    NOTE_BLOCK_PLAY(10),
    BLOCK_CHANGE(11),
    BLOCK_DESTROY(12),
    FLUID_PICKUP(12),
    BLOCK_PLACE(13),
    FLUID_PLACE(13),
    ENTITY_PLACE(14),
    LIGHTNING_STRIKE(14),
    TELEPORT(14),
    ENTITY_DIE(15),
    EXPLODE(15),
    SCULK_SENSOR_TENDRILS_CLICKING(1),
    SHRIEK(1),
    RESONATE_1(1),
    RESONATE_2(2),
    RESONATE_3(3),
    RESONATE_4(4),
    RESONATE_5(5),
    RESONATE_6(6),
    RESONATE_7(7),
    RESONATE_8(8),
    RESONATE_9(9),
    RESONATE_10(10),
    RESONATE_11(11),
    RESONATE_12(12),
    RESONATE_13(13),
    RESONATE_14(14),
    RESONATE_15(15);

    public final String identifier = "minecraft:" + this.name().toLowerCase(Locale.ENGLISH);
    public final int frequency;

    VibrationType(int frequency) {
        if (frequency < 1 || frequency > 15) throw new IllegalArgumentException("frequency must between 1 and 15");
        this.frequency = frequency;
    }

    /**
     * JE {@code #minecraft:ignore_vibrations_sneaking} game event tag: events that a sneaking
     * entity (stepping carefully) does NOT emit. The list mirrors the vanilla tag values.
     */
    public static final Set<VibrationType> IGNORE_VIBRATIONS_SNEAKING = Set.of(
            HIT_GROUND,
            PROJECTILE_SHOOT,
            STEP,
            SWIM,
            ITEM_INTERACT_FINISH
    );
}
