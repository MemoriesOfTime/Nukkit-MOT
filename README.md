<p align="center"><a href="https://www.nukkit-mot.com/"><img src="/.github/images/banner.png" alt="Nukkit-MOT"/></a></p>
<h3 align="center">Nukkit-MOT</h3>
<p align="center">
  <a href="https://www.gnu.org/licenses/lgpl-3.0.html"><img alt="License: LGPL v3" src="https://img.shields.io/github/license/MemoriesOfTime/Nukkit-MOT"></a>
    <a href="https://motci.cn/job/Nukkit-MOT/job/master/"><img alt="Jenkins Build" src="https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fmotci.cn%2Fjob%2FNukkit-MOT%2Fjob%2Fmaster%2F&label=jenkins&logo=jenkins"></a>
    <a href="https://discord.gg/pJjQDQC"><img alt="Discord" src="https://img.shields.io/discord/710480168598372354?label=Discord&logo=discord"></a>
<p align="center">
  <a href="/README.md"><img alt="English" src="https://img.shields.io/badge/English-d9d9d9"></a>
  <a href="/docs/README_zh.md"><img alt="中文" src="https://img.shields.io/badge/中文-d9d9d9"></a>
</p>



------

## Introduction
Nukkit-MOT is a special version of [Nukkit](https://github.com/CloudburstMC/Nukkit) Minecraft Bedrock Edition server software.  
It is developed based on the last open source version of [NukkitPetteriM1Edition](https://github.com/PetteriM1/NukkitPetteriM1Edition)

note: if you need higher version features, please use [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX).

### What's new in Nukkit-MOT?
1. Support for 1.2 – 1.21.110 version (you can set the minimum protocol in the config)
2. Supports most entities with AI
3. Support for the nether world and The Еnd
4. Generation of dungeons and caves
5. Support for vanilla commands

## How to install?
1. Install java 17 or higher
2. Download the .jar file from the links below
3. Write a command to run: `java -jar Nukkit-MOT-SNAPSHOT.jar` (change `Nukkit-MOT-SNAPSHOT.jar` to the name of the file you downloaded)

## Links
- __🌐 Download: [Jenkins](https://motci.cn/job/Nukkit-MOT/) / [GitHub Actions](https://github.com/MemoriesOfTime/Nukkit-MOT/actions/workflows/maven.yml?query=branch%3Amaster)__
- __💬 Discuss: [Discord](https://discord.gg/pJjQDQC) / [QQ Group](https://jq.qq.com/?_wv=1027&k=5aIuYMH)__
- __🔌 Plugins: [Nukkit Forum](https://cloudburstmc.org/resources/categories/nukkit-plugins.1/) / [Nukkit-MOT Forum](https://bbs.nukkit-mot.com/resources/)__
- __🐞 [Report a Bug](https://github.com/MemoriesOfTime/Nukkit-MOT/issues/new/choose)__

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

## Credits
[<img src="https://raw.githubusercontent.com/CloudburstMC/Nukkit/master/.github/images/logo.png" width="18"/>]() [Nukkit](https://github.com/CloudburstMC/Nukkit)  
[<img src="https://avatars.githubusercontent.com/u/26197131?v=4" width="18"/>]() [NukkitPetteriM1Edition](https://github.com/PetteriM1/NukkitPetteriM1Edition)  
[<img src="https://docs.powernukkitx.org/img/PNX_LOGO_sm.png" width="18"/>]() [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX)  

![YourKit](https://www.yourkit.com/images/yklogo.png)  
YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

