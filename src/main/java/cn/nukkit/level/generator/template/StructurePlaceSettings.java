package cn.nukkit.level.generator.template;

import cn.nukkit.nbt.tag.CompoundTag;

import java.util.function.Consumer;

public class StructurePlaceSettings {
    public static final StructurePlaceSettings DEFAULT = new StructurePlaceSettings();

    private boolean ignoreEntities = true;
    private boolean ignoreAir;
    private int integrity = 100;
    private Consumer<CompoundTag> blockActorProcessor;

    public boolean isIgnoreEntities() {
        return ignoreEntities;
    }

    public StructurePlaceSettings setIgnoreEntities(final boolean ignoreEntities) {
        this.ignoreEntities = ignoreEntities;
        return this;
    }

    public boolean isIgnoreAir() {
        return ignoreAir;
    }

    public StructurePlaceSettings setIgnoreAir(final boolean ignoreAir) {
        this.ignoreAir = ignoreAir;
        return this;
    }

    public int getIntegrity() {
        return integrity;
    }

    public StructurePlaceSettings setIntegrity(final int integrity) {
        this.integrity = integrity;
        return this;
    }

    public Consumer<CompoundTag> getBlockActorProcessor() {
        return blockActorProcessor;
    }

    public StructurePlaceSettings setBlockActorProcessor(final Consumer<CompoundTag> blockActorProcessor) {
        this.blockActorProcessor = blockActorProcessor;
        return this;
    }
}
