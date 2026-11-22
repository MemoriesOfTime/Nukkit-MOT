package cn.nukkit.level.format;

import cn.nukkit.Player;

import java.util.List;

/**
 * Revision-cached entity presence query exposed by a chunk's live entity map.
 */
public interface EntityPresenceIndex {

    /**
     * Immutable player snapshot in the complete entity map's iteration order.
     * Returns null when concurrent membership mutation prevents a stable snapshot.
     * Implementations without this index retain the complete entity traversal.
     */
    default List<Player> playersInIterationOrder() {
        return null;
    }
}
