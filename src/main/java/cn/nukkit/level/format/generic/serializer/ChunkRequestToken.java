package cn.nukkit.level.format.generic.serializer;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;

import java.lang.ref.WeakReference;

/** Identifies the live chunk state captured before network serialization. */
public final class ChunkRequestToken {
    private final WeakReference<BaseFullChunk> source;
    private final long revision;
    private final Level.ChunkRequestOwner requestOwner;

    public ChunkRequestToken(BaseFullChunk chunk) {
        this(chunk, null);
    }

    public ChunkRequestToken(BaseFullChunk chunk, Level.ChunkRequestOwner requestOwner) {
        this.source = new WeakReference<>(chunk);
        this.revision = chunk.getMutationRevision();
        this.requestOwner = requestOwner;
    }

    public Level.ChunkRequestOwner requestOwner() {
        return this.requestOwner;
    }

    public boolean matches(BaseFullChunk chunk) {
        return chunk != null && source.get() == chunk && chunk.getMutationRevision() == revision;
    }
}
