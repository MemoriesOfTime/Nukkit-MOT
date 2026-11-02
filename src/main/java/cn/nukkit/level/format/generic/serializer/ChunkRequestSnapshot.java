package cn.nukkit.level.format.generic.serializer;

import cn.nukkit.level.format.generic.BaseFullChunk;

import java.lang.ref.WeakReference;

/** Native source identity and monotonic revision, captured before cloning for the worker. */
public final class ChunkRequestSnapshot {
    private final WeakReference<BaseFullChunk> source;
    private final long revision;

    public ChunkRequestSnapshot(BaseFullChunk source) {
        this.source = new WeakReference<>(source);
        this.revision = source.getNetworkRevision();
    }

    public boolean matches(BaseFullChunk current) {
        return current != null && current == this.source.get()
                && current.getNetworkRevision() == this.revision;
    }
}
