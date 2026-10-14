package cn.nukkit.level.format;

import cn.nukkit.level.format.generic.BaseFullChunk;

/**
 * A registered read barrier, retained from submission through main-thread publication.
 * Implementations invalidate it when a newer snapshot is accepted, including after decode.
 * No method other than read may wait for disk or a lock held across disk IO.
 */
public interface ChunkReadTicket extends AutoCloseable {

    /** Commit pending snapshots and decode on an IO worker. Null means proven absence. */
    BaseFullChunk read();

    /**
     * Atomically validate freshness and install on the server thread. Returns the canonical
     * chunk (possibly an existing winner), or null if the ticket became stale. When read
     * proved absence, the argument may be the provider's empty chunk for these coordinates.
     */
    BaseFullChunk tryMount(BaseFullChunk decoded);

    /** Release registration on success, failure, rejection or cancellation; idempotent. */
    @Override
    void close();
}
