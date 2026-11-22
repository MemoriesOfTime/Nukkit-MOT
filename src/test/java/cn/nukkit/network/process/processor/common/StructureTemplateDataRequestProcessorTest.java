package cn.nukkit.network.process.processor.common;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntityStructureBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.structure.Structure;
import cn.nukkit.level.structure.StructureAPI;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.StructureTemplateDataRequestPacket;
import cn.nukkit.network.protocol.StructureTemplateDataResponsePacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 界面查询必须按请求里的名字返回结构，否则客户端加载按钮会一直禁用。
 * <p>
 * UI queries must answer with the requested name; otherwise the client keeps the
 * Load button disabled for a freshly typed structure name.
 */
class StructureTemplateDataRequestProcessorTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    @Test
    void queryUsesRequestedNameInsteadOfStoredName() throws Exception {
        String name = "qtest:" + System.nanoTime();
        File file = new File(System.getProperty("java.io.tmpdir"),
                "structures/qtest/" + name.substring(name.indexOf(':') + 1) + ".mcstructure");
        Structure structure = new Structure(1, 1, 1,
                List.of(new cn.nukkit.nbt.tag.CompoundTag()
                        .putString("name", "minecraft:stone")
                        .putCompound("states", new cn.nukkit.nbt.tag.CompoundTag())),
                new int[]{BlockID.STONE << Block.DATA_BITS},
                new int[]{0}, new int[]{Structure.VOID}, Map.of(), List.of(), new BlockVector3(0, 0, 0));
        try {
            assertTrue(StructureAPI.save(structure, name));

            Level level = Mockito.mock(Level.class);
            BlockEntityStructureBlock blockEntity = Mockito.mock(BlockEntityStructureBlock.class);
            Mockito.when(blockEntity.getStructureName()).thenReturn("");
            Mockito.when(level.getBlockEntity(Mockito.any(cn.nukkit.math.Vector3.class))).thenReturn(blockEntity);
            Player player = Mockito.mock(Player.class);
            Mockito.when(player.isOp()).thenReturn(true);
            Mockito.when(player.isCreative()).thenReturn(true);
            Mockito.when(player.getLevel()).thenReturn(level);

            StructureTemplateDataRequestPacket request = new StructureTemplateDataRequestPacket();
            request.name = name;
            request.operation = StructureTemplateDataRequestPacket.OPERATION_QUERY_SAVED_STRUCTURE;
            StructureTemplateDataRequestProcessor.INSTANCE.handle(new PlayerHandle(player), request);

            ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
            Mockito.verify(player).dataPacket(captor.capture());
            StructureTemplateDataResponsePacket response = (StructureTemplateDataResponsePacket) captor.getValue();
            assertEquals(name, response.name);
            assertTrue(response.save);
            assertNotNull(response.tag);
            assertEquals(StructureTemplateDataResponsePacket.RESPONSE_QUERY, response.type);
            // v1 必须两层 int 列表；标签版本与层类型不符会让预览整个加载失败
            // v1 needs two int-list layers; a tag/layer type mismatch makes the preview fail.
            assertEquals(1, response.tag.getInt("format_version"));
            cn.nukkit.nbt.tag.ListTag<?> indices = response.tag.getCompound("structure").getList("block_indices");
            assertEquals(2, indices.size());
            for (cn.nukkit.nbt.tag.Tag layer : indices.getAll()) {
                assertTrue(layer instanceof cn.nukkit.nbt.tag.ListTag<?> list
                        && list.type == cn.nukkit.nbt.tag.Tag.TAG_Int);
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }
}
