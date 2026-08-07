package cn.nukkit.resourcepacks;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JarPluginResourcePackSecurityTest {

    private static final String ENCRYPTION_KEY = "0123456789abcdefGHIJKLMNOPQRSTUV";

    @TempDir
    Path tempDir;

    private CapturingAppender appender;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        BaseLang language = mock(BaseLang.class);
        when(language.translateString(any(String.class), any(Object[].class))).thenReturn("resource pack error");
        when(Server.getInstance().getLanguage()).thenReturn(language);

        appender = new CapturingAppender();
        appender.start();
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(JarPluginResourcePack.class);
        logger.addAppender(appender);
        Configurator.setLevel(JarPluginResourcePack.class.getName(), Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(JarPluginResourcePack.class);
        logger.removeAppender(appender);
        appender.stop();
        Configurator.setLevel(JarPluginResourcePack.class.getName(), Level.INFO);
    }

    @Test
    void encryptionKeyIsNotWrittenToDebugLogs() throws IOException {
        Path pluginJar = createPluginJar(tempDir.resolve("plugin.jar"));

        new JarPluginResourcePack(pluginJar.toFile());

        assertFalse(appender.messages.stream().anyMatch(message -> message.contains(ENCRYPTION_KEY)));
    }

    @Test
    void encryptionKeyFieldRemainsProtectedForPluginSubclasses() throws NoSuchFieldException {
        assertTrue(Modifier.isProtected(AbstractResourcePack.class.getDeclaredField("encryptionKey").getModifiers()));
    }

    private static Path createPluginJar(Path path) throws IOException {
        String manifest = """
                {
                  "format_version": 2,
                  "header": {
                    "name": "Plugin Pack",
                    "description": "Plugin resource pack fixture",
                    "uuid": "11111111-1111-1111-1111-111111111111",
                    "version": [1, 0, 0]
                  },
                  "modules": [
                    {
                      "type": "resources",
                      "uuid": "22222222-2222-2222-2222-222222222222",
                      "version": [1, 0, 0]
                    }
                  ]
                }
                """;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            writeEntry(zip, JarPluginResourcePack.RESOURCE_PACK_PATH + "manifest.json", manifest);
            writeEntry(zip, JarPluginResourcePack.RESOURCE_PACK_PATH + "encryption.key", ENCRYPTION_KEY);
        }
        return path;
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final List<String> messages = new ArrayList<>();

        private CapturingAppender() {
            super("capturing", (Filter) null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
