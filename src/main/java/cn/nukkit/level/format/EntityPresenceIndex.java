package cn.nukkit.level.format;

/**
 * Revision-cached entity presence query exposed by a chunk's live entity map.
 * Concurrent mutations report {@code true} conservatively so callers never skip
 * a chunk while an insertion is in flight.
 */
public interface EntityPresenceIndex {

    boolean hasAnyEntities();

    boolean hasPickupEntities(boolean itemsOnly);

    boolean hasRideableEntities();
}
