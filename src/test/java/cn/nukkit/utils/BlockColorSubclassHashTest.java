package cn.nukkit.utils;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class BlockColorSubclassHashTest {
    @Test
    void fieldEqualSubclassKeepsHashWhenPackedColorGetterIsOverridden() {
        BlockColor ordinary=new BlockColor(10,20,30,40);
        BlockColor overridden=new BlockColor(10,20,30,40){
            @Override public int getARGB(){return 0;}
        };
        assertEquals(ordinary,overridden);
        assertEquals(overridden,ordinary);
        assertEquals(ordinary.hashCode(),overridden.hashCode(),"hash must use fields compared by equals");
        var forward=new HashMap<BlockColor,String>();forward.put(ordinary,"plain");
        assertEquals("plain",forward.get(overridden));
        var reverse=new HashMap<BlockColor,String>();reverse.put(overridden,"derived");
        assertEquals("derived",reverse.get(ordinary));
        var set=new HashSet<BlockColor>();set.add(ordinary);set.add(overridden);
        assertEquals(1,set.size());
        assertEquals(0,overridden.getARGB(),"virtual display getter remains available");
    }
}
