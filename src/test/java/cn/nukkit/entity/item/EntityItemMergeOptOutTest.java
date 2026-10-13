package cn.nukkit.entity.item;

import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityItemMergeOptOutTest {

    private static EntityItem entity(boolean mergeable) {
        EntityItem entity = mock(EntityItem.class, CALLS_REAL_METHODS);
        entity.namedTag = new CompoundTag();
        entity.mergeItems = mergeable;
        return entity;
    }

    @Test
    void bothSidesMustAllowMerging() {
        assertTrue(entity(true).mergeableWith(entity(true)));
        assertFalse(entity(false).mergeableWith(entity(true)));
        assertFalse(entity(true).mergeableWith(entity(false)));
        assertFalse(entity(true).mergeableWith(null));
    }

    @Test
    void optOutIsReadFromTheSavedEntity() {
        assertTrue(EntityItem.mergeableFromNbt(new CompoundTag()));
        assertTrue(EntityItem.mergeableFromNbt(null));
        assertFalse(EntityItem.mergeableFromNbt(new CompoundTag().putBoolean("Mergeable", false)));
        assertTrue(EntityItem.mergeableFromNbt(new CompoundTag().putBoolean("Mergeable", true)));
    }

    @Test
    void setterWritesTheFlagOnlyWhileOptedOut() {
        EntityItem entity = entity(true);
        entity.setMergeable(false);
        assertFalse(entity.isMergeable());
        assertTrue(entity.namedTag.contains("Mergeable"));
        assertFalse(entity.namedTag.getBoolean("Mergeable"));

        entity.setMergeable(true);
        assertTrue(entity.isMergeable());
        assertFalse(entity.namedTag.contains("Mergeable"));
    }
}
