package cn.nukkit.camera.data;

import cn.nukkit.nbt.tag.CompoundTag;

public record Ease(float time, EaseType easeType) implements SerializableData {
    @Override
    public CompoundTag serialize() {
        return new CompoundTag("ease")
                .putFloat("time", time)
                .putString("type", easeType.getType());
    }
}
