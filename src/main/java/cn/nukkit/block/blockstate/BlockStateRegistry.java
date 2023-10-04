package cn.nukkit.block.blockstate;

import cn.nukkit.Server;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 保存着从{@link BlockState} -> runtimeid 的注册表
 */
@UtilityClass
@ParametersAreNonnullByDefault
@Log4j2
public class BlockStateRegistry {
    public static final ExecutorService asyncStateRemover = Executors.newSingleThreadExecutor(t -> new Thread(t, "BlockStateRegistry#asyncStateRemover"));

    public final int BIG_META_MASK = 0xFFFFFFFF;

    @Getter
    private static final BiMap<String, String> blockMappings = HashBiMap.create();
    private final Int2ObjectMap<String> blockIdToPersistenceName = new Int2ObjectOpenHashMap<>();
    private final Map<String, Integer> persistenceNameToBlockId = new LinkedHashMap<>();

    private BlockStateRegistryMapping mapping618;

    public static void init() {
        for (Map.Entry<String, RuntimeItems.MappingEntry> entry : RuntimeItems.getMappingEntries().entrySet()) {
            int id = RuntimeItems.getLegacyIdFromLegacyString(entry.getValue().getLegacyName());
            blockMappings.put(id + ":" + entry.getValue().getDamage(), entry.getKey());
        }

        try (InputStream stream = Server.class.getModule().getResourceAsStream("RuntimeBlockStates/block_ids.csv")) {
            if (stream == null) {
                throw new AssertionError("Unable to locate block_ids.csv");
            }

            int count = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    count++;
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split(",");
                    Preconditions.checkArgument(parts.length == 2 || parts[0].matches("^[0-9]+$"));
                    if (parts.length > 1 && parts[1].startsWith("minecraft:")) {
                        int id = Integer.parseInt(parts[0]);
                        blockIdToPersistenceName.put(id, parts[1]);
                        persistenceNameToBlockId.put(parts[1], id);
                    }
                }
            } catch (Exception e) {
                throw new IOException("Error reading the line " + count + " of the block_ids.csv", e);
            }

        } catch (IOException e) {
            throw new AssertionError(e);
        }

        mapping618 = new BlockStateRegistryMapping(blockIdToPersistenceName, persistenceNameToBlockId, ProtocolInfo.v1_20_30);
    }

    public static void close() {
        asyncStateRemover.shutdownNow();
    }

    @Nullable
    public static BlockStateRegistryMapping getMapping(int protocolId) {
        if (protocolId >= ProtocolInfo.v1_20_30_24) {
            return mapping618;
        }
        return null;
    }
}
