package cn.nukkit.block.properties.enums;

public enum DripstoneThickness {
    TIP("tip"),
    FRUSTUM("frustum"),
    MIDDLE("middle"),
    BASE("base"),
    MERGE("merge");

    private final String name;

    DripstoneThickness(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
