package cn.nukkit.level.generator.template;

public class BlockEntry {
    private final int id;
    private final int meta;

    public BlockEntry(final int id) {
        this(id, 0);
    }

    public BlockEntry(final int id, final int meta) {
        this.id = id;
        this.meta = meta;
    }

    public int getId() {
        return id;
    }

    public int getMeta() {
        return meta;
    }
}
