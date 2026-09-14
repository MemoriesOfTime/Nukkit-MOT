package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ItemStackRequestPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.descriptor.DefaultDescriptor;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.AutoCraftRecipeAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recipe book crafts captured from real 1.26.45 clients. Since v2168 the repetitions byte of
 * CRAFT_RECIPE_AUTO is sent once; reading a duplicate shifted the ingredients and the whole
 * batch failed to decode, so the craft silently did nothing.
 */
class AutoCraftRecipeActionDecodeTest {

    /** 9 oak logs into planks through the recipe book. */
    private static final String OAK_LOG_TIMES_9 =
            "930101e10d040b0d861509010101116d696e6563726166743a6f616b5f6c6f670001001113010101146d696e6563726166743a6f616b5f706c616e6b73000400b6b5ac94070a00000000000000000000090505090c0002134406000101243c00328ffcffff0c00028ffcffff00ffffffff";
    /** Three iron ingots into a bucket: one auto-craft action, three consumes. */
    private static final String BUCKET =
            "9301018502060b0d920f01030101146d696e6563726166743a69726f6e5f696e676f740001000101146d696e6563726166743a69726f6e5f696e676f740001000101146d696e6563726166743a69726f6e5f696e676f740001001113010101106d696e6563726166743a6275636b6574000100000a00000000000000000000010505010c00009eed06000505010c00007dffffff0505010c00007dffffff0101013c00327dffffff0c00007dffffff00ffffffff";

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    private static ItemStackRequest decode(String hex, int protocol) {
        byte[] bytes = HexFormat.of().parseHex(hex);
        ItemStackRequestPacket packet = new ItemStackRequestPacket();
        packet.protocol = protocol;
        packet.gameVersion = GameVersion.byProtocol(protocol, false);
        // Skip the packet id (VarUInt 147 = 0x93 0x01), the batch reader strips it the same way.
        packet.setBuffer(bytes, 2);
        packet.decode();
        assertEquals(bytes.length, packet.getOffset(), "decode must consume the whole payload");
        assertEquals(1, packet.getRequests().size());
        return packet.getRequests().get(0);
    }

    @ParameterizedTest(name = "recipe book craft x9 decodes on v{0}")
    @ValueSource(ints = {ProtocolInfo.v1_26_40, ProtocolInfo.v1_26_45})
    void singleIngredientCraftDecodes(int protocol) {
        ItemStackRequest request = decode(OAK_LOG_TIMES_9, protocol);
        assertEquals(4, request.getActions().length);
        AutoCraftRecipeAction auto = assertInstanceOf(AutoCraftRecipeAction.class, request.getActions()[0]);
        assertEquals(2694, auto.getRecipeNetworkId());
        assertEquals(9, auto.getTimesCrafted());
        assertEquals(9, auto.getNumberOfRequestedCrafts());
        assertEquals(1, auto.getIngredients().size());
        DefaultDescriptor log = assertInstanceOf(DefaultDescriptor.class, auto.getIngredients().get(0).getDescriptor());
        assertEquals(Item.fromString("minecraft:oak_log").getId(), log.getItemId());
        assertEquals(1, auto.getIngredients().get(0).getCount());
    }

    @ParameterizedTest(name = "recipe book craft with three ingredients decodes on v{0}")
    @ValueSource(ints = {ProtocolInfo.v1_26_40, ProtocolInfo.v1_26_45})
    void multiIngredientCraftDecodes(int protocol) {
        ItemStackRequest request = decode(BUCKET, protocol);
        assertEquals(6, request.getActions().length);
        AutoCraftRecipeAction auto = assertInstanceOf(AutoCraftRecipeAction.class, request.getActions()[0]);
        assertEquals(1, auto.getTimesCrafted());
        assertEquals(3, auto.getIngredients().size());
    }
}
