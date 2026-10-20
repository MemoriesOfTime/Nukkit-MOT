package cn.nukkit.level.format.generic.serializer;

import cn.nukkit.level.format.generic.BaseFullChunk;

import java.lang.ref.WeakReference;

/** Identifies the live chunk state captured before network serialization. */
public final class ChunkRequestToken {
    private final WeakReference<BaseFullChunk> source;
    private final long revision;

    public ChunkRequestToken(BaseFullChunk chunk) {
        this.source = new WeakReference<>(chunk);
        this.revision = chunk.getMutationRevision();
    }

    public boolean matches(BaseFullChunk chunk) {
        return chunk != null && source.get() == chunk && chunk.getMutationRevision() == revision;
    }
}
