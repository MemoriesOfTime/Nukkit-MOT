package cn.nukkit.resourcepacks;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourcePackManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        BaseLang language = mock(BaseLang.class);
        when(language.translateString(any(String.class), any(Object[].class))).thenReturn("loaded");
        when(Server.getInstance().getLanguage()).thenReturn(language);
    }

    @Test
    void invalidPackConfigDoesNotAbortReload() throws IOException {
        Path packConfig = Files.writeString(
                tempDir.resolve("packs.yml"),
                "invalid: [unterminated",
                StandardCharsets.UTF_8
        );

        assertDoesNotThrow(() -> new ResourcePackManager(Set.of(() -> java.util.List.of()), packConfig.toFile()));
    }

    @Test
    void invalidPackConfigStructureDoesNotAbortReload() throws IOException {
        UUID packId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ResourcePack pack = mock(ResourcePack.class);
        when(pack.getPackId()).thenReturn(packId);
        when(pack.isBehaviourPack()).thenReturn(false);
        Path packConfig = Files.writeString(
                tempDir.resolve("packs.yml"),
                packId + ":\n  cdn: https://example.invalid/pack.mcpack\nnot-a-pack: scalar\n",
                StandardCharsets.UTF_8
        );

        assertDoesNotThrow(() -> new ResourcePackManager(Set.of(() -> List.of(pack)), packConfig.toFile()));
        verify(pack, never()).setCDNUrl(anyString());
    }

    @Test
    void validPackConfigIsStillApplied() throws IOException {
        UUID packId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ResourcePack pack = mock(ResourcePack.class);
        when(pack.getPackId()).thenReturn(packId);
        when(pack.isBehaviourPack()).thenReturn(false);
        Path packConfig = Files.writeString(
                tempDir.resolve("packs.yml"),
                packId + ":\n  cdn: https://example.invalid/pack.mcpack\n  key: test-key\n",
                StandardCharsets.UTF_8
        );

        new ResourcePackManager(Set.of(() -> List.of(pack)), packConfig.toFile());

        verify(pack).setCDNUrl("https://example.invalid/pack.mcpack");
        verify(pack).setEncryptionKey("test-key");
    }
}
