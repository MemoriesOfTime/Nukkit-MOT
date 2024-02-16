package cn.nukkit.camera.data;

import cn.nukkit.nbt.tag.CompoundTag;

public record Rot(float x, float y) implements SerializableData {
    public CompoundTag serialize() {
        return new CompoundTag("rot")
                .putFloat("x", x)
                .putFloat("y", y);
    }
}
