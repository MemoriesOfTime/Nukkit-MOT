package cn.nukkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防御测试：Bootstrap 及其同包辅助类只准 import JDK，任何第三方依赖都会让引导阶段在依赖
 * 下载完成前崩溃。
 * <p>
 * Defensive test: Bootstrap and its same-package helpers may only import the JDK; a third-party
 * import would crash the bootstrap phase before any dependency has been downloaded.
 */
class BootstrapJdkOnlyImportsTest {

    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)");

    @Test
    void bootstrapAndHelpersImportJdkOnly() throws IOException {
        List<Path> sources = Stream.of("Bootstrap.java", "DependencyManifest.java")
                .map(name -> Path.of("src/main/java/cn/nukkit").resolve(name))
                .toList();

        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            assertTrue(Files.isRegularFile(source), "源码文件应存在 / expected source file: " + source);
            Matcher matcher = IMPORT.matcher(Files.readString(source));
            while (matcher.find()) {
                String qualified = matcher.group(1);
                if (!qualified.startsWith("java.") && !qualified.startsWith("javax.") && !qualified.startsWith("jdk.")) {
                    offenders.add(source.getFileName() + ": import " + qualified);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "引导类只准 import JDK / bootstrap classes must import JDK only:\n" + String.join("\n", offenders));
    }
}
