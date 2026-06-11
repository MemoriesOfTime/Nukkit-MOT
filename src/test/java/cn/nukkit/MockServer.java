package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;

/**
 * Mock Server utility for test environments.
 * Ensures Mock Server is set up via static initialization,
 * before any classes that depend on Server.getInstance() are loaded.
 */
public final class MockServer {

    private static volatile boolean initialized = false;
    private static Server originalServer;
    private static Server mockInstance;

    static {
        init();
    }

    /**
     * Initialize Mock Server. Skips if already initialized.
     * Uses double-checked locking for thread safety.
     */
    public static void init() {
        if (initialized) {
            // 已初始化但 instance 可能被其他测试类清除，需要恢复
            ensureInstance();
            return;
        }
        synchronized (MockServer.class) {
            if (initialized) {
                ensureInstance();
                return;
            }
            try {
                Field instanceField = Server.class.getDeclaredField("instance");
                instanceField.setAccessible(true);
                originalServer = (Server) instanceField.get(null);

                if (Server.getInstance() == null) {
                    mockInstance = createMockServer();
                    instanceField.set(null, mockInstance);
                } else {
                    mockInstance = Server.getInstance();
                }

                initBlocksAndItems();
                initialized = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MockServer", e);
            }
        }
    }

    /**
     * Ensure Server.instance is set to the mock instance.
     * Other test classes may have cleared it via reflection.
     */
    private static void ensureInstance() {
        if (Server.getInstance() == null && mockInstance != null) {
            try {
                Field instanceField = Server.class.getDeclaredField("instance");
                instanceField.setAccessible(true);
                instanceField.set(null, mockInstance);
            } catch (Exception e) {
                throw new RuntimeException("Failed to restore MockServer instance", e);
            }
        }
    }

    /**
     * Initialize Block, Item and related registries.
     * Must follow the same order as Server initialization.
     */
    private static void initBlocksAndItems() {
        Block.init();
        Enchantment.init();
        GlobalBlockPalette.init();
        RuntimeItems.init();
        Item.init();
        initCustomBlockManager();
    }

    /**
     * Initialize CustomBlockManager if not already initialized.
     * Required for packets like StartGamePacket that access CustomBlockManager.get().
     */
    private static void initCustomBlockManager() {
        if (CustomBlockManager.get() == null) {
            try {
                CustomBlockManager.init(mockInstance);
            } catch (Exception ignored) {
                // May fail if already initialized
            }
        }
    }

    /**
     * Restore original Server instance (if needed).
     */
    public static void restore() {
        if (!initialized || originalServer == mockInstance) {
            return;
        }
        synchronized (MockServer.class) {
            try {
                Field instanceField = Server.class.getDeclaredField("instance");
                instanceField.setAccessible(true);
                instanceField.set(null, originalServer);
            } catch (Exception e) {
                throw new RuntimeException("Failed to restore Server instance", e);
            }
        }
    }

    /**
     * Get the Mock Server instance.
     */
    public static Server get() {
        init();
        return mockInstance;
    }

    /**
     * Reset Mock Server state (for use between tests).
     */
    public static void reset() {
        if (mockInstance != null) {
            Mockito.reset(mockInstance);
            setupDefaults(mockInstance);
        }
    }

    private static Server createMockServer() {
        Server mock = Mockito.mock(Server.class);
        setupDefaults(mock);
        return mock;
    }

    /**
     * Set up default values for Mock Server.
     * Add new field defaults here when needed.
     */
    private static void setupDefaults(Server mock) {
        mock.useSnappy = false;
        mock.networkCompressionLevel = 7;
        mock.networkCompressionThreshold = 256;
        mock.chunkCompressionLevel = 1;
        mock.minimumProtocol = 0;
        mock.maximumProtocol = Integer.MAX_VALUE;
        mock.onlyNetEaseMode = false;

        Mockito.lenient().when(mock.getPluginManager())
                .thenReturn(Mockito.mock(PluginManager.class));
        Mockito.lenient().when(mock.getLogger())
                .thenReturn(Mockito.mock(MainLogger.class));
        Mockito.lenient().when(mock.getDifficulty()).thenReturn(2);
        Mockito.lenient().when(mock.getOnlinePlayers()).thenReturn(Collections.emptyMap());
        Mockito.lenient().when(mock.getGamemode()).thenReturn(0);
        Mockito.lenient().when(mock.getDataPath()).thenReturn(System.getProperty("java.io.tmpdir"));

        Level mockLevel = Mockito.mock(Level.class);
        setupLevelBlockStub(mockLevel);
        Mockito.lenient().when(mock.getDefaultLevel()).thenReturn(mockLevel);
    }

    /**
     * Setup getBlock() stub for mock Level to return a simple Block for any position.
     * This is needed for BlockIterator and other ray tracing tests.
     */
    private static void setupLevelBlockStub(Level mockLevel) {
        Mockito.lenient().when(mockLevel.getBlock(Mockito.any(Vector3.class)))
            .thenAnswer(invocation -> {
                Vector3 pos = invocation.getArgument(0);
                return createSimpleBlock(pos);
            });
    }

    /**
     * Create a simple Block instance for testing purposes.
     * Returns a minimal Block that reports its position correctly.
     */
    private static Block createSimpleBlock(Vector3 pos) {
        Block block = new Block() {
            @Override
            public String getName() {
                return "Air";
            }

            @Override
            public int getId() {
                return BlockID.AIR;
            }
        };
        block.x = pos.x;
        block.y = pos.y;
        block.z = pos.z;
        return block;
    }

    private MockServer() {}
}
