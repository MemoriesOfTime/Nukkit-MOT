package docs;

import cn.nukkit.GameVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author LT_Name
 */
public class ReadmeVersionTest {

    /**
     * 检查 README.md 是否包含最新的游戏版本
     * 避免我们总是忘记更新 README.md
     * <p>
     * Check if README.md contains the latest game version
     * to avoid us forgetting to update README.md
     *
     * @throws IOException
     */
    @Test
    public void testReadmeVersionMatchesGameVersion() throws IOException {
        String latestVersion = " " + GameVersion.getLastVersion().toString() + " "; //添加空格，避免错误匹配小版本号

        // README.md
        Path readmePath = Path.of("README.md");
        String content = Files.readString(readmePath);
        assertTrue(
                content.contains(latestVersion),
                String.format(
                        """
                        README.md does not mention the latest supported version: %s
        
                        Please update the README.md (e.g. under "What's new in Nukkit-MOT?")
                        to reflect the latest supported game version.
        
                        Example:
                            1. Support for 1.2 – %s version
                        """,
                        latestVersion, latestVersion
                )
        );

        // docs/README_zh.md
        readmePath = Path.of("docs/README_zh.md");
        content = Files.readString(readmePath);
        assertTrue(
                content.contains(latestVersion),
                String.format(
                        """
                        docs/README_zh.md does not mention the latest supported version: %s
        
                        Please update the docs/README_zh.md (e.g. under "Nukkit-MOT 的新特性")
                        to reflect the latest supported game version.
        
                        Example:
                            1. 支持 1.2 – %s 版本
                        """,
                        latestVersion, latestVersion
                )
        );
    }

}
