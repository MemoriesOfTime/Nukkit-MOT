package cn.nukkit.utils;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlockColorHashContractTest {
    @Test void equalColorFindsStoredValue() {
        var map = new HashMap<BlockColor, String>();
        map.put(new BlockColor(10, 20, 30, 40), "stored");
        assertEquals("stored", map.get(new BlockColor(10, 20, 30, 40)));
    }
    @Test void equalityDeduplicatesColors() {
        var set = new HashSet<BlockColor>();
        set.add(new BlockColor(0, 0, 0, 0)); set.add(new BlockColor(0, 0, 0, 0));
        set.add(new BlockColor(0, 0, 0, 255)); assertEquals(2, set.size());
    }
    @Test void equalSubclassKeepsHashWhenPackedColorGetterIsOverridden() {
        var original = new BlockColor(10, 20, 30, 40);
        var overridden = new BlockColor(10, 20, 30, 40) {
            @Override public int getARGB() { return 0; }
        };
        assertEquals(original, overridden);
        assertEquals(overridden, original);
        assertEquals(original.hashCode(), overridden.hashCode());
        var map = new HashMap<BlockColor, String>();
        map.put(original, "original");
        assertEquals("original", map.get(overridden));
        map.clear();
        map.put(overridden, "overridden");
        assertEquals("overridden", map.get(original));
    }
}
