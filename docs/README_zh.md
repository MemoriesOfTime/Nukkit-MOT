<p align="center"><a href="https://www.nukkit-mot.com/"><img src="/.github/images/banner.png" alt="Nukkit-MOT"/></a></p>
<h3 align="center">Nukkit-MOT</h3>
<p align="center">
  <a href="https://www.gnu.org/licenses/lgpl-3.0.html"><img alt="许可证: LGPL v3" src="https://img.shields.io/github/license/MemoriesOfTime/Nukkit-MOT"></a>
  <a href="https://motci.cn/job/Nukkit-MOT/job/master/"><img alt="Jenkins 构建状态" src="https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fmotci.cn%2Fjob%2FNukkit-MOT%2Fjob%2Fmaster%2F&label=jenkins&logo=jenkins"></a>
  <a href="https://discord.gg/pJjQDQC"><img alt="Discord" src="https://img.shields.io/discord/710480168598372354?label=Discord&logo=discord"></a>
<p align="center">
  <a href="/README.md"><img alt="English" src="https://img.shields.io/badge/English-d9d9d9"></a>
  <a href="/docs/README_zh.md"><img alt="中文" src="https://img.shields.io/badge/中文-d9d9d9"></a>
</p>

------

## 项目介绍
Nukkit-MOT 是基于 [Nukkit](https://github.com/CloudburstMC/Nukkit) 的分支项目，具备多版本支持、网易客户端兼容以及良好的插件生态兼容性。

只想玩新版本？不妨试试 [Lumi](https://github.com/KoshakMineDEV/Lumi) 或 [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX)

### Nukkit-MOT 的新特性
1. 支持 1.2 – 1.21.130 版本（可在配置中设置最低协议版本）
2. 支持大多数实体的AI
3. 支持下界（Nether）和末地（The End）世界
4. 生成地牢和洞穴
5. 支持原版命令
6. 支持网易客户端

## 如何安装？
1. 安装 Java 17 或更高版本
2. 从下方链接下载 `.jar` 文件
3. 运行命令：`java -jar Nukkit-MOT-SNAPSHOT.jar`（将 `Nukkit-MOT-SNAPSHOT.jar` 替换为你下载的文件名）

## 相关链接
- __🌐 下载地址: [Jenkins](https://motci.cn/job/Nukkit-MOT/) / [GitHub Actions](https://github.com/MemoriesOfTime/Nukkit-MOT/actions/workflows/maven.yml?query=branch%3Amaster)__
- __💬 交流社区: [Discord](https://discord.gg/pJjQDQC) / [QQ 群](https://jq.qq.com/?_wv=1027&k=5aIuYMH)__
- __🔌 插件资源: [Nukkit 论坛](https://cloudburstmc.org/resources/categories/nukkit-plugins.1/) / [Nukkit-MOT 论坛](https://bbs.nukkit-mot.com/resources/)__
- __🐞 [提交问题反馈](https://github.com/MemoriesOfTime/Nukkit-MOT/issues/new/choose)__

## Maven
#### Repository:
```xml
<repositories>
    <repository>
        <id>repo-lanink-cn</id>
        <url>https://repo.lanink.cn/repository/maven-public/</url>
    </repository>
</repositories>
```

#### Dependencies:
```xml
<dependencies>
    <dependency>
        <groupId>cn.nukkit</groupId>
        <artifactId>Nukkit</artifactId>
        <version>MOT-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## Gradle
#### Repository:
```kts
repositories {
    mavenCentral()
    maven("https://repo.lanink.cn/repository/maven-public/")
} 
```

#### Dependencies:
```kts
dependencies {
    compileOnly("cn.nukkit:Nukkit:MOT-SNAPSHOT")
}
```

## 致谢
[<img src="https://raw.githubusercontent.com/CloudburstMC/Nukkit/master/.github/images/logo.png" width="18"/>]() [Nukkit](https://github.com/CloudburstMC/Nukkit)  
[<img src="https://avatars.githubusercontent.com/u/26197131?v=4" width="18"/>]() [NukkitPetteriM1Edition](https://github.com/PetteriM1/NukkitPetteriM1Edition)  
[<img src="https://avatars.githubusercontent.com/u/20168691?v=4" width="18"/>]() [EaseCation](https://www.easecation.net/) [Nukkit](https://github.com/EaseCation/Nukkit) & [SynapseAPI](https://github.com/EaseCation/SynapseAPI)  
[<img src="https://docs.powernukkitx.org/img/PNX_LOGO_sm.png" width="18"/>]() [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX)

[<img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.png" width="120"/>](https://jb.gg/OpenSourceSupport)  
Thanks to [jetbrains](https://jb.gg/OpenSourceSupport) for providing development tools for this project for free!

![YourKit](https://www.yourkit.com/images/yklogo.png)  
YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

