package cn.nukkit.block.blockproperty.value;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public enum StructureBlockType {
    INVALID("Structure Block"),

    DATA("Data Structure Block"),

    SAVE("Save Structure Block"),

    LOAD("Load Structure Block"),

    CORNER("Corner Structure Block"),

    EXPORT("Export Structure Block");

    private final String englishName;

    @NotNull
    public String getEnglishName() {
        return englishName;
    }

    private static final StructureBlockType[] VALUES = StructureBlockType.values();

    public static StructureBlockType from(int id) {
        return VALUES[id];
    }
}
