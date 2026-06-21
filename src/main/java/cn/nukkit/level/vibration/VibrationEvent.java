package cn.nukkit.level.vibration;

import cn.nukkit.math.Vector3;

/**
 * A vibration that occurred in the world. Adapted from PowerNukkitX.
 *
 * @param initiator what caused the vibration (Block, Entity, ...)
 * @param source    vibration source position
 * @param type      vibration type
 */
public record VibrationEvent(Object initiator, Vector3 source, VibrationType type) {
}
