package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.StringTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class LevelDBNameCacheTest {
    @TempDir
    Path directory;

    private boolean previousNative;
    private int previousCache;
    private LevelDBProvider provider;

    @BeforeEach
    void openWorld() throws Exception {
        MockServer.init();
        Server server = Server.getInstance();
        previousNative = server.useNativeLevelDB;
        previousCache = server.levelDbCache;
        server.useNativeLevelDB = false;
        server.levelDbCache = 8;
        LevelDBProvider.generate(directory.toString(), "original", 404L, Flat.class);
        provider = new LevelDBProvider(mock(Level.class), directory.toString());
    }

    @AfterEach
    void closeWorld() {
        if (provider != null) provider.close();
        Server.getInstance().useNativeLevelDB = previousNative;
        Server.getInstance().levelDbCache = previousCache;
    }

    @Test
    void seesRenamesAndAllChangesThroughLiveMetadata() {
        assertEquals("original", provider.getName());
        provider.updateLevelName("renamed");
        assertEquals("renamed", provider.getName());

        CompoundTag metadata = provider.getLevelData();
        StringTag renamed = (StringTag) metadata.get("LevelName");
        renamed.data = "direct field update";
        assertEquals("direct field update", provider.getName());
        metadata.putInt("LevelName", 42);
        assertEquals("42", provider.getName());
        metadata.getAllTags().remove(metadata.get("LevelName"));
        assertEquals("", provider.getName());
        provider.updateLevelName("restored");
        assertEquals("restored", metadata.getString("LevelName"));
        metadata.putString("LevelName", "replacement tag");
        assertEquals("replacement tag", provider.getName());
    }

    @Test
    void seesChangesThroughCallerSuppliedMetadata() {
        CompoundTag injected = new CompoundTag() {
            @Override
            public String getString(String name) {
                return "custom:" + super.getString(name);
            }
        }.putString("LevelName", "one");
        provider.setLevelData(injected, GameRules.getDefault());
        assertEquals("custom:one", provider.getName());
        injected.putString("LevelName", "two");
        assertEquals("custom:two", provider.getName());
        assertSame(injected, provider.getLevelData());
    }

    @Test
    void customGameRuleWriterMayRetainAndReplaceNameTag() {
        class RetainingRules extends GameRules {
            private CompoundTag metadata;

            @Override
            public void writeBedrockNBT(CompoundTag metadata) {
                this.metadata = metadata;
                metadata.putString("LevelName", "callback update");
                assertEquals("callback update", provider.getName());
            }
        }
        RetainingRules rules = new RetainingRules();
        provider.setGameRules(rules);
        rules.metadata.putString("LevelName", "retained callback state");
        assertEquals("retained callback state", provider.getName());
    }
}
