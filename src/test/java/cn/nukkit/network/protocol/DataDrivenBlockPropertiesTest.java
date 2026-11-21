package cn.nukkit.network.protocol;

import cn.nukkit.MockServer;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.ByteTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class DataDrivenBlockPropertiesTest {

    private static CompoundTag properties;

    @BeforeAll
    static void load() throws IOException {
        MockServer.init();
        try (InputStream stream = DataDrivenBlockPropertiesTest.class.getClassLoader()
                .getResourceAsStream("data_driven_blocks_2193.nbt")) {
            properties = NBTIO.read(new BufferedInputStream(new GZIPInputStream(stream)), ByteOrder.BIG_ENDIAN, false);
        }
    }

    /**
     * 文件里的每个条目都必须是带属性的 vanilla 方块标识符，不能混入自定义方块。
     * <p>
     * Every entry must be a vanilla block identifier carrying properties; no custom blocks may leak in.
     */
    @Test
    void everyEntryIsAVanillaBlockWithProperties() {
        for (Map.Entry<String, Tag> entry : properties.getTags().entrySet()) {
            assertTrue(entry.getKey().startsWith("minecraft:"),
                    "非 vanilla 命名空间 / not a vanilla namespace: " + entry.getKey());
            assertFalse(((CompoundTag) entry.getValue()).isEmpty(),
                    "属性为空 / empty properties: " + entry.getKey());
        }
    }

    /**
     * 属性 NBT 必须保留 BDS 的 tag 类型：布尔字段是 TAG_Byte、破坏秒数是 TAG_Float。
     * JSON 重建（Int/Double）会改变字节布局，客户端读属性流错位后 double slab 丢 is_double、渲染成半块。
     * <p>
     * The property NBT must keep the BDS tag types: boolean fields are TAG_Byte and destroy
     * times TAG_Float. Rebuilding from JSON (Int/Double) shifts the byte layout, and the client
     * misreading the stream drops is_double so double slabs render as half blocks.
     */
    @Test
    void nbtKeepsBdsTagTypes() {
        assertEquals(98, properties.getTags().size(), "entry count");

        Tag isDouble = properties.getCompound("minecraft:white_wool_double_slab")
                .getCompound("vanilla_block_data")
                .getCompound("block_archetype")
                .get("is_double");
        assertInstanceOf(ByteTag.class, isDouble, "is_double must stay TAG_Byte");
        assertEquals(1, isDouble.parseValue(), "double slab is_double");

        Tag conductor = properties.getCompound("minecraft:white_wool_double_slab")
                .getCompound("components")
                .getCompound("minecraft:redstone_conductivity")
                .get("redstoneConductor");
        assertInstanceOf(ByteTag.class, conductor, "redstoneConductor must stay TAG_Byte");
        assertEquals(1, conductor.parseValue(), "double slab conducts redstone");

        Tag destroyTime = properties.getCompound("minecraft:white_wool_slab")
                .getCompound("components")
                .getCompound("minecraft:destructible_by_mining")
                .get("value");
        assertInstanceOf(FloatTag.class, destroyTime, "destroy time must stay TAG_Float");
    }
}
