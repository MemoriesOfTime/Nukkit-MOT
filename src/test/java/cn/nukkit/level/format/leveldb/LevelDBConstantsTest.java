package cn.nukkit.level.format.leveldb;

import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author LT_Name
 */
public class LevelDBConstantsTest {

    @Test
    public void testStateVersion() {
        List<NbtMap> list = NukkitLegacyMapper.loadBlockPalette();
        int version = 0;
        for (int i = 0; i < list.size(); ++i) {
            NbtMap nbtMap = list.get(i);
            version = nbtMap.getInt("version");
            break;
        }
        assertEquals(version, LevelDBConstants.STATE_VERSION, "LevelDBConstants.STATE_VERSION mismatch");
    }

}


