package cn.nukkit.level.format.leveldb.structure;

public enum ChunkState {
    NEW,
    GENERATED,
    POPULATED,
    FINISHED;

    public boolean canSend() {
        return this.ordinal() >= 2;
    }
}
