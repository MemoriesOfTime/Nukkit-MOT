package cn.nukkit.camera.data;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;

public record Pos(float x, float y, float z) implements SerializableData {
    public CompoundTag serialize() {
        return new CompoundTag("pos")
                .putList("pos", new ListTag<>("pos")
                        .add(new FloatTag(x))
                        .add(new FloatTag(y))
                        .add(new FloatTag(z))
                );
    }
}
