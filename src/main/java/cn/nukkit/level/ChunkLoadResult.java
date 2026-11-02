package cn.nukkit.level;

import cn.nukkit.level.format.generic.BaseFullChunk;

/**
 * Terminal outcome of an asynchronous chunk request. A successful result is published on
 * the server thread after mounting and initialization, never merely after disk decoding.
 * CREATED means the read proved absence and an empty, not yet generated chunk was mounted.
 */
public record ChunkLoadResult(Status status, BaseFullChunk chunk, Throwable failure) {

    public enum Status {
        LOADED, CREATED, FAILED, CANCELLED, REJECTED, DISABLED
    }

    public boolean isSuccess() {
        return this.status == Status.LOADED || this.status == Status.CREATED;
    }
}
