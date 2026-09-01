package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.Zlib;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.FurnaceRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.InvalidDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用 CloudburstMC 参考解码器解析生产路径产出的真实配方包（与代理侧 debug 一致），
 * 覆盖 1.16.100 ~ 1.19.30 旧协议区间。
 * <p>
 * 背景: putRecipeIngredient 的 AIR 路径曾在 #811 中回归, &lt;553 协议把 count 写进 id 位导致流错位,
 * 1.18.30 客户端进服即断连。既有 CompatibilityTest 的手写解析器镜像编码端假设且版本从 v567 才开始,
 * 无法发现该问题; 本测试改用独立解码器, 任何字节错位都会立即抛出。
 * <p>
 * Decodes real crafting packets produced via the production packetFor() path with the
 * CloudburstMC reference codec, mirroring proxy-side debugging, for protocols 419-554.
 * Any stream misalignment (e.g. the &lt;553 AIR-ingredient regression) fails immediately.
 */
public class CraftingDataPacketCloudburstDecodeTest extends AbstractPacketRegressionTest {

    private static CraftingManager manager;

    @BeforeAll
    static void init() {
        MockServer.init();
        manager = new CraftingManager();
    }

    static Stream<GameVersion> versions() {
        return Stream.of(
                GameVersion.V1_16_100, // 419
                GameVersion.V1_17_0,   // 440
                GameVersion.V1_17_10,  // 448
                GameVersion.V1_17_30,  // 465
                GameVersion.V1_17_40,  // 471
                GameVersion.V1_18_0,   // 475
                GameVersion.V1_18_10,  // 486
                GameVersion.V1_18_30,  // 503: 曾因 AIR ingredient 回归根连
                GameVersion.V1_19_0,   // 527
                GameVersion.V1_19_10,  // 534
                GameVersion.V1_19_20,  // 544
                GameVersion.V1_19_21,  // 545
                GameVersion.V1_19_30   // 554: 首个 descriptor 型 ingredient 格式, 边界守护
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versions")
    void realRecipePacketDecodesWithCloudburst(GameVersion gv) throws Exception {
        cn.nukkit.network.protocol.CraftingDataPacket shell = encodeRealPacket(gv);

        org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket decoded =
                crossDecode(shell, org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket.class,
                        itemDefinitions(gv));

        int shaped = 0;
        int shapeless = 0;
        int furnace = 0;
        int shapedWithEmptyCell = 0;
        for (var data : decoded.getCraftingData()) {
            if (data instanceof ShapedRecipeData shapedRecipe) {
                shaped++;
                for (ItemDescriptorWithCount ingredient : shapedRecipe.getIngredients()) {
                    if (ingredient.getDescriptor() == InvalidDescriptor.INSTANCE) {
                        shapedWithEmptyCell++;
                        break;
                    }
                }
            } else if (data instanceof ShapelessRecipeData) {
                shapeless++;
            } else if (data instanceof FurnaceRecipeData) {
                furnace++;
            }
        }

        // 防空洞通过: 包必须真实携带各类配方, 且 shaped 配方确实含空格子(AIR ingredient 路径)
        // Anti-vacuous guards: the packet must carry real recipes, and shaped recipes
        // must actually contain empty cells so the AIR-ingredient path is exercised.
        assertTrue(shaped > 0, "shaped recipes must be present on " + gv);
        assertTrue(shapeless > 0, "shapeless recipes must be present on " + gv);
        assertTrue(furnace > 0, "furnace recipes must be present on " + gv);
        assertTrue(shapedWithEmptyCell > 0, "shaped recipes with empty cells must be present on " + gv);
        assertFalse(decoded.getPotionMixData().isEmpty(), "potion mixes must be present on " + gv);
        assertFalse(decoded.getContainerMixData().isEmpty(), "container mixes must be present on " + gv);
    }

    /**
     * 走生产路径 packetFor() 生成压缩 BatchPacket, 解压后取出内层 CraftingDataPacket 原始字节。
     * <p>
     * Produces a compressed BatchPacket via the production packetFor() path, inflates it
     * and extracts the raw inner CraftingDataPacket bytes wrapped in a packet shell.
     */
    private static cn.nukkit.network.protocol.CraftingDataPacket encodeRealPacket(GameVersion gv) throws Exception {
        Method packetFor = CraftingManager.class.getDeclaredMethod("packetFor", GameVersion.class);
        packetFor.setAccessible(true);
        BatchPacket bp = (BatchPacket) packetFor.invoke(manager, gv);
        byte[] raw = Zlib.inflateRaw(bp.payload, 64 * 1024 * 1024);

        BinaryStream batch = new BinaryStream(raw);
        int packetLen = (int) batch.getUnsignedVarInt();
        byte[] packet = batch.get(packetLen);

        cn.nukkit.network.protocol.CraftingDataPacket shell = new cn.nukkit.network.protocol.CraftingDataPacket();
        shell.protocol = gv.getProtocol();
        shell.gameVersion = gv;
        shell.setBuffer(packet);
        return shell;
    }

    private static Consumer<BedrockCodecHelper> itemDefinitions(GameVersion gv) {
        return helper -> {
            // CLIENT 档放宽数组上限: 真实配方包条目数(~3000)超过 DEFAULT 的 maxListSize(1536)
            // CLIENT settings: real recipe packets carry ~3000 entries, above DEFAULT maxListSize (1536)
            helper.setEncodingSettings(EncodingSettings.CLIENT);
            SimpleDefinitionRegistry.Builder<ItemDefinition> builder = SimpleDefinitionRegistry.builder();
            Set<Integer> runtimeIds = new HashSet<>();
            Set<String> identifiers = new HashSet<>();
            for (var entry : RuntimeItems.getMapping(gv).getItemPaletteEntries()) {
                if (!runtimeIds.add(entry.getRuntimeId()) || !identifiers.add(entry.getIdentifier())) {
                    continue;
                }
                builder.add(new SimpleItemDefinition(entry.getIdentifier(), entry.getRuntimeId(), false));
            }
            helper.setItemDefinitions(builder.build());
            // 空方块注册表即可: readItemInstance 对 blockRuntimeId 的解析结果仅存入 ItemData, 不参与流读取
            // An empty block registry suffices: the resolved definition is stored, never affects stream reads
            helper.setBlockDefinitions(SimpleDefinitionRegistry.<BlockDefinition>builder().build());
        };
    }
}
