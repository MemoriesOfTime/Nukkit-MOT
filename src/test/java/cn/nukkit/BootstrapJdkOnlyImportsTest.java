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
 * 防御测试：Bootstrap 及其同包辅助类只准 import JDK（第三方导入会让引导阶段在依赖
 * 下载完成前崩溃）；用户可见文本必须走 {@link BootstrapLang}，字符串字面量里不得
 * 出现硬编码中文（注释不受限）。
 * <p>
 * Defensive tests: bootstrap classes may only import the JDK, and user-visible text must go
 * through {@link BootstrapLang} — no hardcoded CJK inside string literals (comments exempt).
 */
class BootstrapJdkOnlyImportsTest {

    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf]");

    @Test
    void bootstrapAndHelpersImportJdkOnly() throws IOException {
        List<Path> sources = bootstrapSources();

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

    @Test
    void userVisibleTextIsNotHardcodedInLiterals() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : bootstrapSources()) {
            String content = Files.readString(source);
            Matcher literal = STRING_LITERAL.matcher(content);
            while (literal.find()) {
                if (CJK.matcher(literal.group()).find()) {
                    offenders.add(source.getFileName() + ": " + literal.group());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "用户可见文本必须走 BootstrapLang / user-visible text must go through BootstrapLang:\n"
                        + String.join("\n", offenders));
    }

    private static List<Path> bootstrapSources() {
        return Stream.of("Bootstrap.java", "BootstrapLang.java", "DependencyManifest.java")
                .map(name -> Path.of("src/main/java/cn/nukkit").resolve(name))
                .toList();
    }
}
